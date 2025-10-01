package ru.sarkolsss.compiler;

import ru.sarkolsss.utils.Logger;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class VisualStudioCompiler {
    private final Path workDir;
    private final Path cppSrcDir;
    private final Path buildDir;

    public VisualStudioCompiler(Path workDir) {
        this.workDir = workDir;
        this.cppSrcDir = workDir.resolve("cpp_src");
        this.buildDir = cppSrcDir.resolve("build");
    }

    public Path compile() {
        try {
            Files.createDirectories(buildDir);

            Logger.detail("Configuring CMake...");
            runCommand(buildDir, "cmake", "..", "-G", "Visual Studio 17 2022", "-A", "x64");

            Logger.detail("Building with MSBuild...");
            runCommand(buildDir, "cmake", "--build", ".", "--config", "Release");

            Path dllPath = buildDir.resolve("Release").resolve("java2cpp_native.dll");

            if (!Files.exists(dllPath)) {
                throw new RuntimeException("DLL not found after compilation");
            }

            Logger.detail("DLL compiled successfully: " + dllPath.getFileName());
            return dllPath;

        } catch (IOException | InterruptedException e) {
            Logger.error("Compilation failed: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void runCommand(Path directory, String... command)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(directory.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Logger.compile(line);
            }
        }

        boolean finished = process.waitFor(5, TimeUnit.MINUTES);

        if (!finished) {
            process.destroy();
            throw new RuntimeException("Compilation timeout");
        }

        if (process.exitValue() != 0) {
            throw new RuntimeException("Compilation failed with exit code: "
                    + process.exitValue());
        }
    }
}