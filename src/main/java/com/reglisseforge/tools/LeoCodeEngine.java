package com.reglisseforge.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.reglisseforge.utils.CommandRunner;

public class LeoCodeEngine {

    Model model = Model.CLAUDE_3_7_SONNET_LATEST;
    MessageCreateParams.Builder base;

    public void init(Model model) {
        this.model = model;
        this.base = MessageCreateParams.builder().model(model).maxTokens(1024L);
    }


    public static String generatedProject(String projectName) {
        try {
            // 1. Répertoire courant
            Path baseDir = Paths.get(".").toAbsolutePath().normalize();
    
            // 2. Construire le chemin vers le workspace
            Path workspaceDir = baseDir.resolve("leoworkspace");
    
            // 3. Créer le dossier s'il n'existe pas
            Files.createDirectories(workspaceDir);
    
            // 4. Construire la commande
            String command = "leo new " + projectName;
    
            // 5. Exécuter la commande DANS le workspace
            CommandRunner.runBash(command, workspaceDir.toFile());
    
            return workspaceDir.resolve(projectName).toString();
        } catch (IOException e) {
            throw new RuntimeException("ERROR GENERATING PROJECT", e);
        }
    }

    
    
    



    public String architect(String projectName, String description) {
        // TODO: Implement
        return null;
    }


    public String continueGeneration(String projectName) {
        // TODO: Implement
        return null;
    }
    
    public String loopFixAndBuild(String projectName) {
        // TODO: Implement

        return null;
    }

    public String build(String projectName) {
        // TODO: Implement

        return null;
    }
    
}