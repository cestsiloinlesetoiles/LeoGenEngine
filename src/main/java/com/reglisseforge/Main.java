package com.reglisseforge;

import com.reglisseforge.tools.FileEditorTool;
import com.reglisseforge.tools.FileReaderTool;
import com.reglisseforge.tools.LeoCodeEngine;
import com.reglisseforge.tools.WorkspaceScannerTool;

public class Main {
	public static void main(String[] args) throws Exception {
		String path = LeoCodeEngine.generatedProject("simple");

		String content = FileReaderTool.readFileWithLineNumbers(path + "/src/main.leo");
		System.out.println(content);
		FileEditorTool.editFile(path + "/src/main.leo", 1, 1, "print('Hello, World!')");
		content = FileReaderTool.readFileWithLineNumbers(path + "/src/main.leo");
		System.out.println(content);

		System.out.println(WorkspaceScannerTool.listProjects());
	}


}
