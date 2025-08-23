package com.reglisseforge.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FileEditorTool {

    /**
     * Remplace une plage de lignes dans un fichier par un contenu donné.
     *
     * @param filePath   chemin du fichier
     * @param startLine  première ligne à remplacer (1-based, inclus)
     * @param endLine    dernière ligne à remplacer (1-based, inclus)
     * @param newContent contenu à insérer (plusieurs lignes séparées par \n)
     * @return message de succès ou d’erreur
     */
    public static String editFile(String filePath, int startLine, int endLine, String newContent) {
        try {
            Path path = Path.of(filePath);

            if (!Files.exists(path)) {
                return "Error: File does not exist -> " + filePath;
            }

            List<String> lines = new ArrayList<>(Files.readAllLines(path));

            if (startLine < 1 || endLine > lines.size() || startLine > endLine) {
                return "Error: Invalid line range " + startLine + "-" + endLine;
            }

            // Supprimer la plage
            for (int i = endLine; i >= startLine; i--) {
                lines.remove(i - 1);
            }

            // Insérer le nouveau contenu
            String[] newLines = newContent.split("\\R"); // split par lignes
            lines.addAll(startLine - 1, List.of(newLines));

            // Réécrire le fichier
            Files.write(path, lines);

            return "Patch applied successfully to " + filePath;

        } catch (IOException e) {
            return "Error editing file: " + e.getMessage();
        }
    }
}
