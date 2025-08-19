package com.reglisseforge.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUnion;
import com.anthropic.models.messages.ToolUseBlock;
import com.reglisseforge.tools.base.ToolExecutor;
import com.reglisseforge.tools.base.ToolRegistry;
import com.reglisseforge.utils.AnthropicClientFactory;
import com.reglisseforge.utils.AnthropicUtils;
import com.reglisseforge.utils.StreamTextAccumulator;
import com.reglisseforge.utils.TokenCounter;

import lombok.Builder;
import lombok.Getter;


@Builder
public class Agent {

    @Builder.Default
    private final Model model = Model.CLAUDE_3_7_SONNET_LATEST;

    private final String name;

    @Builder.Default
    private final AnthropicClient client = AnthropicClientFactory.create();

    private final String instructions;

    @Getter
    @Builder.Default
    private final ToolRegistry toolRegistry = new ToolRegistry();


    private  ToolExecutor toolExecutor;


    @Builder.Default
    private boolean async = false;

    @Builder.Default
    private boolean cacheSystem = false;

    @Builder.Default
    private int maxTurn = 10;

    @Builder.Default
    private long systemTokenCount = -1L;


    /**
     * Sends a plain text message to the agent using the proper SDK builder flow.
     */
    public Message sendMessage(String userText) {
        if (userText == null || userText.isBlank()) {
            throw new IllegalArgumentException("Message cannot be null or empty");
        }

        MessageCreateParams.Builder base = MessageCreateParams.builder()
                .model(model)
                .maxTokens(1024L);

        // Optional system (with caching if appropriate)
        applySystem(base);

        return internalLoop(base, userText);
    }

    /**
     * Async wrapper around the internal tool loop. Tool calls remain synchronous inside the loop.
     */
    public CompletableFuture<Message> sendMessageAsync(String userText) {
        if (userText == null || userText.isBlank()) {
            throw new IllegalArgumentException("Message cannot be null or empty");
        }

        MessageCreateParams.Builder base = MessageCreateParams.builder()
                .model(model)
                .maxTokens(1024L);

        applySystem(base);

        return CompletableFuture.supplyAsync(() -> internalLoop(base, userText));
    }

    /**
     * Async streaming call using the internal loop for tool calls.
     * Streams only the final assistant message (character-by-character) via the provided consumer.
     */
    public CompletableFuture<String> streamMessageAsync(String userText, Consumer<String> onTextChunk) {
        if (userText == null || userText.isBlank()) {
            throw new IllegalArgumentException("Message cannot be null or empty");
        }

        MessageCreateParams.Builder base = MessageCreateParams.builder()
                .model(model)
                .maxTokens(1024L);

        // Optional system
        applySystem(base);

        // Prepare tools and executor (tool calls are synchronous inside the loop)
        ensureToolExecutor();
        List<ToolUnion> tools = buildToolUnions();
        if (!tools.isEmpty()) {
            base.tools(tools);
        }

        // Start with user message
        base.addUserMessage(userText);

        StreamTextAccumulator accumulator = new StreamTextAccumulator(onTextChunk);
        StringBuilder preFinalText = new StringBuilder();

        return CompletableFuture.supplyAsync(() -> {
            for (int turn = 0; turn < maxTurn; turn++) {
                MessageCreateParams req = base.build();
                Message res = client.messages().create(req);

                List<ToolUseBlock> toolUses = extractToolUses(res);
                if (toolUses.isEmpty()) {
                    // Final turn → stream the same request to emit chunks
                    try (StreamResponse<RawMessageStreamEvent> streamResponse =
                                 client.messages().createStreaming(req)) {
                        if (!preFinalText.isEmpty() && onTextChunk != null) {
                            onTextChunk.accept(preFinalText.toString());
                        }
                        streamResponse.stream().forEach(accumulator::onEvent);
                        return preFinalText.toString() + accumulator.getText();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                // Add assistant message to conversation and execute tools synchronously
                base.addMessage(res);
                // Capture any assistant text emitted alongside tool_use and print it before final
                String maybeText = AnthropicUtils.getAssistantText(res);
                if (!maybeText.isBlank()) {
                    preFinalText.append(maybeText);
                }
                List<ContentBlockParam> toolResults = executeToolsSync(toolUses);
                if (!toolResults.isEmpty()) {
                    base.addUserMessageOfBlockParams(toolResults);
                }
                base.addUserMessage("Produce a single, coherent, complete final answer in the user's language. Integrate tool results and any validated information. Do not reveal internal reasoning or tool IDs. Strictly follow any output format requested by the user (JSON-only, code-only, precise template, key order). If minor ambiguities remain but are not blocking, make reasonable assumptions and state them briefly. If blocking, ask at most one clear, minimal question.");
            }

            // Safety net: stream after exhausting turns
            try (com.anthropic.core.http.StreamResponse<com.anthropic.models.messages.RawMessageStreamEvent> streamResponse =
                         client.messages().createStreaming(base.build())) {
                if (preFinalText.length() > 0 && onTextChunk != null) {
                    onTextChunk.accept(preFinalText.toString());
                }
                streamResponse.stream().forEach(accumulator::onEvent);
                return preFinalText.toString() + accumulator.getText();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /** Convenience overload that prints chunks to stdout. */
    public CompletableFuture<String> streamMessageAsync(String userText) {
        return streamMessageAsync(userText, chunk -> System.out.print(chunk));
    }



    private Message internalLoop(MessageCreateParams.Builder base, String userMessage) {

        ensureToolExecutor();

        List<ToolUnion> tools = buildToolUnions();
        if(!tools.isEmpty()) {
            base.tools(tools);
        }

        // Start with user message
        base.addUserMessage(userMessage);

        Message lastResponse = null;

        for (int turn = 0; turn < maxTurn; turn++) {
            MessageCreateParams req = base.build();
            Message res = client.messages().create(req);
            lastResponse = res;
            
            List<ToolUseBlock> toolUses = extractToolUses(res);

            if(toolUses.isEmpty()) {
                return res;
            }

            // Add assistant message to conversation
            base.addMessage(res);

            // Execute tools and create tool result blocks (sync)
            List<ContentBlockParam> toolResults = executeToolsSync(toolUses);

            // Add tool results as user message (block params API)
            if (!toolResults.isEmpty()) {
                base.addUserMessageOfBlockParams(toolResults);
            }
            base.addUserMessage("Produce a single, coherent, complete final answer in the user's language. Integrate tool results and any validated information. Do not reveal internal reasoning or tool IDs. Strictly follow any output format requested by the user (JSON-only, code-only, precise template, key order). If minor ambiguities remain but are not blocking, make reasonable assumptions and state them briefly. If blocking, ask at most one clear, minimal question.");
        }

        if (lastResponse != null) {
            return lastResponse;
        }
        // Fallback: make a single call if nothing was returned (should not happen with maxTurn > 0)
        return client.messages().create(base.build());
    }

    private void ensureToolExecutor() {
        if (toolExecutor == null) {
            toolExecutor = new ToolExecutor(toolRegistry);
        }
    }

    private List<ToolUnion> buildToolUnions() {
        return toolRegistry.getAllTools().stream()
                .map(info -> ToolUnion.ofTool(Tool.builder()
                        .name(info.getName())
                        .description(info.getDescription())
                        .inputSchema(info.getSchema())
                        .build()))
                .toList();
    }

    private List<ToolUseBlock> extractToolUses(Message res) {
        List<ToolUseBlock> toolUses = new ArrayList<>();
        for(ContentBlock cb : res.content()){
            cb.toolUse().ifPresent(toolUses::add);
        }
        return toolUses;
    }

    private List<ContentBlockParam> executeToolsSync(List<ToolUseBlock> toolUses) {
        List<ContentBlockParam> toolResults = new ArrayList<>();
        for (ToolUseBlock toolUse : toolUses) {
            try {
                Object result = toolExecutor.executeTool(toolUse);
                String resultJson = result != null ? result.toString() : "null";
                toolResults.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(toolUse.id())
                        .content(resultJson)
                        .build()));
            } catch (Throwable e) {
                toolResults.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(toolUse.id())
                        .content("Error: " + e.getMessage())
                        .isError(true)
                        .build()));
            }
        }
        return toolResults;
    }



    private void applySystem(MessageCreateParams.Builder base) {


        if (instructions == null || instructions.isBlank()) {
            return;
        }
        if (!cacheSystem) {
            base.system(instructions);
            return;
        }


        // Cache enabled → count once and compare against a threshold
        long sysTokens = ensureSystemTokenCount();

        if (sysTokens >= 2048L) {
            // Use a cache breakpoint on the system block
            TextBlockParam systemBlock = TextBlockParam.builder()
                    .text(instructions)
                    .cacheControl(CacheControlEphemeral.builder()
                            .build())
                    .build();
            base.systemOfTextBlockParams(List.of(systemBlock));
        } else {
            // Too small to be eligible → plain system (no cache)
            base.system(instructions);
        }
    }


    private long ensureSystemTokenCount() {

        if (systemTokenCount >= 0) return systemTokenCount;
        try {
            systemTokenCount = TokenCounter.countTokens(instructions, model, client);
        } catch (Exception e) {
            // On error, fall back to 0 so we skip caching
            systemTokenCount = 0L;
        }
        return systemTokenCount;
    }




}
