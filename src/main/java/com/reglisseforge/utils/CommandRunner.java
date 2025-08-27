package com.reglisseforge.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.PumpStreamHandler;

public final class CommandRunner {

    private CommandRunner() {}

    public record CommandResult(int exitCode, String stdout, String stderr) {}

    public static CommandResult run(String command, File workingDir) {
        try {
            DefaultExecutor.Builder<?> builder = DefaultExecutor.builder();
            if (workingDir != null) {
                builder.setWorkingDirectory(workingDir);
            }
            DefaultExecutor executor = builder.get();
            executor.setExitValues(null); 

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            executor.setStreamHandler(new PumpStreamHandler(out, err));

            int code = executor.execute(CommandLine.parse(command));
            return new CommandResult(code, out.toString(), err.toString());
        } catch (ExecuteException ee) {
            return new CommandResult(ee.getExitValue(), "", ee.getMessage());
        } catch (Exception e) {
            return new CommandResult(-1, "", e.getMessage());
        }
    }

    public static CommandResult runBash(String bashCommand, File workingDir) {
        try {
            DefaultExecutor.Builder<?> builder = DefaultExecutor.builder();
            if (workingDir != null) {
                builder.setWorkingDirectory(workingDir);
            }
            DefaultExecutor executor = builder.get();
            executor.setExitValues(null); 

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            executor.setStreamHandler(new PumpStreamHandler(out, err));

            // Use CommandLine.addArgument to properly handle quotes
            CommandLine cmdLine = new CommandLine("bash");
            cmdLine.addArgument("-lc");
            cmdLine.addArgument(bashCommand, false); // false = don't handle quotes ourselves
            
            int code = executor.execute(cmdLine);
            return new CommandResult(code, out.toString(), err.toString());
        } catch (ExecuteException ee) {
            return new CommandResult(ee.getExitValue(), "", ee.getMessage());
        } catch (Exception e) {
            return new CommandResult(-1, "", e.getMessage());
        }
    }
}


