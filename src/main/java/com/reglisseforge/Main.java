package com.reglisseforge;

import com.reglisseforge.tools.LeoCodeEngine;

public class Main {

    public static void main(String[] args) {
        LeoCodeEngine engine = new LeoCodeEngine();
        
        // Initialize project and generate code
        engine.initProject("simple_dex", "Simple dex protocol");
        
        // Build and fix if necessary
        engine.buildAndFix();
    }


}
