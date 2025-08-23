package com.reglisseforge.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class FileReaderTool {

    /**
     * Lit un fichier et retourne chaque ligne avec son numéro.
     *
     * @param filePath chemin du fichier à lire
     * @return contenu avec numéros de ligne
     */
    public static String readFileWithLineNumbers(String filePath) {
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
}
