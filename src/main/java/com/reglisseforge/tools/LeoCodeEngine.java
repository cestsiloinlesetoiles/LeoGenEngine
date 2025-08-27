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

/**
 * Leo code generation engine with streaming + extended thinking + tool-use auto-fix loop.
 */
public class LeoCodeEngine {

    
    Model model = Model.CLAUDE_3_7_SONNET_LATEST;

    AnthropicClient client;
    String projectPath;


    public LeoCodeEngine() {
        this.client = AnthropicClientFactory.create();
    }

    public void initProject(String projectName, String projectDescription) {
        try {
            Path baseDir = Paths.get(".").toAbsolutePath().normalize();
            Path workspaceDir = baseDir.resolve("leoworkspace");
            Files.createDirectories(workspaceDir);

            // Force project name to lowercase and replace spaces with underscores to avoid Leo parsing issues
            String leoProjectName = projectName.toLowerCase().replaceAll("\\s+", "_");
            
            // 1) Crée un squelette Leo
            String command = "leo new " + leoProjectName;
            CommandRunner.runBash(command, workspaceDir.toFile());

            this.projectPath = workspaceDir.resolve(leoProjectName).toString();

            // 2) Génère le code initial avec LLM
            generate_initial_code(this.projectPath, leoProjectName, projectDescription);
        } catch (IOException e) {
            throw new RuntimeException("ERROR GENERATING PROJECT", e);
        }
    }


    public void generate_initial_code(String projectPath, String projectName, String description) {
        Path outputFile = Paths.get(projectPath, "src", "main.leo");
        
        try {
            // Créer le répertoire src s'il n'existe pas
            Files.createDirectories(outputFile.getParent());
            
            List<BetaTextBlockParam> system = List.of(
                    LeoPrompt.LeoRulesBookParam()
            );

            // Utiliser le prompt fourni par LeoPrompt
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

            System.out.println("🚀 Generating Leo code for: " + projectName);
            System.out.println("📝 Output file: " + outputFile.toAbsolutePath());
            System.out.println("─".repeat(80));
            System.out.println("Thinking... (this may take a while, please wait) will be reduce in future versions");
            // Utiliser BufferedWriter pour une écriture efficace
            try (BufferedWriter writer = Files.newBufferedWriter(outputFile);
                 StreamResponse<BetaRawMessageStreamEvent> streamResponse = client.beta().messages().createStreaming(params)) {
                
                // Compteur pour flush périodique
                AtomicInteger chunkCount = new AtomicInteger(0);
                
                streamResponse.stream()
                        .flatMap(event -> event.contentBlockDelta().stream())
                        .flatMap(deltaEvent -> deltaEvent.delta().text().stream())
                        .forEach(textDelta -> {
                            String chunk = textDelta.text();
                            
                            // Afficher sur la console
                            System.out.print(chunk);
                            
                            // Écrire dans le fichier
                            try {
                                writer.write(chunk);
                                
                                // Flush tous les 5 chunks pour voir les mises à jour
                                if (chunkCount.incrementAndGet() % 5 == 0) {
                                    writer.flush();
                                }
                            } catch (IOException e) {
                                System.err.println("\n⚠️  Error writing chunk to file: " + e.getMessage());
                            }
                        });
                
                // Flush final pour s'assurer que tout est écrit
                writer.flush();
            }
            
            System.out.println("\n" + "─".repeat(80));
            System.out.println("✅ Leo code generated successfully!");
            System.out.println("📄 File saved at: " + outputFile.toAbsolutePath());
            
            // Fix admin addresses before build
            fixInvalidAdminAddresses(outputFile);
            
            // Optionnel : afficher les premières lignes du fichier généré
            System.out.println("\n📋 Generated code preview:");
            System.out.println("─".repeat(80));
            List<String> lines = Files.readAllLines(outputFile);
            lines.stream().limit(10).forEach(System.out::println);
            if (lines.size() > 10) {
                System.out.println("... (" + (lines.size() - 10) + " more lines)");
            }
            
        } catch (IOException e) {
            throw new RuntimeException("ERROR CREATING OUTPUT DIRECTORY OR WRITING FILE", e);
        } catch (Exception e) {
            throw new RuntimeException("ERROR DURING INITIAL CODE GENERATION", e);
        }
    }
    
    /**
     * Detect and fix invalid admin addresses in generated Leo code
     */
    private void fixInvalidAdminAddresses(Path leoFile) {
        try {
            System.out.println("\n🔍 Checking for invalid admin addresses...");
            
            List<String> lines = Files.readAllLines(leoFile);
            boolean modified = false;
            
            // Pattern to match admin address declarations
            String adminPattern = "(?i)(const\\s+ADMIN\\s*:\\s*address\\s*=\\s*)([a-zA-Z0-9]+)(;)";
            String validAddress = LeoPrompt.ADMIN_PLACEHOLDER;
            
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                
                // Check if line contains admin address declaration
                if (line.matches(".*(?i)const\\s+ADMIN\\s*:.*address.*")) {
                    // Extract the address
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(adminPattern);
                    java.util.regex.Matcher matcher = pattern.matcher(line);
                    
                    if (matcher.find()) {
                        String currentAddress = matcher.group(2);
                        
                        // Check if address is valid (63 chars, starts with aleo1)
                        if (!isValidAleoAddress(currentAddress)) {
                            System.out.println("  ⚠️ Found invalid address: " + currentAddress);
                            System.out.println("  ✅ Replacing with valid address: " + validAddress);
                            
                            // Replace the line with valid address
                            lines.set(i, matcher.group(1) + validAddress + matcher.group(3));
                            modified = true;
                        }
                    }
                }
            }
            
            if (modified) {
                // Write the fixed content back
                Files.write(leoFile, lines);
                System.out.println("  ✅ Admin addresses fixed successfully!");
            } else {
                System.out.println("  ✅ No invalid admin addresses found.");
            }
            
        } catch (IOException e) {
            System.err.println("⚠️ Warning: Could not check/fix admin addresses: " + e.getMessage());
        }
    }
    
    private boolean isValidAleoAddress(String address) {
        return address != null 
            && address.length() == 63 
            && address.startsWith("aleo1") 
            && address.matches("^aleo1[a-z0-9]{58}$");
    }
    /**
     * Builds the project and attempts to fix compilation errors
     * 
     * @return true if build succeeded, false otherwise
     */
    public boolean buildAndFix() {
        if (this.projectPath == null) {
            throw new IllegalStateException("Project not initialized. Call initProject first.");
        }
        
        System.out.println("\n" + "═".repeat(80));
        System.out.println("🔨 Starting build and fix process...");
        System.out.println("═".repeat(80));
        
        // First try to build
        String buildOutput = attemptBuild();
        
        // Check if initial build succeeded
        if (isBuildSuccessful(buildOutput)) {
            System.out.println("✅ Initial build succeeded! No fixes needed.");
            return true;
        }
        
        // If build failed, start correction loop
        System.out.println("❌ Initial build failed. Starting automatic correction...");
        System.out.println("📝 Error output:");
        System.out.println(buildOutput);
        System.out.println("─".repeat(80));
        
        // Use LeoCodeCorrector to fix errors
        LeoCodeCorrector corrector = new LeoCodeCorrector();
        boolean success = corrector.fixCompilationErrors(this.projectPath, 20);
        
        if (success) {
            System.out.println("\n✅ Successfully fixed all compilation errors!");
        } else {
            System.out.println("\n❌ Failed to fix all errors after 20 attempts.");
            System.out.println("Manual intervention required.");
        }
        
        return success;
    }
    
    private String attemptBuild() {
        Path projectDir = Paths.get(this.projectPath);
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
