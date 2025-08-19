package com.reglisseforge.tools.files;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reglisseforge.tools.base.Param;
import com.reglisseforge.tools.base.Tool;

public class FileReadTool {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Tool(name = "file_read", description = "Read only the src/main.leo file from a Leo project")
    public String read(
            @Param(name = "path", description = "Path to the file or the workspace directory", required = true) String path
    ) throws Exception {
        Path input = Paths.get(path).toAbsolutePath().normalize();
        Path p = Files.isDirectory(input) ? input.resolve("src/main.leo") : input;
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
        result.put("content", content);
        return JSON.writeValueAsString(result);
    }
}


