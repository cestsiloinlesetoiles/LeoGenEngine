package com.reglisseforge.tools;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUnion;
import com.anthropic.models.messages.ToolUseBlock;
import com.reglisseforge.tools.base.ToolExecutor;
import com.reglisseforge.tools.base.ToolRegistry;
import com.reglisseforge.utils.AnthropicClientFactory;
import com.reglisseforge.utils.CommandRunner;
import com.reglisseforge.utils.LeoPrompt;
import com.reglisseforge.web.service.StreamEventService;

/**
 * Web-enabled Leo code corrector with WebSocket streaming support
 */
public class WebLeoCodeCorrector {
    private static final Logger logger = LoggerFactory.getLogger(WebLeoCodeCorrector.class);
    
    private final AnthropicClient client;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final Model model = Model.CLAUDE_3_7_SONNET_LATEST;
    private final StreamEventService eventService;
    
    public WebLeoCodeCorrector(StreamEventService eventService) {
        this.client = AnthropicClientFactory.create();
        this.toolRegistry = new ToolRegistry();
        this.eventService = eventService;
        
        // Register static tools
        registerTools();
        
        this.toolExecutor = new ToolExecutor(toolRegistry);
    }
    
    private void registerTools() {
        try {
            // Register FileReaderTool methods
            toolRegistry.registerStaticMethod(FileReaderTool.class, "readFileWithLineNumbers");
            toolRegistry.registerStaticMethod(FileReaderTool.class, "readFileLines");
            
            // Register FileEditorTool methods
            toolRegistry.registerStaticMethod(FileEditorTool.class, "editFile");
            
            logger.info("Registered tools: {}", toolRegistry.getToolNames());
        } catch (Exception e) {
            logger.error("Error registering tools", e);
            throw new RuntimeException("Failed to register tools", e);
        }
    }
    
    /**
     * Attempts to fix Leo compilation errors with WebSocket streaming
     */
    public boolean fixCompilationErrors(String sessionId, String projectPath, int maxAttempts) {
        logger.info("Starting Leo code correction for project: {}", projectPath);
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            logger.info("Correction attempt {}/{}", attempt, maxAttempts);
            
            eventService.sendFixingStarted(sessionId, attempt, maxAttempts);
            
            // Try to build the project
            eventService.sendFixingProgress(sessionId, "🔨 Running leo build...", attempt);
            String buildOutput = buildProject(projectPath);
            
            // Check if build succeeded
            if (buildOutput.contains("✅ Compiled") || buildOutput.contains("Successfully compiled") || 
                !buildOutput.contains("Error") && !buildOutput.contains("error")) {
                
                logger.info("✅ Build succeeded on attempt {}!", attempt);
                eventService.sendFixingSuccess(sessionId, attempt);
                return true;
            }
            
            logger.info("Build failed, attempting to fix errors...");
            logger.debug("Build output:\n{}", buildOutput);
            
            eventService.sendFixingProgress(sessionId, "❌ Build failed. Analyzing errors...", attempt);
            
            // Use AI to fix the errors
            boolean fixed = attemptFix(sessionId, projectPath, buildOutput, attempt, maxAttempts);
            
            if (!fixed) {
                logger.warn("Failed to apply fixes on attempt {}", attempt);
                eventService.sendFixingProgress(sessionId, "⚠️ Failed to apply fixes on this attempt", attempt);
            } else {
                eventService.sendFixingProgress(sessionId, "✅ Fixes applied, checking build...", attempt);
            }
        }
        
        logger.error("❌ Failed to fix compilation errors after {} attempts", maxAttempts);
        return false;
    }
    
    private String buildProject(String projectPath) {
        File projectDir = new File(projectPath);
        CommandRunner.CommandResult result = CommandRunner.runBash("leo build", projectDir);
        
        // Combine stdout and stderr for full output
        String fullOutput = result.stdout();
        if (!result.stderr().isEmpty()) {
            fullOutput += "\n" + result.stderr();
        }
        
        logger.debug("Build exit code: {}", result.exitCode());
        
        return fullOutput;
    }
    
    private boolean attemptFix(String sessionId, String projectPath, String errorOutput, int attemptNumber, int maxAttempts) {
        try {
            // Build message with system prompt and user message
            MessageCreateParams.Builder builder = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(8000L)
                    .system(LeoPrompt.getLeoCorrectorSystemPrompt());
            
            // Add tools
            List<ToolUnion> tools = buildToolUnions();
            if (!tools.isEmpty()) {
                builder.tools(tools);
            }
            
            // Add user message with error details
            builder.addUserMessage(LeoPrompt.getLeoCorrectorUserPrompt(projectPath, errorOutput, attemptNumber).text());
            
            // Run the correction loop with WebSocket feedback
            Message response = runCorrectionLoop(sessionId, builder, attemptNumber);
            
            return response != null;
            
        } catch (Exception e) {
            logger.error("Error during correction attempt", e);
            eventService.sendError(sessionId, "Error during correction: " + e.getMessage());
            return false;
        }
    }
    
    private Message runCorrectionLoop(String sessionId, MessageCreateParams.Builder builder, int attemptNumber) {
        Message lastResponse = null;
        int maxToolTurns = 10; // Max tool turns within a single correction attempt
        
        for (int turn = 0; turn < maxToolTurns; turn++) {
            eventService.sendFixingProgress(sessionId, 
                String.format("▶ Tool turn %d/%d (attempt %d)", turn + 1, maxToolTurns, attemptNumber), 
                attemptNumber);
            
            MessageCreateParams request = builder.build();
            Message response = client.messages().create(request);
            lastResponse = response;
            
            // Extract tool uses from response
            List<ToolUseBlock> toolUses = extractToolUses(response);
            
            if (toolUses.isEmpty()) {
                // No more tools to call, correction attempt is complete
                logger.info("Correction attempt complete after {} tool turns", turn + 1);
                eventService.sendFixingProgress(sessionId, 
                    String.format("✅ Correction attempt complete after %d tool turns", turn + 1), 
                    attemptNumber);
                return response;
            }
            
            logger.info("Executing {} tool calls", toolUses.size());
            eventService.sendFixingProgress(sessionId, 
                String.format("🔧 Executing %d tool calls...", toolUses.size()), 
                attemptNumber);
            
            // Add assistant message to conversation
            builder.addMessage(response);
            
            // Execute tools and collect results
            List<ContentBlockParam> toolResults = executeTools(sessionId, toolUses, attemptNumber);
            
            // Add tool results as user message
            if (!toolResults.isEmpty()) {
                builder.addUserMessageOfBlockParams(toolResults);
            }
        }
        
        logger.warn("Reached maximum tool turns ({}) in correction loop", maxToolTurns);
        eventService.sendFixingProgress(sessionId, 
            String.format("⚠️ Reached maximum tool turns (%d)", maxToolTurns), 
            attemptNumber);
        return lastResponse;
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
    
    private List<ToolUseBlock> extractToolUses(Message response) {
        List<ToolUseBlock> toolUses = new ArrayList<>();
        for (ContentBlock block : response.content()) {
            block.toolUse().ifPresent(toolUses::add);
        }
        return toolUses;
    }
    
    private List<ContentBlockParam> executeTools(String sessionId, List<ToolUseBlock> toolUses, int attemptNumber) {
        List<ContentBlockParam> toolResults = new ArrayList<>();
        
        for (ToolUseBlock toolUse : toolUses) {
            try {
                logger.info("Executing tool: {}", toolUse.name());
                eventService.sendFixingProgress(sessionId, 
                    String.format("    → Executing: %s", toolUse.name()), 
                    attemptNumber);
                
                Object result = toolExecutor.executeTool(toolUse);
                String resultStr = result != null ? result.toString() : "Success";
                
                toolResults.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(toolUse.id())
                        .content(resultStr)
                        .build()));
                        
                logger.info("Tool result: {}", resultStr);
                
                // Send abbreviated result via WebSocket
                String abbreviatedResult = resultStr.length() > 100 ? 
                    resultStr.substring(0, 97) + "..." : resultStr;
                eventService.sendFixingProgress(sessionId, 
                    String.format("      ✓ Result: %s", abbreviatedResult), 
                    attemptNumber);
                
            } catch (Throwable e) {
                logger.error("Error executing tool: {}", toolUse.name(), e);
                
                eventService.sendFixingProgress(sessionId, 
                    String.format("      ✗ Error: %s", e.getMessage()), 
                    attemptNumber);
                
                toolResults.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(toolUse.id())
                        .content("Error: " + e.getMessage())
                        .isError(true)
                        .build()));
            }
        }
        
        return toolResults;
    }
}
