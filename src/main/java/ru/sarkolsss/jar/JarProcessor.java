package ru.sarkolsss.jar;

import ru.sarkolsss.ClassModifier;
import ru.sarkolsss.compiler.NativeCompiler;
import ru.sarkolsss.processor.impl.ClassProcessor;
import ru.sarkolsss.util.ClassContext;
import ru.sarkolsss.util.Util;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.*;
import java.util.zip.*;

public class JarProcessor {

    private final String inputJarPath;
    private final String outputDir;
    private final Set<String> processedClasses = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, String> nativeLibraries = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    public JarProcessor(String inputJarPath, String outputDir) {
        this.inputJarPath = inputJarPath;
        this.outputDir = outputDir;
        this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    public void process() throws Exception {
        System.out.println("=== Java Native Obfuscator ===");
        System.out.println("Input: " + inputJarPath);
        System.out.println("Output: " + outputDir);
        System.out.println("CPU Cores: " + Runtime.getRuntime().availableProcessors());

        Files.createDirectories(Paths.get(outputDir));
        String tempDir = outputDir + "/temp";
        String nativeDir = outputDir + "/native";
        String classesDir = outputDir + "/classes";

        Files.createDirectories(Paths.get(tempDir));
        Files.createDirectories(Paths.get(nativeDir));
        Files.createDirectories(Paths.get(classesDir));

        System.out.println("\n[1/6] Extracting JAR...");
        extractJar(inputJarPath, tempDir);

        System.out.println("\n[2/6] Loading classes...");
        List<Class<?>> classes = loadClassesFromJar(inputJarPath, tempDir);
        System.out.println("Found " + classes.size() + " classes");

        System.out.println("\n[3/6] Adding NativeLoader...");
        addNativeLoader(classesDir);

        System.out.println("\n[4/6] Processing native classes...");
        List<Future<Boolean>> futures = new ArrayList<>();

        for (Class<?> clazz : classes) {
            if (Util.canProcess(clazz)) {
                futures.add(executor.submit(() -> {
                    processClass(clazz, nativeDir);
                    return true;
                }));
            }
        }

        int processedCount = 0;
        for (Future<Boolean> future : futures) {
            if (future.get()) processedCount++;
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);

        System.out.println("Processed " + processedCount + " classes");

        System.out.println("\n[5/6] Modifying bytecode...");
        for (Class<?> clazz : classes) {
            if (processedClasses.contains(clazz.getName())) {
                String libPath = nativeLibraries.get(clazz.getName());
                ClassModifier modifier = new ClassModifier();
                modifier.modifyClass(clazz, libPath, classesDir);
            } else {
                copyClassFile(tempDir, classesDir, clazz);
            }
        }

        System.out.println("\n[6/6] Creating output JAR...");
        String outputJarPath = outputDir + "/" + new File(inputJarPath).getName();
        createJar(classesDir, tempDir, outputJarPath, nativeDir);

        System.out.println("\n=== Complete ===");
        System.out.println("Output JAR: " + outputJarPath);
        System.out.println("Native libraries: " + nativeDir);
        System.out.println("Modified classes: " + processedCount);

        deleteDirectory(new File(tempDir));
        deleteDirectory(new File(classesDir));
    }

    private void addNativeLoader(String classesDir) throws IOException, InterruptedException {
        String loaderSource =
                "package ru.sarkolsss;\n\n" +
                        "import java.io.*;\n" +
                        "import java.nio.file.*;\n\n" +
                        "public class NativeLoader {\n" +
                        "    private static boolean loaded = false;\n\n" +
                        "    public static synchronized void loadLibrary(String resourcePath, String className) {\n" +
                        "        if (loaded) return;\n" +
                        "        try {\n" +
                        "            String libName = new File(resourcePath).getName();\n" +
                        "            String tempDir = System.getProperty(\"java.io.tmpdir\");\n" +
                        "            Path tempLib = Paths.get(tempDir, \"native_\" + System.currentTimeMillis() + \"_\" + libName);\n" +
                        "            try (InputStream is = NativeLoader.class.getResourceAsStream(resourcePath)) {\n" +
                        "                if (is == null) throw new RuntimeException(\"Native library not found: \" + resourcePath);\n" +
                        "                Files.copy(is, tempLib, StandardCopyOption.REPLACE_EXISTING);\n" +
                        "            }\n" +
                        "            tempLib.toFile().deleteOnExit();\n" +
                        "            System.load(tempLib.toAbsolutePath().toString());\n" +
                        "            loaded = true;\n" +
                        "        } catch (Exception e) {\n" +
                        "            throw new RuntimeException(\"Failed to load: \" + resourcePath, e);\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n";

        File tempJavaFile = new File(classesDir, "NativeLoader.java");
        tempJavaFile.getParentFile().mkdirs();

        try (FileWriter writer = new FileWriter(tempJavaFile)) {
            writer.write(loaderSource);
        }

        ProcessBuilder pb = new ProcessBuilder(
                "javac",
                "-d", classesDir,
                tempJavaFile.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }

        if (process.waitFor() != 0) {
            throw new IOException("Failed to compile NativeLoader");
        }

        tempJavaFile.delete();

        Path loaderClassPath = Paths.get(classesDir, "ru", "sarkolsss", "NativeLoader.class");
        if (loaderClassPath.toFile().exists()) {
            System.out.println("NativeLoader added successfully");
        }
    }

    private void extractJar(String jarPath, String destDir) throws IOException {
        try (JarFile jarFile = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                File entryFile = new File(destDir, entry.getName());

                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                } else {
                    entryFile.getParentFile().mkdirs();

                    try (InputStream is = jarFile.getInputStream(entry);
                         FileOutputStream fos = new FileOutputStream(entryFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                }
            }
        }
    }

    private List<Class<?>> loadClassesFromJar(String jarPath, String tempDir) throws Exception {
        List<Class<?>> classes = new ArrayList<>();

        URL jarUrl = new File(jarPath).toURI().toURL();
        URLClassLoader classLoader = new URLClassLoader(
                new URL[]{jarUrl},
                Thread.currentThread().getContextClassLoader()
        );

        List<String> classNames = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(jarPath))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    String className = entry.getName()
                            .replace('/', '.')
                            .replace(".class", "");

                    if (!className.endsWith("module-info") &&
                            !className.endsWith("package-info")) {
                        classNames.add(className);
                    }
                }
            }
        }

        for (String className : classNames) {
            try {
                Class<?> clazz = classLoader.loadClass(className);
                classes.add(clazz);
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                System.err.println("Warning: " + className + " - " + e.getMessage());
            }
        }

        return classes;
    }

    private void processClass(Class<?> clazz, String nativeDir) throws Exception {
        System.out.println("Processing: " + clazz.getName());

        ClassContext ctx = new ClassContext();
        ctx.clazz = clazz;

        ClassProcessor processor = new ClassProcessor();
        processor.process(ctx);

        if (ctx.content.length() == 0) {
            return;
        }

        String cppFileName = nativeDir + "/" + clazz.getSimpleName() + ".cpp";
        try (FileWriter writer = new FileWriter(cppFileName)) {
            writer.write(ctx.content.toString());
        }

        NativeCompiler compiler = new NativeCompiler();
        String libPath = compiler.compileWithCMake(cppFileName, nativeDir);

        nativeLibraries.put(clazz.getName(), libPath);
        processedClasses.add(clazz.getName());

        System.out.println("  Generated: " + libPath);
    }

    private void copyClassFile(String sourceDir, String destDir, Class<?> clazz) throws IOException {
        String classPath = clazz.getName().replace('.', '/') + ".class";
        File sourceFile = new File(sourceDir, classPath);
        File destFile = new File(destDir, classPath);

        if (sourceFile.exists()) {
            destFile.getParentFile().mkdirs();
            Files.copy(sourceFile.toPath(), destFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void createJar(String classesDir, String tempDir, String outputJarPath,
                           String nativeDir) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputJarPath);
             JarOutputStream jos = new JarOutputStream(fos)) {

            Manifest manifest = readManifest(tempDir);
            if (manifest != null) {
                JarEntry manifestEntry = new JarEntry(JarFile.MANIFEST_NAME);
                jos.putNextEntry(manifestEntry);
                manifest.write(jos);
                jos.closeEntry();
            }

            addDirectoryToJar(new File(classesDir), classesDir, jos);
            addNativeLibrariesToJar(nativeDir, jos);
            addResourcesFromTemp(tempDir, jos, classesDir);
        }
    }

    private Manifest readManifest(String tempDir) throws IOException {
        File manifestFile = new File(tempDir, "META-INF/MANIFEST.MF");
        if (manifestFile.exists()) {
            try (FileInputStream fis = new FileInputStream(manifestFile)) {
                return new Manifest(fis);
            }
        }
        return null;
    }

    private void addDirectoryToJar(File directory, String baseDir, JarOutputStream jos)
            throws IOException {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                addDirectoryToJar(file, baseDir, jos);
            } else {
                String entryName = file.getPath()
                        .substring(baseDir.length() + 1)
                        .replace('\\', '/');

                JarEntry entry = new JarEntry(entryName);
                jos.putNextEntry(entry);

                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        jos.write(buffer, 0, bytesRead);
                    }
                }

                jos.closeEntry();
            }
        }
    }

    private void addNativeLibrariesToJar(String nativeDir, JarOutputStream jos) throws IOException {
        File nativeDirFile = new File(nativeDir);

        System.out.println("Looking for native libraries in: " + nativeDirFile.getAbsolutePath());
        System.out.println("Directory exists: " + nativeDirFile.exists());

        if (!nativeDirFile.exists()) {
            System.out.println("Native directory not found, skipping native libraries");
            return;
        }

        File[] allFiles = nativeDirFile.listFiles();
        if (allFiles != null) {
            System.out.println("Files in native directory:");
            for (File f : allFiles) {
                System.out.println("  - " + f.getName() + " (dir: " + f.isDirectory() + ")");
            }
        }

        File[] nativeFiles = nativeDirFile.listFiles((dir, name) -> {
            String lowerName = name.toLowerCase();
            return lowerName.endsWith(".dll") ||
                    lowerName.endsWith(".so") ||
                    lowerName.endsWith(".dylib");
        });

        if (nativeFiles == null || nativeFiles.length == 0) {
            System.out.println("No native library files found");

            File buildSubDir = new File(nativeDirFile, "build");
            if (buildSubDir.exists()) {
                System.out.println("Checking build subdirectory...");
                nativeFiles = findNativeLibrariesRecursive(buildSubDir);
            }

            if (nativeFiles == null || nativeFiles.length == 0) {
                System.out.println("Searching recursively in: " + nativeDir);
                nativeFiles = findNativeLibrariesRecursive(nativeDirFile);
            }
        }

        if (nativeFiles == null || nativeFiles.length == 0) {
            System.out.println("WARNING: No native libraries found to add to JAR");
            return;
        }

        for (File nativeFile : nativeFiles) {
            String entryName = "native/" + nativeFile.getName();
            System.out.println("Adding to JAR: " + entryName + " from " + nativeFile.getAbsolutePath());

            JarEntry entry = new JarEntry(entryName);
            entry.setTime(nativeFile.lastModified());
            jos.putNextEntry(entry);

            try (FileInputStream fis = new FileInputStream(nativeFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    jos.write(buffer, 0, bytesRead);
                }
            }

            jos.closeEntry();
            System.out.println("Successfully added: " + entryName);
        }
    }

    private File[] findNativeLibrariesRecursive(File dir) {
        List<File> libraries = new ArrayList<>();
        findNativeLibrariesRecursive(dir, libraries);
        return libraries.toArray(new File[0]);
    }

    private void findNativeLibrariesRecursive(File dir, List<File> libraries) {
        if (!dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                findNativeLibrariesRecursive(file, libraries);
            } else {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib")) {
                    System.out.println("Found native library: " + file.getAbsolutePath());
                    libraries.add(file);
                }
            }
        }
    }

    private void addResourcesFromTemp(String tempDir, JarOutputStream jos,
                                      String classesDir) throws IOException {
        File tempDirFile = new File(tempDir);
        addResourcesRecursive(tempDirFile, tempDir, jos, classesDir);
    }

    private void addResourcesRecursive(File file, String baseDir,
                                       JarOutputStream jos, String classesDir)
            throws IOException {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    addResourcesRecursive(f, baseDir, jos, classesDir);
                }
            }
        } else {
            String relativePath = file.getPath()
                    .substring(baseDir.length() + 1)
                    .replace('\\', '/');

            if (relativePath.endsWith(".class") ||
                    relativePath.equals("META-INF/MANIFEST.MF")) {
                return;
            }

            try {
                JarEntry entry = new JarEntry(relativePath);
                jos.putNextEntry(entry);

                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        jos.write(buffer, 0, bytesRead);
                    }
                }

                jos.closeEntry();
            } catch (ZipException e) {
            }
        }
    }

    private void deleteDirectory(File directory) throws IOException {
        if (directory.exists()) {
            Files.walk(directory.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }
}