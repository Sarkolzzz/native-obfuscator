package ru.sarkolsss.core;

import ru.sarkolsss.utils.Logger;
import java.io.IOException;
import java.nio.file.Path;

public class BuildAutomation {
    private final Path workDir;

    public BuildAutomation(Path workDir) {
        this.workDir = workDir;
    }

    public void loadNativeLibrary() {
        try {
            Path dllPath = workDir.resolve("native").resolve("java2cpp_native.dll");

            if (!java.nio.file.Files.exists(dllPath)) {
                extractEmbeddedLibrary(dllPath);
            }

            System.load(dllPath.toAbsolutePath().toString());
            Logger.detail("Native library loaded successfully");

        } catch (Exception e) {
            Logger.error("Failed to load native library: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void extractEmbeddedLibrary(Path targetPath) throws IOException {
        java.nio.file.Files.createDirectories(targetPath.getParent());

        try (var inputStream = getClass().getResourceAsStream("/native/java2cpp_native.dll")) {
            if (inputStream == null) {
                throw new IOException("Native library not found in JAR");
            }

            java.nio.file.Files.copy(inputStream, targetPath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}