package ru.sarkolsss;

import ru.sarkolsss.jar.JarProcessor;
import ru.sarkolsss.processor.impl.ClassProcessor;
import ru.sarkolsss.util.ClassContext;
import ru.sarkolsss.compiler.NativeCompiler;

import java.io.*;
import java.nio.file.*;

public class Main {
    public static void main(String[] args) {
        try {
            if (args.length < 2) {
                printUsage();
                System.exit(1);
            }

            String input = args[0];
            String outputDir = args[1];

            if (input.endsWith(".jar")) {
                processJarFile(input, outputDir);
            } else {
                processClass(input, outputDir);
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void processJarFile(String jarPath, String outputDir)
            throws Exception {
        File jarFile = new File(jarPath);
        if (!jarFile.exists()) {
            throw new FileNotFoundException("JAR not found: " + jarPath);
        }

        JarProcessor processor = new JarProcessor(jarPath, outputDir);
        processor.process();
    }

    private static void processClass(String className, String outputDir)
            throws Exception {
        System.out.println("=== Native Obfuscator ===");
        System.out.println("Target: " + className);
        System.out.println("Output: " + outputDir);

        Class<?> clazz = Class.forName(className);
        System.out.println("Loaded: " + clazz.getName());

        Files.createDirectories(Paths.get(outputDir));

        System.out.println("\n[1/3] Generating C++ code...");
        ClassContext ctx = new ClassContext();
        ctx.clazz = clazz;

        ClassProcessor processor = new ClassProcessor();
        processor.process(ctx);

        String cppFileName = outputDir + "/" + clazz.getSimpleName() + ".cpp";
        try (FileWriter writer = new FileWriter(cppFileName)) {
            writer.write(ctx.content.toString());
        }
        System.out.println("Generated: " + cppFileName);

        System.out.println("\n[2/3] Compiling native library...");
        NativeCompiler compiler = new NativeCompiler();
        String libPath = compiler.compileWithCMake(cppFileName, outputDir);
        System.out.println("Compiled: " + libPath);

        System.out.println("\n[3/3] Modifying bytecode...");
        ClassModifier modifier = new ClassModifier();
        modifier.modifyClass(clazz, libPath, outputDir);
        System.out.println("Modified class created");

        System.out.println("\n=== Complete ===");
        System.out.println("C++ source: " + cppFileName);
        System.out.println("Native library: " + libPath);
        System.out.println("Modified class: " + outputDir + "/" + clazz.getSimpleName() + ".class");
    }

    private static void printUsage() {
        System.out.println("Native Obfuscator - Java to Native Code Converter");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar native-obfuscator.jar <input> <output-directory>");
        System.out.println();
        System.out.println("Input:");
        System.out.println("  <class-name>     Fully qualified class name");
        System.out.println("  <jar-file>       Path to JAR file");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar native-obfuscator.jar com.example.Calculator ./output");
        System.out.println("  java -jar native-obfuscator.jar myapp.jar ./output");
    }
}