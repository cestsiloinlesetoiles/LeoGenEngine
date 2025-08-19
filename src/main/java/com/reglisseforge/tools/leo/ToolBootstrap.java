package com.reglisseforge.tools.leo;

import com.reglisseforge.tools.base.ToolRegistry;
import com.reglisseforge.tools.files.FileEditTool;
import com.reglisseforge.tools.files.FileReadTool;

public final class ToolBootstrap {

    private ToolBootstrap() {}

    public static void registerDefaultTools(ToolRegistry registry) {
        registry.registerTool(new LeoNewTool());
        registry.registerTool(new LeoBuildTool());
        registry.registerTool(new FileReadTool());
        registry.registerTool(new FileEditTool());
    }
}


