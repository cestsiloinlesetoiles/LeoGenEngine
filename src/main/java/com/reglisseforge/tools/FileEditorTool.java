package com.reglisseforge.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.reglisseforge.tools.base.Param;
import com.reglisseforge.tools.base.Tool;

public class FileEditorTool {

    /**
     * Replaces a range of lines in a file with given content.
     *
     * @param filePath   file path
     * @param startLine  first line to replace (1-based, inclusive)
     * @param endLine    last line to replace (1-based, inclusive)
     * @param newContent content to insert (multiple lines separated by \n)
     * @return success or error message
     */
    @Tool(name = "edit_file", description = "Replaces a range of lines (1-based inclusive) with new content")
    public static String editFile(
            @Param(name = "filePath", description = "Path of file to modify") String filePath,
            @Param(name = "startLine", description = "Start line (1-based)") int startLine,
            @Param(name = "endLine", description = "End line (1-based)") int endLine,
            @Param(name = "newContent", description = "New content to insert") String newContent) {
        try {
            Path path = Path.of(filePath);

            if (!Files.exists(path)) {
                return "Error: File does not exist -> " + filePath;
            }

            List<String> lines = new ArrayList<>(Files.readAllLines(path));

            if (startLine < 1 || endLine > lines.size() || startLine > endLine) {
                return "Error: Invalid line range " + startLine + "-" + endLine;
            }

            // Remove the range
            for (int i = endLine; i >= startLine; i--) {
                lines.remove(i - 1);
            }

            // Insert the new content
            String[] newLines = newContent.split("\\R"); // split by lines
            lines.addAll(startLine - 1, List.of(newLines));

            // Rewrite the file
            Files.write(path, lines);

            return "Patch applied successfully to " + filePath;

        } catch (IOException e) {
            return "Error editing file: " + e.getMessage();
        }
    }
}
