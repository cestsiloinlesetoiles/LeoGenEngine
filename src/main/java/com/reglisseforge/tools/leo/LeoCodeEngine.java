package com.reglisseforge.tools.leo;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.anthropic.client.AnthropicClient;
 
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.TextBlockParam;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reglisseforge.tools.base.Param;
import com.reglisseforge.tools.base.Tool;
import com.reglisseforge.utils.CommandRunner;
import com.reglisseforge.utils.AnthropicClientFactory;
import com.reglisseforge.utils.StreamTextAccumulator;

public class LeoCodeEngine {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ADMIN_PLACEHOLDER = "xxxx";
    private static final String SYSTEM_PROMPT = """
        You are an expert Leo programmer specialized in secure blockchain code generation.
        
        GENERAL INSTRUCTIONS:
        - Generate only valid and compilable Leo code (no markdown, no extra text).
        - Use explicit types and add concise comments.
        - Strictly follow Leo reference rules.
        
        REFERENCE RULES:
        - Records MUST have owner: address as first field
        - All numeric literals need type suffixes: 42u64, 1field, true
        - Transitions that modify mappings MUST be async
        - Use self.signer for record ownership, self.caller for contract calls
        - Default visibility is private if not specified
        """;
    private static final String REPORT_SYSTEM_PROMPT = """
        You are a technical auditor. Produce concise, structured reports in JSON only.
        - Provide a summary, generation status, possible risks/scenarios, and actionable steps.
        - Analyze build logs if provided, detect error classes and practical fixes.
        - Output strictly JSON. Do not include any text outside JSON.
        """;
    private static final String EDIT_SYSTEM_PROMPT = """
        You are a Leo code repair assistant.
        
        EDIT INSTRUCTIONS:
        - Apply minimal, local edits only (span editing). Do not rewrite the whole file.
        - Fix exactly the provided span to resolve the compiler error(s).
        - Return ONLY the corrected code to replace the span (no backticks, no explanations).
        - Preserve existing style and semantics. Keep the same intent.
        
        """ + LeoPrompts.LEO_CORE_RULES;

    /**
     * Selects the model to use. When thinking is requested, attempts to use a thinking-capable
     * model if available in the SDK, otherwise falls back to the default Sonnet model.
     */
    private static Model selectModel(boolean thinking) {
        if (thinking) {
            String[] thinkingCandidates = new String[] {
                "CLAUDE_3_7_SONNET_THINKING_LATEST",
                "CLAUDE_3_7_SONNET_THINKING",
                "CLAUDE_3_7_HAIKU_THINKING_LATEST",
                "CLAUDE_3_7_HAIKU_THINKING"
            };
            for (String candidate : thinkingCandidates) {
                Model m = getModelByStaticField(candidate);
                if (m != null) return m;
            }
        }
        return Model.CLAUDE_3_7_SONNET_LATEST;
    }

    private static Model getModelByStaticField(String fieldName) {
        try {
            java.lang.reflect.Field f = Model.class.getField(fieldName);
            Object v = f.get(null);
            if (v instanceof Model) {
                return (Model) v;
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
        }
        return null;
    }

    private static String tail(String s, int maxChars) {
        if (s == null) return "";
        if (s.length() <= maxChars) return s;
        return s.substring(s.length() - maxChars);
    }

    @Tool(name = "leo_init", description = "Initialize a new Leo project with basic structure")
    public String init(
            @Param(name = "project_name", description = "Name of the Leo project", required = true) String projectName,
            @Param(name = "directory", description = "Directory where to create the project", required = false, defaultValue = ".") String directory,
            @Param(name = "description", description = "Description of what the Leo project should do", required = true) String description
    ) throws Exception {
        Path baseDir = Paths.get(directory).toAbsolutePath().normalize();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", projectName);
        result.put("directory", baseDir.toString());

        // Créer le projet Leo
        LeoNewTool leoNewTool = new LeoNewTool();
        String createResult = leoNewTool.createProject(projectName, directory);
        
        // Vérifier si la création a réussi
        Map<String, Object> createData = JSON.readValue(createResult, new TypeReference<Map<String, Object>>() {});
        if (!(Boolean) createData.get("success")) {
            result.put("error", "project_creation_failed");
            result.put("details", createData);
            return JSON.writeValueAsString(result);
        }

        Path projectPath = baseDir.resolve(projectName);
        
        // Générer le code initial en streaming (sans Agent) et l'écrire en direct dans src/main.leo
        Path mainLeo = projectPath.resolve("src/main.leo");
        String genPrompt = String.format(
            "Generate Leo code for project '%s'.\n" +
            "PROJECT DESCRIPTION: %s\n\n" +
            "OUTPUT CONSTRAINTS:\n" +
            "- Emit ONLY raw Leo code for src/main.leo (no markdown, no extra text)\n" +
            "- Include at least one struct and one transition with concise comments\n" +
            "- Implement functionality based on the project description\n" +
            "- Use admin address placeholder: xxxx (do not use a real address; we will replace later)\n",
            projectName, description
        );

        AtomicReference<Exception> writeError = new AtomicReference<>(null);
        String generated;
        AnthropicClient client = AnthropicClientFactory.create();
        // Cache-aware system prompt (ephemeral) like Agent.applySystem
        TextBlockParam sysBlock = TextBlockParam.builder()
                .text(SYSTEM_PROMPT)
                .cacheControl(CacheControlEphemeral.builder().build())
                .build();
        MessageCreateParams genReq = MessageCreateParams.builder()
                .model(selectModel(true))
                .maxTokens(2048L)
                .systemOfTextBlockParams(java.util.List.of(sysBlock))
                .addUserMessage(genPrompt)
                .build();

        try (BufferedWriter writer = Files.newBufferedWriter(
                    mainLeo,
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                    java.nio.file.StandardOpenOption.WRITE
            );
            StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(genReq)) {
            StreamTextAccumulator acc = new StreamTextAccumulator(chunk -> {
                try {
                    writer.write(chunk);
                    writer.flush();
                } catch (Exception ioe) {
                    writeError.compareAndSet(null, ioe);
                }
            });
            stream.stream().forEachOrdered(acc::onEvent);
            // Ensure last buffered bytes are flushed before leaving try-with-resources
            writer.flush();
            generated = acc.getText();
        }

        // Vérifier l'écriture
        if (writeError.get() != null) {
            result.put("success", false);
            result.put("error", "write_failed");
            result.put("details", writeError.get().toString());
            return JSON.writeValueAsString(result);
        }

        // Remplacer le placeholder d'adresse admin si présent
        replaceAdminPlaceholder(mainLeo);

        // Construire le projet pour détecter d'éventuelles erreurs
        CommandRunner.CommandResult buildOut = CommandRunner.runBash("leo build", projectPath.toFile());
        boolean buildSuccess = buildOut.exitCode() == 0;

        // Boucle de réparation ciblée (span editing) si le build échoue
        int repairAttempts = 0;
        while (!buildSuccess && repairAttempts < 3) {
            boolean repaired = attemptLocalRepair(projectPath, mainLeo, buildOut.stderr());
            if (!repaired) break;
            repairAttempts++;
            buildOut = CommandRunner.runBash("leo build", projectPath.toFile());
            buildSuccess = buildOut.exitCode() == 0;
        }

        // Tronquer les logs pour le rapport (tail)
        int maxChars = 6000;
        String stdoutForReport = tail(buildOut.stdout(), maxChars);
        String stderrForReport = tail(buildOut.stderr(), maxChars);

        String reportPrompt = String.format("""
                                            Write a concise JSON report covering: summary, generation_status, files, metrics, build, errors, scenarios, risks, actions.
                                            Context:
                                            - project: %s
                                            - directory: %s
                                            - main_file: %s
                                            - generated_chars: %d
                                            - build_exit_code: %d
                                            - build_success: %s
                                            - build_stdout_tail_last_%d_chars:
                                            %s
                                            - build_stderr_tail_last_%d_chars:
                                            %s
                                            Return JSON only (no extra text).""",
            projectName,
            projectPath.toString(),
            mainLeo.toString(),
            generated == null ? 0 : generated.length(),
            buildOut.exitCode(),
            Boolean.toString(buildSuccess),
            maxChars,
            stdoutForReport,
            maxChars,
            stderrForReport
        );
        
        TextBlockParam reportSysBlock = TextBlockParam.builder()
                .text(REPORT_SYSTEM_PROMPT)
                .cacheControl(CacheControlEphemeral.builder().build())
                .build();
        MessageCreateParams reportReq = MessageCreateParams.builder()
                .model(Model.CLAUDE_3_7_SONNET_LATEST)
                .maxTokens(768L)
                .systemOfTextBlockParams(java.util.List.of(reportSysBlock))
                .addUserMessage(reportPrompt)
                .build();
        String reportText;
        try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(reportReq)) {
            StreamTextAccumulator acc = new StreamTextAccumulator();
            stream.stream().forEach(acc::onEvent);
            reportText = acc.getText();
        }
        
        // Historique minimal pour réutilisation ultérieure avec un autre stream call
        List<Map<String, Object>> history = List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", genPrompt),
                Map.of("role", "assistant", "content", generated)
        );
        result.put("history", history);
        
        // Sauvegarder l'historique dans un fichier pour la continuation
        try {
            saveHistoryToFile(projectPath, history);
        } catch (Exception e) {
            // Log mais ne pas échouer pour ça
            System.err.println("Warning: Could not save history: " + e.getMessage());
        }
        
        result.put("success", true);
        result.put("status", "initialized_with_generated_code");
        result.put("summary", String.format(
            "Projet Leo '%s' créé avec succès. Code initial généré et intégré dans src/main.leo.", 
            projectName
        ));
        result.put("generated_chars", generated == null ? 0 : generated.length());
        result.put("build_success", buildSuccess);
        result.put("build_exit_code", buildOut.exitCode());
        result.put("build_log", buildOut.stdout());
        result.put("build_stderr", buildOut.stderr());
        result.put("report", reportText);
        
        return JSON.writeValueAsString(result);
    }

    @Tool(name = "leo_continue", description = "Continue incomplete Leo code generation")
    public String continueGeneration(
            @Param(name = "project_name", description = "Name of the Leo project", required = true) String projectName,
            @Param(name = "directory", description = "Directory of the project", required = false, defaultValue = ".") String directory
    ) throws Exception {
        Path baseDir = Paths.get(directory).toAbsolutePath().normalize();
        Path projectPath = baseDir.resolve(projectName);
        Path mainLeo = projectPath.resolve("src/main.leo");
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", projectName);
        result.put("directory", projectPath.toString());
        
        // Charger l'historique depuis le fichier
        List<Map<String, Object>> history;
        try {
            history = loadHistoryFromFile(projectPath);
        } catch (Exception e) {
            result.put("error", "history_load_failed");
            result.put("success", false);
            result.put("details", e.getMessage());
            return JSON.writeValueAsString(result);
        }
        
        if (history.isEmpty()) {
            result.put("error", "no_history_found");
            result.put("success", false);
            result.put("message", "Aucun historique trouvé. Lancez d'abord init()");
            return JSON.writeValueAsString(result);
        }
        
        // Lire le code existant pour vérifier s'il est incomplet
        if (!Files.exists(mainLeo)) {
            result.put("error", "main_leo_not_found");
            result.put("success", false);
            return JSON.writeValueAsString(result);
        }
        
        String existingCode = Files.readString(mainLeo, StandardCharsets.UTF_8);
        boolean isIncomplete = detectIncompleteCode(existingCode);
        
        if (!isIncomplete) {
            result.put("success", true);
            result.put("status", "code_already_complete");
            result.put("message", "Le code semble déjà complet");
            return JSON.writeValueAsString(result);
        }
        
        // Continuer la génération en utilisant l'historique
        String continuePrompt = "Continue where you left off. Complete the remaining code. " +
            "Only output the missing parts that need to be added to complete the program. " +
            "Ensure all functions and finalize blocks are complete and the program ends with '}'.";
        
        AtomicReference<Exception> writeError = new AtomicReference<>(null);
        String generated;
        AnthropicClient client = AnthropicClientFactory.create();
        
        TextBlockParam sysBlock = TextBlockParam.builder()
                .text(SYSTEM_PROMPT)
                .cacheControl(CacheControlEphemeral.builder().build())
                .build();
        
        // Construire la requête en rejouant l'historique
        MessageCreateParams.Builder genReqBuilder = MessageCreateParams.builder()
                .model(selectModel(true))
                .maxTokens(2048L)
                .systemOfTextBlockParams(java.util.List.of(sysBlock));

        for (Map<String, Object> entry : history) {
            String role = (String) entry.get("role");
            String content = (String) entry.get("content");
            if ("user".equals(role)) {
                genReqBuilder.addUserMessage(content);
            } else if ("assistant".equals(role)) {
                genReqBuilder.addAssistantMessage(content);
            }
            // Les messages système sont gérés via systemOfTextBlockParams
        }

        // Ajouter le message de continuation
        genReqBuilder.addUserMessage(continuePrompt);

        MessageCreateParams genReq = genReqBuilder.build();

        try (BufferedWriter writer = Files.newBufferedWriter(
                    mainLeo,
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.APPEND
            );
            StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(genReq)) {
            StreamTextAccumulator acc = new StreamTextAccumulator(chunk -> {
                try {
                    writer.write(chunk);
                    writer.flush();
                } catch (Exception ioe) {
                    writeError.compareAndSet(null, ioe);
                }
            });
            stream.stream().forEachOrdered(acc::onEvent);
            // Ensure last buffered bytes are flushed before leaving try-with-resources
            writer.flush();
            generated = acc.getText();
        }

        if (writeError.get() != null) {
            result.put("success", false);
            result.put("error", "write_failed");
            result.put("details", writeError.get().toString());
            return JSON.writeValueAsString(result);
        }

        // Remplacer le placeholder d'adresse admin si présent
        replaceAdminPlaceholder(mainLeo);

        // Test de build
        CommandRunner.CommandResult buildOut = CommandRunner.runBash("leo build", projectPath.toFile());
        boolean buildSuccess = buildOut.exitCode() == 0;

        // Boucle de réparation ciblée (span editing) si le build échoue
        int repairAttempts = 0;
        while (!buildSuccess && repairAttempts < 3) {
            boolean repaired = attemptLocalRepair(projectPath, mainLeo, buildOut.stderr());
            if (!repaired) break;
            repairAttempts++;
            buildOut = CommandRunner.runBash("leo build", projectPath.toFile());
            buildSuccess = buildOut.exitCode() == 0;
        }

        // Mettre à jour l'historique avec la nouvelle génération
        List<Map<String, Object>> updatedHistory = new ArrayList<>();
        for (Map<String, Object> entry : history) {
            updatedHistory.add(new LinkedHashMap<>(entry));
        }
        updatedHistory.add(Map.of("role", "user", "content", continuePrompt));
        updatedHistory.add(Map.of("role", "assistant", "content", generated));

        // Sauvegarder l'historique mis à jour
        try {
            saveHistoryToFile(projectPath, updatedHistory);
        } catch (Exception e) {
            System.err.println("Warning: Could not save updated history: " + e.getMessage());
        }

        // Nouvelle logique: validation post-génération et boucle build/edit
        Map<String, Object> finalValidation = performPostGenerationValidation(projectPath, mainLeo, buildSuccess, buildOut);
        
        result.put("success", (Boolean) finalValidation.get("success"));
        result.put("status", "continued_generation_with_validation");
        result.put("generated_chars", generated == null ? 0 : generated.length());
        result.put("build_success", (Boolean) finalValidation.get("final_build_success"));
        result.put("build_exit_code", (Integer) finalValidation.get("final_exit_code"));
        result.put("build_log", (String) finalValidation.get("final_build_log"));
        result.put("build_stderr", (String) finalValidation.get("final_build_stderr"));
        result.put("history_entries", updatedHistory.size());
        result.put("validation_details", finalValidation);
        
        return JSON.writeValueAsString(result);
    }
    
    private boolean detectIncompleteCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return true;
        }
        
        String trimmed = code.trim();
        
        // Vérifications syntaxiques de base
        if (!trimmed.endsWith("}")) {
            return true;
        }
        
        // Vérifier les accolades équilibrées
        int openBraces = 0;
        for (char c : code.toCharArray()) {
            if (c == '{') openBraces++;
            else if (c == '}') openBraces--;
        }
        if (openBraces != 0) {
            return true;
        }
        
        // Vérifier les finalize incomplets
        if (code.contains("finalize") && !code.substring(code.lastIndexOf("finalize")).contains("}")) {
            return true;
        }
        
        // Vérifier les transitions incomplètes
        Pattern transitionPattern = Pattern.compile("transition\\s+\\w+\\s*\\([^)]*\\)\\s*(?:->[^{]*)?\\s*\\{");
        Matcher matcher = transitionPattern.matcher(code);
        while (matcher.find()) {
            String afterTransition = code.substring(matcher.end());
            if (!afterTransition.contains("}")) {
                return true;
            }
        }
        
        // Vérifier les structures incomplètes
        if (code.contains("struct") && !Pattern.compile("struct\\s+\\w+\\s*\\{[^}]*\\}").matcher(code).find()) {
            return true;
        }
        
        // Vérifier les appels de fonctions non définies
        Pattern callPattern = Pattern.compile("(\\w+)\\s*\\(");
        Matcher callMatcher = callPattern.matcher(code);
        while (callMatcher.find()) {
            String funcName = callMatcher.group(1);
            if (!isBuiltinFunction(funcName) && !code.contains("function " + funcName) && 
                !code.contains("inline " + funcName) && !code.contains("transition " + funcName)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean isBuiltinFunction(String funcName) {
        // Fonctions built-in communes de Leo
        Set<String> builtins = Set.of("assert", "assert_eq", "assert_neq", "get", "get_or_use", "set", 
                                     "contains", "remove", "square_root", "pow", "abs", "min", "max");
        return builtins.contains(funcName);
    }

    private List<Map<String, Object>> loadHistoryFromFile(Path projectPath) throws Exception {
        Path historyFile = projectPath.resolve(".leo_history.json");
        if (!Files.exists(historyFile)) {
            return new ArrayList<>();
        }
        
        String historyJson = Files.readString(historyFile, StandardCharsets.UTF_8);
        return JSON.readValue(historyJson, new TypeReference<List<Map<String, Object>>>() {});
    }
    
    private void saveHistoryToFile(Path projectPath, List<Map<String, Object>> history) throws Exception {
        Path historyFile = projectPath.resolve(".leo_history.json");
        String historyJson = JSON.writeValueAsString(history);
        Files.writeString(historyFile, historyJson, StandardCharsets.UTF_8);
    }
    
    /**
     * Tente une réparation locale (span editing) à partir d'un message d'erreur du compilateur.
     * Retourne true si une modification a été appliquée.
     */
    private boolean attemptLocalRepair(Path projectPath, Path mainLeo, String compilerStderr) {
        try {
            // Exemple d'erreur: "--> /path/simple_dex/src/main.leo:76:13"
            int line = extractErrorLine(compilerStderr);
            if (line <= 0) return false;

            String code = Files.readString(mainLeo, StandardCharsets.UTF_8);
            String[] lines = code.split("\n", -1);
            // Définir une fenêtre locale autour de la ligne fautive
            int start = Math.max(0, line - 3);
            int end = Math.min(lines.length, line + 2);
            StringBuilder spanBuilder = new StringBuilder();
            for (int i = start; i < end; i++) {
                spanBuilder.append(lines[i]).append('\n');
            }
            String faultySpan = spanBuilder.toString();

            // Construire la requête d'édition minimale
            AnthropicClient client = AnthropicClientFactory.create();
            TextBlockParam sysBlock = TextBlockParam.builder()
                    .text(EDIT_SYSTEM_PROMPT)
                    .cacheControl(CacheControlEphemeral.builder().build())
                    .build();

            String editPrompt = "Compiler error:\n" + compilerStderr +
                    "\n\nFile: " + mainLeo +
                    "\nSpan (replace this only):\n" + faultySpan +
                    "\n\nReturn ONLY the corrected code for the span.";

            MessageCreateParams req = MessageCreateParams.builder()
                    .model(Model.CLAUDE_3_7_SONNET_LATEST)
                    .maxTokens(512L)
                    .systemOfTextBlockParams(java.util.List.of(sysBlock))
                    .addUserMessage(editPrompt)
                    .build();

            String edited;
            try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(req)) {
                StreamTextAccumulator acc = new StreamTextAccumulator();
                stream.stream().forEach(acc::onEvent);
                edited = acc.getText();
            }

            if (edited == null || edited.isBlank()) return false;

            // Remplacer la fenêtre par l'édition renvoyée
            StringBuilder newCode = new StringBuilder();
            for (int i = 0; i < start; i++) newCode.append(lines[i]).append('\n');
            newCode.append(edited);
            if (!edited.endsWith("\n")) newCode.append('\n');
            for (int i = end; i < lines.length; i++) newCode.append(lines[i]).append('\n');

            Files.writeString(mainLeo, newCode.toString(), StandardCharsets.UTF_8);

            // Essayer aussi de remplacer le placeholder admin si besoin
            replaceAdminPlaceholder(mainLeo);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private int extractErrorLine(String stderr) {
        if (stderr == null) return -1;
        // Cherche un motif ":<line>:<col>" sur le chemin du fichier
        // Exemple: ":76:13" → ligne 76
        Pattern pattern = Pattern.compile(":(\\d+):\\d+\\s*$", Pattern.MULTILINE);
        java.util.regex.Matcher m = pattern.matcher(stderr);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) { }
        }
        return -1;
    }

    /**
     * Remplace le placeholder 'xxxx' utilisé pour l'adresse ADMIN par une vraie adresse
     * fournie via l'environnement LEO_ADMIN_ADDRESS. La substitution est simple et sûre:
     * - Ne fait rien si la variable n'est pas définie
     * - Remplace uniquement un littéral d'adresse exact 'xxxx' (pas d'autres occurrences textuelles)
     */
    private void replaceAdminPlaceholder(Path mainLeo) {
        try {
            String admin = System.getenv("LEO_ADMIN_ADDRESS");
            if (admin == null || admin.isBlank()) {
                return;
            }

            String code = Files.readString(mainLeo, StandardCharsets.UTF_8);

            // Remplace uniquement l'adresse si elle apparaît comme littéral d'adresse Leo
            // Exemples ciblés:
            //   const ADMIN: address = xxxx;
            //   owner: address = xxxx
            //   const X: address = xxxx
            Pattern p = Pattern.compile("(:\\s*address\\s*=\\s*)" + Pattern.quote(ADMIN_PLACEHOLDER) + "(\\b)");
            Matcher m = p.matcher(code);
            String replaced = m.replaceAll("$1" + admin + "$2");

            if (!replaced.equals(code)) {
                Files.writeString(mainLeo, replaced, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            // Ne pas faire échouer le flux si la substitution échoue
        }
    }

    

    @Tool(name = "leo_edit", description = "Edit and fix Leo code with intelligent suggestions")
    public String edit(
            @Param(name = "workspace", description = "Path to the Leo project workspace", required = false, defaultValue = ".") String workspace,
            @Param(name = "operation", description = "Type of edit operation (fix, optimize, refactor)", required = false, defaultValue = "fix") String operation,
            @Param(name = "target", description = "Specific function or section to edit", required = false) String target
    ) throws Exception {
        Path workspacePath = Paths.get(workspace).toAbsolutePath().normalize();
        Path mainLeo = workspacePath.resolve("src/main.leo");
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workspace", workspacePath.toString());
        result.put("operation", operation);
        
        if (!Files.exists(mainLeo)) {
            result.put("error", "main_leo_not_found");
            result.put("success", false);
            return JSON.writeValueAsString(result);
        }
        
        String code = Files.readString(mainLeo, StandardCharsets.UTF_8);
        
        // Effectuer le build pour identifier les erreurs
        CommandRunner.CommandResult buildOut = CommandRunner.runBash("leo build", workspacePath.toFile());
        boolean hasErrors = buildOut.exitCode() != 0;
        
        if (!hasErrors && "fix".equals(operation)) {
            result.put("success", true);
            result.put("status", "no_errors_found");
            result.put("message", "Le code ne présente pas d'erreurs de compilation");
            return JSON.writeValueAsString(result);
        }
        
        // Analyser et corriger les erreurs
        Map<String, Object> editResult = performTargetedEdit(workspacePath, mainLeo, buildOut.stderr(), target, operation);
        result.putAll(editResult);
        
        return JSON.writeValueAsString(result);
    }

    @Tool(name = "leo_build", description = "Build the Leo project with error analysis and suggestions")
    public String build(
            @Param(name = "workspace", description = "Path to the Leo project workspace", required = false, defaultValue = ".") String workspace,
            @Param(name = "mode", description = "Build mode (development, release)", required = false, defaultValue = "development") String mode
    ) throws Exception {
        // TODO: Implementation
        return null;
    }
    
    /**
     * Effectue la validation post-génération avec boucle build/edit
     */
    private Map<String, Object> performPostGenerationValidation(Path projectPath, Path mainLeo, 
                                                               boolean initialBuildSuccess, 
                                                               CommandRunner.CommandResult initialBuildOut) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> editLog = new ArrayList<>();
        
        try {
            // Lire le fichier complet
            String code = Files.readString(mainLeo, StandardCharsets.UTF_8);
            
            // Vérifier si le code est vraiment complet
            boolean isComplete = !detectIncompleteCode(code);
            result.put("code_complete", isComplete);
            
            if (!isComplete) {
                result.put("success", false);
                result.put("error", "code_still_incomplete");
                result.put("final_build_success", false);
                result.put("final_exit_code", -1);
                result.put("final_build_log", "");
                result.put("final_build_stderr", "Code incomplet détecté");
                return result;
            }
            
            // Entrer dans la boucle build/edit
            boolean buildSuccess = initialBuildSuccess;
            CommandRunner.CommandResult buildOut = initialBuildOut;
            int maxIterations = 5;
            int iteration = 0;
            
            editLog.add("Initial build success: " + buildSuccess);
            
            while (!buildSuccess && iteration < maxIterations) {
                iteration++;
                editLog.add("Iteration " + iteration + ": Attempting repair...");
                
                // Essayer la réparation locale existante
                boolean repaired = attemptLocalRepair(projectPath, mainLeo, buildOut.stderr());
                
                if (!repaired) {
                    editLog.add("Local repair failed, trying intelligent edit...");
                    // Essayer une édition plus intelligente
                    Map<String, Object> editResult = performTargetedEdit(projectPath, mainLeo, 
                                                                        buildOut.stderr(), null, "fix");
                    repaired = (Boolean) editResult.getOrDefault("success", false);
                    editLog.add("Intelligent edit result: " + repaired);
                }
                
                if (!repaired) {
                    editLog.add("All repair attempts failed for iteration " + iteration);
                    break;
                }
                
                // Rebuild après réparation
                buildOut = CommandRunner.runBash("leo build", projectPath.toFile());
                buildSuccess = buildOut.exitCode() == 0;
                editLog.add("Build after repair: " + buildSuccess);
                
                // Pause courte pour éviter surcharge
                Thread.sleep(100);
            }
            
            result.put("success", buildSuccess);
            result.put("final_build_success", buildSuccess);
            result.put("final_exit_code", buildOut.exitCode());
            result.put("final_build_log", buildOut.stdout());
            result.put("final_build_stderr", buildOut.stderr());
            result.put("iterations_performed", iteration);
            result.put("max_iterations_reached", iteration >= maxIterations);
            result.put("edit_log", editLog);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "validation_exception");
            result.put("details", e.getMessage());
            result.put("edit_log", editLog);
        }
        
        return result;
    }
    
    /**
     * Effectue une édition ciblée plus intelligente que attemptLocalRepair
     */
    private Map<String, Object> performTargetedEdit(Path projectPath, Path mainLeo, String errorMessage, 
                                                   String target, String operation) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        try {
            String code = Files.readString(mainLeo, StandardCharsets.UTF_8);
            
            // Analyser l'erreur pour déterminer la stratégie d'édition
            String editStrategy = determineEditStrategy(errorMessage, code, target);
            result.put("edit_strategy", editStrategy);
            
            AnthropicClient client = AnthropicClientFactory.create();
            TextBlockParam sysBlock = TextBlockParam.builder()
                    .text(EDIT_SYSTEM_PROMPT)
                    .cacheControl(CacheControlEphemeral.builder().build())
                    .build();
            
            String editPrompt = buildIntelligentEditPrompt(errorMessage, code, target, operation, editStrategy);
            
            MessageCreateParams req = MessageCreateParams.builder()
                    .model(Model.CLAUDE_3_7_SONNET_LATEST)
                    .maxTokens(1024L)
                    .systemOfTextBlockParams(java.util.List.of(sysBlock))
                    .addUserMessage(editPrompt)
                    .build();
            
            String edited;
            try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(req)) {
                StreamTextAccumulator acc = new StreamTextAccumulator();
                stream.stream().forEach(acc::onEvent);
                edited = acc.getText();
            }
            
            if (edited != null && !edited.isBlank()) {
                // Appliquer l'édition selon la stratégie
                boolean applied = applyEditByStrategy(mainLeo, code, edited, editStrategy, errorMessage);
                
                if (applied) {
                    replaceAdminPlaceholder(mainLeo);
                    result.put("success", true);
                    result.put("edit_applied", true);
                } else {
                    result.put("success", false);
                    result.put("edit_applied", false);
                    result.put("error", "failed_to_apply_edit");
                }
            } else {
                result.put("success", false);
                result.put("error", "empty_edit_response");
            }
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "edit_exception");
            result.put("details", e.getMessage());
        }
        
        return result;
    }
    
    private String determineEditStrategy(String errorMessage, String code, String target) {
        if (errorMessage.contains("syntax error") || errorMessage.contains("parse error")) {
            return "syntax_fix";
        }
        if (errorMessage.contains("type") && errorMessage.contains("mismatch")) {
            return "type_fix";
        }
        if (errorMessage.contains("undefined") || errorMessage.contains("not found")) {
            return "definition_fix";
        }
        if (target != null && !target.isEmpty()) {
            return "targeted_fix";
        }
        return "general_fix";
    }
    
    private String buildIntelligentEditPrompt(String errorMessage, String code, String target, 
                                            String operation, String editStrategy) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Compiler Error:\n").append(errorMessage).append("\n\n");
        prompt.append("Edit Strategy: ").append(editStrategy).append("\n");
        prompt.append("Operation: ").append(operation).append("\n\n");
        
        if (target != null && !target.isEmpty()) {
            prompt.append("Target: ").append(target).append("\n\n");
        }
        
        switch (editStrategy) {
            case "syntax_fix":
                prompt.append("Fix syntax errors in the following Leo code. Return the complete corrected file:\n");
                break;
            case "type_fix":
                prompt.append("Fix type mismatches in the following Leo code. Return the complete corrected file:\n");
                break;
            case "definition_fix":
                prompt.append("Add missing definitions or fix undefined references. Return the complete corrected file:\n");
                break;
            case "targeted_fix":
                prompt.append("Fix the specific issue in the targeted section. Return the complete corrected file:\n");
                break;
            default:
                prompt.append("Fix all issues in the following Leo code. Return the complete corrected file:\n");
        }
        
        prompt.append("\n").append(code);
        
        return prompt.toString();
    }
    
    private boolean applyEditByStrategy(Path mainLeo, String originalCode, String editedCode, 
                                      String strategy, String errorMessage) {
        try {
            // Pour la plupart des cas, remplacer le fichier entier
            if (editedCode.contains("program ") && editedCode.contains("{") && editedCode.trim().endsWith("}")) {
                Files.writeString(mainLeo, editedCode, StandardCharsets.UTF_8);
                return true;
            }
            
            // Si l'édition ne semble pas être un fichier complet, essayer span editing
            int line = extractErrorLine(errorMessage);
            if (line > 0) {
                String[] lines = originalCode.split("\n", -1);
                if (line <= lines.length) {
                    // Remplacer la ligne erronée
                    lines[line - 1] = editedCode.trim();
                    String newCode = String.join("\n", lines);
                    Files.writeString(mainLeo, newCode, StandardCharsets.UTF_8);
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}