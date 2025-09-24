package com.reglisseforge.tools;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

/**
 * Leo code corrector that uses tools to fix compilation errors
 */
public class LeoCodeCorrector {
    private static final Logger logger = LogManager.getLogger(LeoCodeCorrector.class);
    
    private final AnthropicClient client;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final Model model = Model.CLAUDE_4_SONNET_20250514;
    
    public LeoCodeCorrector() {
        this.client = AnthropicClientFactory.create();
        this.toolRegistry = new ToolRegistry();
        
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
     * Attempts to fix Leo compilation errors using an AI-powered correction loop
     * 
     * @param projectPath Path to the Leo project
     * @param maxAttempts Maximum number of correction attempts (default 20)
     * @return true if compilation succeeded, false if max attempts reached
     */
    public boolean fixCompilationErrors(String projectPath, int maxAttempts) {
        logger.info("Starting Leo code correction for project: {}", projectPath);
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            logger.info("Correction attempt {}/{}", attempt, maxAttempts);
            System.out.println("\n" + "━".repeat(60));
            System.out.println("🔄 CORRECTION ATTEMPT " + attempt + "/" + maxAttempts);
            System.out.println("━".repeat(60));
            
            // Try to build the project
            System.out.println("🔨 Running leo build...");
            String buildOutput = buildProject(projectPath);
            
            // Check if build succeeded
            if (buildOutput.contains("✅ Compiled") || buildOutput.contains("Successfully compiled") || 
                !buildOutput.contains("Error") && !buildOutput.contains("error")) {
                logger.info("✅ Build succeeded on attempt {}!", attempt);
                System.out.println("\n🎉 BUILD SUCCEEDED on attempt " + attempt + "!");
                return true;
            }
            
            logger.info("Build failed, attempting to fix errors...");
            logger.debug("Build output:\n{}", buildOutput);
            System.out.println("❌ Build failed. Analyzing errors...");
            
            // Use AI to fix the errors
            boolean fixed = attemptFix(projectPath, buildOutput, attempt);
            
            if (!fixed) {
                logger.warn("Failed to apply fixes on attempt {}", attempt);
                System.out.println("⚠️ Failed to apply fixes on this attempt");
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
    
    private boolean attemptFix(String projectPath, String errorOutput, int attemptNumber) {
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
            
            // Run the correction loop
            Message response = runCorrectionLoop(builder);
            
            return response != null;
            
        } catch (Exception e) {
            logger.error("Error during correction attempt", e);
            return false;
        }
    }
    
    private Message runCorrectionLoop(MessageCreateParams.Builder builder) {
        Message lastResponse = null;
        int maxToolTurns = 10; // Max tool turns within a single correction attempt
        
        for (int turn = 0; turn < maxToolTurns; turn++) {
            System.out.println("  ▶ Tool turn " + (turn + 1) + "/" + maxToolTurns);
            
            MessageCreateParams request = builder.build();
            Message response = client.messages().create(request);
            lastResponse = response;
            
            // Extract tool uses from response
            List<ToolUseBlock> toolUses = extractToolUses(response);
            
            if (toolUses.isEmpty()) {
                // No more tools to call, correction attempt is complete
                logger.info("Correction attempt complete after {} tool turns", turn + 1);
                System.out.println("  ✅ Correction attempt complete after " + (turn + 1) + " tool turns");
                return response;
            }
            
            logger.info("Executing {} tool calls", toolUses.size());
            System.out.println("  🔧 Executing " + toolUses.size() + " tool calls...");
            
            // Add assistant message to conversation
            builder.addMessage(response);
            
            // Execute tools and collect results
            List<ContentBlockParam> toolResults = executeTools(toolUses);
            
            // Add tool results as user message
            if (!toolResults.isEmpty()) {
                builder.addUserMessageOfBlockParams(toolResults);
            }
        }
        
        logger.warn("Reached maximum tool turns ({}) in correction loop", maxToolTurns);
        System.out.println("  ⚠️ Reached maximum tool turns (" + maxToolTurns + ")");
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
    
    private List<ContentBlockParam> executeTools(List<ToolUseBlock> toolUses) {
        List<ContentBlockParam> toolResults = new ArrayList<>();
        
        for (ToolUseBlock toolUse : toolUses) {
            try {
                logger.info("Executing tool: {}", toolUse.name());
                System.out.println("    → Executing: " + toolUse.name());
                
                Object result = toolExecutor.executeTool(toolUse);
                String resultStr = result != null ? result.toString() : "Success";
                
                toolResults.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(toolUse.id())
                        .content(resultStr)
                        .build()));
                        
                logger.info("Tool result: {}", resultStr);
                
                // Show abbreviated result for better UX
                if (resultStr.length() > 100) {
                    System.out.println("      ✓ Result: " + resultStr.substring(0, 97) + "...");
                } else {
                    System.out.println("      ✓ Result: " + resultStr);
                }
                
            } catch (Throwable e) {
                logger.error("Error executing tool: {}", toolUse.name(), e);
                System.out.println("      ✗ Error: " + e.getMessage());
                
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
