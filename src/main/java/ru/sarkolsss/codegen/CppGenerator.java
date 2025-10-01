package ru.sarkolsss.codegen;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import ru.sarkolsss.bytecode.MethodBodyAnalyzer;
import ru.sarkolsss.bytecode.MethodBodyInfo;
import ru.sarkolsss.bytecode.NativeMethodInfo;
import ru.sarkolsss.utils.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CppGenerator {
    private final Path workDir;
    private final TypeMapper typeMapper;
    private final JniHeaderGenerator headerGen;
    private final BytecodeTranslator translator;

    public CppGenerator(Path workDir) {
        this.workDir = workDir;
        this.typeMapper = new TypeMapper();
        this.headerGen = new JniHeaderGenerator();
        this.translator = new BytecodeTranslator(typeMapper);
    }

    public void generate(List<NativeMethodInfo> methods) {
        Path cppDir = workDir.resolve("cpp_src");

        try {
            Files.createDirectories(cppDir);

            generateHeader(cppDir, methods);
            generateImplementation(cppDir, methods);

            Logger.detail("Generated C++ code for " + methods.size() + " methods");
        } catch (IOException e) {
            Logger.error("Failed to generate C++ code: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void generateHeader(Path dir, List<NativeMethodInfo> methods)
            throws IOException {
        StringBuilder header = new StringBuilder();

        header.append("#ifndef JAVA2CPP_NATIVE_H\n");
        header.append("#define JAVA2CPP_NATIVE_H\n\n");
        header.append("#include <jni.h>\n");
        header.append("#include <string>\n");
        header.append("#include <vector>\n");
        header.append("#include <cmath>\n\n");
        header.append("extern \"C\" {\n\n");

        for (NativeMethodInfo method : methods) {
            String signature = headerGen.generateSignature(method);
            header.append(signature).append(";\n\n");
        }

        header.append("}\n\n");
        header.append("#endif\n");

        Files.writeString(dir.resolve("native.h"), header.toString());
    }

    private void generateImplementation(Path dir, List<NativeMethodInfo> methods)
            throws IOException {
        StringBuilder impl = new StringBuilder();

        impl.append("#include \"native.h\"\n");
        impl.append("#include <iostream>\n");
        impl.append("#include <cstring>\n\n");

        for (NativeMethodInfo method : methods) {
            MethodBodyInfo bodyInfo = extractMethodBody(method);
            impl.append(generateMethodImplementation(method, bodyInfo)).append("\n\n");
        }

        Files.writeString(dir.resolve("native.cpp"), impl.toString());
    }

    private MethodBodyInfo extractMethodBody(NativeMethodInfo method) {
        Path classPath = workDir.resolve(method.getClassName() + ".class");

        try {
            byte[] bytecode = Files.readAllBytes(classPath);
            ClassReader reader = new ClassReader(bytecode);
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, ClassReader.EXPAND_FRAMES);

            for (MethodNode methodNode : classNode.methods) {
                if (methodNode.name.equals(method.getMethodName()) &&
                        methodNode.desc.equals(method.getDescriptor())) {
                    return MethodBodyAnalyzer.analyzeMethod(methodNode, method.getClassName());
                }
            }
        } catch (IOException e) {
            Logger.error("Failed to read method body: " + method.getSimpleName());
        }

        return null;
    }

    private String generateMethodImplementation(NativeMethodInfo method, MethodBodyInfo bodyInfo) {
        StringBuilder impl = new StringBuilder();

        String signature = headerGen.generateSignature(method);
        impl.append(signature).append(" {\n");

        if (bodyInfo != null && bodyInfo.instructions != null && !bodyInfo.instructions.isEmpty()) {
            impl.append(translator.translateMethodBody(bodyInfo, method));
        } else {
            impl.append("    ");
            String returnType = typeMapper.getReturnType(method.getDescriptor());
            if (!returnType.equals("void")) {
                impl.append("return ").append(typeMapper.getDefaultValue(returnType)).append(";\n");
            }
        }

        impl.append("}");

        return impl.toString();
    }
}