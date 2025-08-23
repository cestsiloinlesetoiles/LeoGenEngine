package com.reglisseforge.tools;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class WorkspaceScannerTool {

    /**
     * Lists all Leo projects inside the "leoworkspace" directory
     * and checks if they contain a main.leo file.
     *
     * @return message with projects, their paths, and main.leo status.
     */
    public static String listProjects() {
        try {
            Path baseDir = Paths.get(".").toAbsolutePath().normalize();
            Path workspaceDir = baseDir.resolve("leoworkspace");

            if (!Files.exists(workspaceDir) || !Files.isDirectory(workspaceDir)) {
                return "No 'leoworkspace' directory found in the current repository: " + baseDir;
            }

            List<String> projects = new ArrayList<>();

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(workspaceDir)) {
                for (Path projectDir : stream) {
                    if (Files.isDirectory(projectDir)) {
                        Path mainFile = projectDir.resolve("src").resolve("main.leo");
                        if (Files.exists(mainFile)) {
                            projects.add(projectDir.getFileName() + " -> " 
                                         + projectDir.toAbsolutePath() 
                                         + " (main.leo found: " + mainFile + ")");
                        } else {
                            projects.add(projectDir.getFileName() + " -> " 
                                         + projectDir.toAbsolutePath() 
                                         + " (main.leo not found)");
                        }
                    }
                }
            }

            if (projects.isEmpty()) {
                return "No projects found inside 'leoworkspace'.";
            }

            StringBuilder sb = new StringBuilder("Projects in 'leoworkspace':\n");
            for (String project : projects) {
                sb.append(" - ").append(project).append("\n");
            }

            return sb.toString();

        } catch (IOException e) {
            return "Error scanning workspace: " + e.getMessage();
        }
    }
}
