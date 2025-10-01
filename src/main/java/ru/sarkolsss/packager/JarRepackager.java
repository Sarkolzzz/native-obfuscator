package ru.sarkolsss.packager;

import ru.sarkolsss.bytecode.NativeMethodInfo;
import ru.sarkolsss.utils.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class JarRepackager {
    private final Path inputJar;
    private final Path outputJar;
    private final Path workDir;

    public JarRepackager(Path inputJar, Path outputJar, Path workDir) {
        this.inputJar = inputJar;
        this.outputJar = outputJar;
        this.workDir = workDir;
    }

    public void repackage(Path dllPath, List<NativeMethodInfo> nativeMethods) {
        try {
            NativeMethodModifier modifier = new NativeMethodModifier(workDir, nativeMethods);
            modifier.modifyClasses();

            Set<String> classesWithNativeMethods = new HashSet<>();
            for (NativeMethodInfo method : nativeMethods) {
                classesWithNativeMethods.add(method.getClassName() + ".class");
            }

            byte[] loaderClass = NativeLoaderGenerator.generateLoaderClass();

            try (JarFile jar = new JarFile(inputJar.toFile());
                 JarOutputStream jos = new JarOutputStream(Files.newOutputStream(outputJar))) {

                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();

                    if (entry.getName().endsWith(".class")) {
                        Path modifiedClass = workDir.resolve(entry.getName());

                        if (Files.exists(modifiedClass)) {
                            byte[] classBytes = Files.readAllBytes(modifiedClass);

                            if (classesWithNativeMethods.contains(entry.getName())) {
                                String className = entry.getName()
                                        .replace(".class", "")
                                        .replace('/', '.');
                                classBytes = StaticInitializerInjector.injectStaticInitializer(
                                        classBytes, className
                                );
                                Logger.detail("Injected static initializer: " + entry.getName());
                            }

                            jos.putNextEntry(new JarEntry(entry.getName()));
                            jos.write(classBytes);
                        } else {
                            jos.putNextEntry(entry);
                            jar.getInputStream(entry).transferTo(jos);
                        }
                    } else {
                        jos.putNextEntry(entry);
                        jar.getInputStream(entry).transferTo(jos);
                    }

                    jos.closeEntry();
                }

                JarEntry loaderEntry = new JarEntry("ru/sarkolsss/NativeLoader.class");
                jos.putNextEntry(loaderEntry);
                jos.write(loaderClass);
                jos.closeEntry();
                Logger.detail("Added NativeLoader class");

                JarEntry dllEntry = new JarEntry("native/java2cpp_native.dll");
                jos.putNextEntry(dllEntry);
                Files.copy(dllPath, jos);
                jos.closeEntry();

                Logger.detail("Native library embedded into JAR");
            }

        } catch (IOException e) {
            Logger.error("Failed to repackage JAR: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}