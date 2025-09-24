package com.reglisseforge.tools;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.beta.messages.BetaRawMessageStreamEvent;
import com.anthropic.models.beta.messages.BetaTextBlock;
import com.anthropic.models.beta.messages.BetaTextBlockParam;
import com.anthropic.models.beta.messages.BetaThinkingConfigEnabled;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.reglisseforge.utils.AnthropicClientFactory;
import com.reglisseforge.utils.CommandRunner;
import com.reglisseforge.utils.LeoPrompt;
import com.reglisseforge.web.service.StreamEventService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Web-enabled Leo code generation engine with WebSocket streaming support.
 * Extends the original LeoCodeEngine with real-time event emission.
 */
@Component
public class WebLeoCodeEngine {
    
    private static final Logger logger = LoggerFactory.getLogger(WebLeoCodeEngine.class);
    
    private final Model model = Model.CLAUDE_3_7_SONNET_LATEST;
    private final AnthropicClient client;
    private final StreamEventService eventService;
    
    public WebLeoCodeEngine(StreamEventService eventService) {
        this.client = AnthropicClientFactory.create();
        this.eventService = eventService;
    }

    /**
     * Initialize a Leo project and generate code with WebSocket streaming
     */
    public String initProject(String sessionId, String projectName, String projectDescription, String workspacePath) {
        try {
            // Use provided workspace path or default
            Path baseDir = workspacePath != null ? 
                Paths.get(workspacePath).toAbsolutePath().normalize() :
                Paths.get(".").toAbsolutePath().normalize();
                
            Path workspaceDir = baseDir.resolve("leoworkspace");
            Files.createDirectories(workspaceDir);

            // Force project name to lowercase and replace spaces with underscores
            String leoProjectName = projectName.toLowerCase().replaceAll("\\s+", "_");
            
            eventService.sendInfo(sessionId, "Creating Leo project structure...");
            
            // Create Leo project skeleton
            String command = "leo new " + leoProjectName;
            CommandRunner.runBash(command, workspaceDir.toFile());
            
            String projectPath = workspaceDir.resolve(leoProjectName).toString();
            
            eventService.sendInfo(sessionId, "Project structure created at: " + projectPath);
            
            // Generate initial code with streaming
            generateInitialCode(sessionId, projectPath, leoProjectName, projectDescription);
            
            return projectPath;
            
        } catch (IOException e) {
            String error = "Failed to create project: " + e.getMessage();
            eventService.sendError(sessionId, error);
            throw new RuntimeException(error, e);
        }
    }

    /**
     * Generate initial Leo code with real-time streaming to WebSocket
     */
    private void generateInitialCode(String sessionId, String projectPath, String projectName, String description) {
        Path outputFile = Paths.get(projectPath, "src", "main.leo");
        
        try {
            // Create src directory if it doesn't exist
            Files.createDirectories(outputFile.getParent());
            
            // Prepare system and user messages
            List<BetaTextBlockParam> system = List.of(LeoPrompt.LeoRulesBookParam());
            BetaTextBlock userMessage = LeoPrompt.LeoGenPrompt(projectName, description, null);

            MessageCreateParams params = MessageCreateParams.builder()
                    .model(this.model)
                    .maxTokens(16_000L)
                    .systemOfBetaTextBlockParams(system)
                    .addUserMessage(userMessage.text())
                    .thinking(
                            BetaThinkingConfigEnabled.builder()
                                    .budgetTokens(10_000L)
                                    .build()
                    )
                    .build();

            eventService.sendThinking(sessionId, "Analyzing project requirements and planning code structure...");
            
            // Use BufferedWriter for efficient file writing
            try (BufferedWriter writer = Files.newBufferedWriter(outputFile);
                 StreamResponse<BetaRawMessageStreamEvent> streamResponse = client.beta().messages().createStreaming(params)) {
                
                eventService.sendGenerating(sessionId, "Starting code generation...");
                
                // Counter for periodic flush
                AtomicInteger chunkCount = new AtomicInteger(0);
                
                streamResponse.stream()
                        .flatMap(event -> event.contentBlockDelta().stream())
                        .flatMap(deltaEvent -> deltaEvent.delta().text().stream())
                        .forEach(textDelta -> {
                            String chunk = textDelta.text();
                            
                            // Send chunk via WebSocket immediately
                            eventService.sendCodeChunk(sessionId, chunk);
                            
                            // Write to file
                            try {
                                writer.write(chunk);
                                
                                // Flush more frequently for real-time streaming
                                if (chunkCount.incrementAndGet() % 2 == 0) {
                                    writer.flush();
                                }
                            } catch (IOException e) {
                                String error = "Error writing code to file: " + e.getMessage();
                                eventService.sendError(sessionId, error);
                                logger.error("Error writing chunk to file", e);
                            }
                            
                            // Small delay to ensure real-time streaming
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });
                
                // Final flush
                writer.flush();
            }
            
            eventService.sendInfo(sessionId, "✅ Initial code generation completed");
            eventService.sendInfo(sessionId, "📄 Code saved to: " + outputFile.toAbsolutePath());
            
            // Fix invalid admin addresses
            fixInvalidAdminAddresses(sessionId, outputFile);
            
        } catch (IOException e) {
            String error = "Failed to generate code: " + e.getMessage();
            eventService.sendError(sessionId, error);
            throw new RuntimeException(error, e);
        } catch (Exception e) {
            String error = "Unexpected error during code generation: " + e.getMessage();
            eventService.sendError(sessionId, error);
            throw new RuntimeException(error, e);
        }
    }
    
    /**
     * Fix invalid admin addresses with WebSocket feedback
     */
    private void fixInvalidAdminAddresses(String sessionId, Path leoFile) {
        try {
            eventService.sendInfo(sessionId, "🔍 Checking for invalid admin addresses...");
            
            List<String> lines = Files.readAllLines(leoFile);
            boolean modified = false;
            
            // Pattern to match admin address declarations
            String adminPattern = "(?i)(const\\s+ADMIN\\s*:\\s*address\\s*=\\s*)([a-zA-Z0-9]+)(;)";
            String validAddress = LeoPrompt.ADMIN_PLACEHOLDER;
            
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                
                if (line.matches(".*(?i)const\\s+ADMIN\\s*:.*address.*")) {
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(adminPattern);
                    java.util.regex.Matcher matcher = pattern.matcher(line);
                    
                    if (matcher.find()) {
                        String currentAddress = matcher.group(2);
                        
                        if (!isValidAleoAddress(currentAddress)) {
                            eventService.sendInfo(sessionId, "⚠️ Found invalid address: " + currentAddress);
                            eventService.sendInfo(sessionId, "✅ Replacing with valid address");
                            
                            lines.set(i, matcher.group(1) + validAddress + matcher.group(3));
                            modified = true;
                        }
                    }
                }
            }
            
            if (modified) {
                Files.write(leoFile, lines);
                eventService.sendInfo(sessionId, "✅ Admin addresses fixed successfully!");
            } else {
                eventService.sendInfo(sessionId, "✅ No invalid admin addresses found");
            }
            
        } catch (IOException e) {
            String warning = "Warning: Could not check/fix admin addresses: " + e.getMessage();
            eventService.sendInfo(sessionId, warning);
            logger.warn(warning, e);
        }
    }
    
    private boolean isValidAleoAddress(String address) {
        return address != null 
            && address.length() == 63 
            && address.startsWith("aleo1") 
            && address.matches("^aleo1[a-z0-9]{58}$");
    }

    /**
     * Build project and attempt to fix compilation errors with WebSocket feedback
     */
    public boolean buildAndFix(String sessionId, String projectPath, int maxAttempts) {
        if (projectPath == null) {
            eventService.sendError(sessionId, "Project path is null");
            return false;
        }
        
        eventService.sendInfo(sessionId, "🔨 Starting build and fix process...");
        
        // First build attempt
        eventService.sendBuildStarted(sessionId);
        String buildOutput = attemptBuild(projectPath);
        
        // Check if initial build succeeded
        if (isBuildSuccessful(buildOutput)) {
            eventService.sendBuildSuccess(sessionId);
            eventService.sendProjectComplete(sessionId, projectPath);
            return true;
        }
        
        // Build failed, start correction process
        eventService.sendBuildFailed(sessionId, buildOutput);
        eventService.sendInfo(sessionId, "❌ Initial build failed. Starting automatic correction...");
        
        // Use LeoCodeCorrector with WebSocket integration
        WebLeoCodeCorrector corrector = new WebLeoCodeCorrector(eventService);
        boolean success = corrector.fixCompilationErrors(sessionId, projectPath, maxAttempts);
        
        if (success) {
            eventService.sendProjectComplete(sessionId, projectPath);
        } else {
            eventService.sendFixingFailed(sessionId, maxAttempts);
            eventService.sendError(sessionId, "Manual intervention required after " + maxAttempts + " attempts");
        }
        
        return success;
    }
    
    private String attemptBuild(String projectPath) {
        Path projectDir = Paths.get(projectPath);
        CommandRunner.CommandResult result = CommandRunner.runBash("leo build", projectDir.toFile());
        
        // Combine stdout and stderr for full output
        String fullOutput = result.stdout();
        if (!result.stderr().isEmpty()) {
            fullOutput += "\n" + result.stderr();
        }
        
        return fullOutput;
    }
    
    private boolean isBuildSuccessful(String output) {
        return output.contains("✅ Compiled") || 
               output.contains("Successfully compiled") || 
               (!output.contains("Error") && !output.contains("error"));
    }
}
