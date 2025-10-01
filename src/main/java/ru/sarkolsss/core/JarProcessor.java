package ru.sarkolsss.core;

import ru.sarkolsss.utils.FileUtils;
import ru.sarkolsss.utils.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class JarProcessor {
    private final Path jarPath;
    private final Path extractDir;

    public JarProcessor(Path jarPath, Path extractDir) {
        this.jarPath = jarPath;
        this.extractDir = extractDir;
    }

    public List<String> extractAndAnalyze() {
        List<String> classFiles = new ArrayList<>();

        try {
            Files.createDirectories(extractDir);

            try (JarFile jar = new JarFile(jarPath.toFile())) {
                var entries = jar.entries();

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    Path entryPath = extractDir.resolve(entry.getName());

                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        Files.createDirectories(entryPath.getParent());
                        Files.copy(jar.getInputStream(entry), entryPath);

                        if (entry.getName().endsWith(".class")) {
                            classFiles.add(entry.getName());
                        }
                    }
                }
            }

            Logger.detail("Extracted " + classFiles.size() + " class files");
        } catch (IOException e) {
            Logger.error("Failed to process JAR: " + e.getMessage());
            throw new RuntimeException(e);
        }

        return classFiles;
    }

    public void cleanup() {
        FileUtils.deleteDirectory(extractDir);
    }
}