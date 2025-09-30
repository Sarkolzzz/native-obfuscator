package ru.sarkolsss;

import java.io.*;
import java.nio.file.*;

public class NativeLoader {
    private static boolean loaded = false;

    public static synchronized void loadLibrary(String resourcePath, String className) {
        if (loaded) return;

        try {
            String libName = new File(resourcePath).getName();
            String tempDir = System.getProperty("java.io.tmpdir");
            Path tempLib = Paths.get(tempDir, "native_" + System.currentTimeMillis() + "_" + libName);

            try (InputStream is = NativeLoader.class.getResourceAsStream(resourcePath)) {
                if (is == null) {
                    throw new RuntimeException("Native library not found in resources: " + resourcePath);
                }

                Files.copy(is, tempLib, StandardCopyOption.REPLACE_EXISTING);
            }

            tempLib.toFile().deleteOnExit();
            System.load(tempLib.toAbsolutePath().toString());
            loaded = true;

            System.out.println("Loaded native library: " + resourcePath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load native library: " + resourcePath, e);
        }
    }
}