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

public class LeoBuildTool {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Tool(name = "leo_build", description = "Build a Leo project in the given workspace (uses 'leo build' if available, otherwise validates structure)")
    public String build(
            @Param(name = "workspace", description = "Path to the Leo project (default: .)", required = false, defaultValue = ".") String workspace
    ) throws Exception {
        Path workDir = Paths.get(workspace).toAbsolutePath().normalize();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workspace", workDir.toString());

        CommandRunner.CommandResult check = CommandRunner.runBash("PATH=\"$PATH:/home/eden/.cargo/bin\" command -v leo >/dev/null 2>&1", workDir.toFile());
        result.put("used_cli", check.exitCode() == 0);

        if (check.exitCode() == 0) {
            CommandRunner.CommandResult out = CommandRunner.runBash("PATH=\"$PATH:/home/eden/.cargo/bin\" leo build", workDir.toFile());
            result.put("exit_code", out.exitCode());
            result.put("log", out.stdout());
            result.put("stderr", out.stderr());
            result.put("success", out.exitCode() == 0);
            return JSON.writeValueAsString(result);
        }

        // Fallback: minimal structure validation
        boolean hasProgram = Files.exists(workDir.resolve("program.json"));
        boolean hasMain = Files.exists(workDir.resolve("src/main.leo"));
        result.put("structure_ok", hasProgram && hasMain);
        result.put("success", hasProgram && hasMain);
        result.put("log", (hasProgram && hasMain) ? "Minimal structure detected (CLI not available)" : "Invalid structure: program.json or src/main.leo missing");
        return JSON.writeValueAsString(result);
    }
}


