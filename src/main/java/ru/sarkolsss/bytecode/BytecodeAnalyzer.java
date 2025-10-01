package ru.sarkolsss.bytecode;

import org.objectweb.asm.ClassReader;
import ru.sarkolsss.utils.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class BytecodeAnalyzer {
    private final Path workDir;
    private final boolean skipAnnotationCheck;

    public BytecodeAnalyzer(Path workDir, boolean skipAnnotationCheck) {
        this.workDir = workDir;
        this.skipAnnotationCheck = skipAnnotationCheck;
    }

    public List<NativeMethodInfo> findNativeMethods(List<String> classFiles) {
        List<NativeMethodInfo> nativeMethods = new ArrayList<>();

        for (String classFile : classFiles) {
            Path classPath = workDir.resolve(classFile);

            try {
                byte[] bytecode = Files.readAllBytes(classPath);
                ClassReader reader = new ClassReader(bytecode);

                ClassVisitor visitor = new ClassVisitor(nativeMethods, skipAnnotationCheck);
                reader.accept(visitor, 0);

            } catch (IOException e) {
                Logger.error("Failed to read class: " + classFile);
            }
        }

        String mode = skipAnnotationCheck ? "all methods" : "@Native annotated methods";
        Logger.detail("Found " + nativeMethods.size() + " " + mode);
        return nativeMethods;
    }
}