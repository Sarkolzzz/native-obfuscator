package ru.sarkolsss.packager;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import ru.sarkolsss.bytecode.NativeMethodInfo;
import ru.sarkolsss.utils.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class NativeMethodModifier {
    private final Path workDir;
    private final List<NativeMethodInfo> nativeMethods;

    public NativeMethodModifier(Path workDir, List<NativeMethodInfo> nativeMethods) {
        this.workDir = workDir;
        this.nativeMethods = nativeMethods;
    }

    public void modifyClasses() {
        for (NativeMethodInfo method : nativeMethods) {
            String classFile = method.getClassName() + ".class";
            Path classPath = workDir.resolve(classFile);

            if (!Files.exists(classPath)) {
                continue;
            }

            try {
                byte[] bytecode = Files.readAllBytes(classPath);
                byte[] modified = makeMethodNative(bytecode, method);
                Files.write(classPath, modified);

                Logger.detail("Modified method: " + method.getSimpleName());

            } catch (IOException e) {
                Logger.error("Failed to modify class: " + classFile);
            }
        }
    }

    private byte[] makeMethodNative(byte[] bytecode, NativeMethodInfo method) {
        ClassReader reader = new ClassReader(bytecode);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);

        for (MethodNode methodNode : classNode.methods) {
            if (methodNode.name.equals(method.getMethodName()) &&
                    methodNode.desc.equals(method.getDescriptor())) {

                methodNode.access |= Opcodes.ACC_NATIVE;

                if (methodNode.instructions != null) {
                    methodNode.instructions.clear();
                }
                if (methodNode.tryCatchBlocks != null) {
                    methodNode.tryCatchBlocks.clear();
                }
                methodNode.localVariables = null;
                methodNode.maxStack = 0;
                methodNode.maxLocals = 0;
            }
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }
}