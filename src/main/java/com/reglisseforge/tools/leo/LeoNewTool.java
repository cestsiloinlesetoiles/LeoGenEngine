package com.reglisseforge.tools.leo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reglisseforge.tools.base.Param;
import com.reglisseforge.tools.base.Tool;
import com.reglisseforge.utils.CommandRunner;

public class LeoNewTool {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Tool(name = "leo_new", description = "Create a new Leo project using the 'leo new' CLI only")
    public String createProject(
            @Param(name = "project_name", description = "Leo project name", required = true) String projectName,
            @Param(name = "directory", description = "Directory where to create the project (default: .)", required = false, defaultValue = ".") String directory
    ) throws Exception {
        if (projectName == null || projectName.isBlank()) {
            throw new IllegalArgumentException("project_name is required");
        }

        Path baseDir = Paths.get(directory).toAbsolutePath().normalize();
        Files.createDirectories(baseDir);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", projectName);
        result.put("directory", baseDir.toString());

        CommandRunner.CommandResult check = CommandRunner.runBash("PATH=\"$PATH:/home/eden/.cargo/bin\" command -v leo >/dev/null 2>&1", baseDir.toFile());
        if (check.exitCode() != 0) {
            result.put("error", "leo_cli_not_found");
            result.put("success", false);
            return JSON.writeValueAsString(result);
        }

        String safeName = projectName.replace("\"", "\\\"");
        CommandRunner.CommandResult exec = CommandRunner.runBash("PATH=\"$PATH:/home/eden/.cargo/bin\" leo new \"" + safeName + "\"", baseDir.toFile());
        result.put("exit_code", exec.exitCode());
        result.put("log", exec.stdout());
        result.put("stderr", exec.stderr());
        result.put("success", exec.exitCode() == 0);
        result.put("status", exec.exitCode() == 0 ? "created_with_cli" : "cli_failed");
        return JSON.writeValueAsString(result);
    }
}


