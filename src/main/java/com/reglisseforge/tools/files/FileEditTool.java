package com.reglisseforge.tools.files;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reglisseforge.tools.base.Param;
import com.reglisseforge.tools.base.Tool;

public class FileEditTool {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Tool(name = "file_edit", description = "Edit only src/main.leo (replace the first match)")
    public String edit(
            @Param(name = "path", description = "Path to the file or the workspace directory", required = true) String path,
            @Param(name = "search", description = "Text (or regex) to look for", required = true) String search,
            @Param(name = "replace", description = "Replacement text", required = true) String replace,
            @Param(name = "regex", description = "Enable regex search (true/false)", required = false, defaultValue = "false") String regex
    ) throws Exception {
        Path input = Paths.get(path).toAbsolutePath().normalize();
        Path p = java.nio.file.Files.isDirectory(input) ? input.resolve("src/main.leo") : input;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", p.toString());
        if (!p.getFileName().toString().equals("main.leo")) {
            result.put("error", "only_main_leo_allowed");
            return JSON.writeValueAsString(result);
        }

        if (!Files.exists(p)) {
            result.put("error", "file_not_found");
            return JSON.writeValueAsString(result);
        }

        String content = Files.readString(p, StandardCharsets.UTF_8);
        String newContent;
        boolean useRegex = Boolean.parseBoolean(regex == null ? "false" : regex);

        if (useRegex) {
            newContent = content.replaceFirst(search, replace);
        } else {
            int idx = content.indexOf(search);
            if (idx < 0) {
                result.put("error", "pattern_not_found");
                return JSON.writeValueAsString(result);
            }
            newContent = content.substring(0, idx) + replace + content.substring(idx + search.length());
        }

        if (!newContent.equals(content)) {
            Files.writeString(p, newContent, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        }

        result.put("changed", !newContent.equals(content));
        return JSON.writeValueAsString(result);
    }
}


