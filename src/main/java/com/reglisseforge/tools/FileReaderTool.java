package com.reglisseforge.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.reglisseforge.tools.base.Param;
import com.reglisseforge.tools.base.Tool;

public class FileReaderTool {

    /**
     * Lit un fichier et retourne chaque ligne avec son numéro.
     *
     * @param filePath chemin du fichier à lire
     * @return contenu avec numéros de ligne
     */
    @Tool(name = "read_file_with_line_numbers", description = "Lit un fichier entier et retourne chaque ligne précédée de son numéro (1-based)")
    public static String readFileWithLineNumbers(
            @Param(name = "filePath", description = "Chemin absolu ou relatif du fichier à lire") String filePath) {
        try {
            Path path = Path.of(filePath);

            if (!Files.exists(path)) {
                return "Error: File does not exist -> " + filePath;
            }

            List<String> lines = Files.readAllLines(path);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                sb.append(i + 1) // numéro de ligne (1-based)
                  .append(": ")
                  .append(lines.get(i))
                  .append("\n");
            }

            return sb.toString();

        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    /**
     * Lit un fichier de la ligne startLine à endLine (incluses).
     *
     * @param filePath chemin du fichier à lire
     * @param startLine numéro de ligne de début (1-based)
     * @param endLine numéro de ligne de fin (1-based)
     * @return contenu des lignes spécifiées avec numéros de ligne
     */
    @Tool(name = "read_file_lines", description = "Lit une plage de lignes (inclusives, 1-based) et retourne les lignes numérotées")
    public static String readFileLines(
            @Param(name = "filePath", description = "Chemin absolu ou relatif du fichier à lire") String filePath,
            @Param(name = "startLine", description = "Numéro de ligne de début (1-based)") int startLine,
            @Param(name = "endLine", description = "Numéro de ligne de fin (1-based)") int endLine) {
        try {
            Path path = Path.of(filePath);

            if (!Files.exists(path)) {
                return "Error: File does not exist -> " + filePath;
            }

            if (startLine < 1 || endLine < 1) {
                return "Error: Line numbers must be >= 1";
            }

            if (startLine > endLine) {
                return "Error: Start line must be <= end line";
            }

            List<String> lines = Files.readAllLines(path);

            if (startLine > lines.size()) {
                return "Error: Start line " + startLine + " exceeds file length (" + lines.size() + " lines)";
            }

            // Ajuster endLine si elle dépasse la taille du fichier
            int actualEndLine = Math.min(endLine, lines.size());

            StringBuilder sb = new StringBuilder();
            for (int i = startLine - 1; i < actualEndLine; i++) {
                sb.append(i + 1) // numéro de ligne (1-based)
                  .append(": ")
                  .append(lines.get(i))
                  .append("\n");
            }

            return sb.toString();

        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }
}
