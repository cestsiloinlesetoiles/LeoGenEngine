package com.reglisseforge;

import com.reglisseforge.tools.leo.LeoCodeEngine;

public class Main {
	public static void main(String[] args) throws Exception {
		LeoCodeEngine engine = new LeoCodeEngine();

		String projectName = args.length > 0 ? args[0] : "simple_dex";
		String directory = args.length > 1 ? args[1] : ".";
		String description = args.length > 2 ? args[2]
				: "A basic DEX for token swapping with liquidity pools and trading functionality";

		System.out.println("=== Test génération itérative complète ===");
		System.out.println("projectName: " + projectName);
		System.out.println("directory: " + directory);

		// 1. Créer le projet initial
		System.out.println("\n=== Étape 1: Création initiale ===");
		String initResult = engine.init(projectName, directory, description);
		System.out.println(initResult);

		// 2. Continuer la génération si nécessaire
		System.out.println("\n=== Étape 2: Continuation ===");
		String continueResult = engine.continueGeneration(projectName, directory);
		System.out.println(continueResult);
	}
}
