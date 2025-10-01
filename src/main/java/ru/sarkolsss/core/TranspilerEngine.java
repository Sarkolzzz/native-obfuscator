package ru.sarkolsss.core;

import ru.sarkolsss.bytecode.BytecodeAnalyzer;
import ru.sarkolsss.codegen.CppGenerator;
import ru.sarkolsss.compiler.CMakeGenerator;
import ru.sarkolsss.compiler.VisualStudioCompiler;
import ru.sarkolsss.packager.JarRepackager;
import ru.sarkolsss.utils.Logger;
import ru.sarkolsss.utils.FileUtils;
import java.nio.file.Path;
import java.util.List;

public class TranspilerEngine {
    private final Path inputJar;
    private final Path outputJar;
    private final Path workDir;
    private final boolean skipAnnotationCheck;

    static {
        System.setProperty("java.awt.headless", "true");
    }

    public TranspilerEngine(Path inputJar, Path outputJar, boolean skipAnnotationCheck) {
        this.inputJar = inputJar;
        this.outputJar = outputJar;
        this.workDir = Path.of("transpiler_temp_" + System.currentTimeMillis());
        this.skipAnnotationCheck = skipAnnotationCheck;
    }

    public void execute() {
        try {
            Logger.info("Starting transpilation process...");

            if (skipAnnotationCheck) {
                Logger.warning("Annotation check disabled - processing ALL methods");
            }

            JarProcessor jarProcessor = new JarProcessor(inputJar, workDir);
            Logger.step("Processing JAR file...");
            List<String> classes = jarProcessor.extractAndAnalyze();

            if (classes.isEmpty()) {
                Logger.error("No class files found in JAR");
                return;
            }

            BytecodeAnalyzer analyzer = new BytecodeAnalyzer(workDir, skipAnnotationCheck);
            Logger.step("Analyzing bytecode...");
            var nativeMethods = analyzer.findNativeMethods(classes);

            if (nativeMethods.isEmpty()) {
                Logger.info("No methods found for transpilation, copying original JAR...");
                java.nio.file.Files.copy(inputJar, outputJar,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return;
            }

            CppGenerator cppGen = new CppGenerator(workDir);
            Logger.step("Generating C++ code...");
            cppGen.generate(nativeMethods);

            CMakeGenerator cmakeGen = new CMakeGenerator(workDir);
            Logger.step("Generating CMake configuration...");
            cmakeGen.generate();

            VisualStudioCompiler compiler = new VisualStudioCompiler(workDir);
            Logger.step("Compiling with Visual Studio 2022...");
            Path dllPath = compiler.compile();

            JarRepackager repackager = new JarRepackager(inputJar, outputJar, workDir);
            Logger.step("Repackaging JAR with native library...");
            repackager.repackage(dllPath, nativeMethods);

        } catch (Exception e) {
            Logger.error("Transpilation failed: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            Logger.cleanup("Cleaning up temporary files...");
            FileUtils.deleteDirectory(workDir);
        }
    }
}