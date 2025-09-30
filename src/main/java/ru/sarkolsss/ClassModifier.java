package ru.sarkolsss;

import org.objectweb.asm.*;
import ru.sarkolsss.util.Util;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;

public class ClassModifier {

    public void modifyClass(Class<?> clazz, String libName, String outputDir) throws Exception {
        String className = clazz.getName().replace('.', '/');
        InputStream is = clazz.getClassLoader().getResourceAsStream(className + ".class");

        if (is == null) {
            throw new Exception("Cannot find class file for: " + clazz.getName());
        }

        ClassReader cr = new ClassReader(is);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        Set<String> nativeMethodSignatures = new HashSet<>();
        for (Method m : clazz.getDeclaredMethods()) {
            if (Util.canProcess(m)) {
                String signature = m.getName() + Type.getMethodDescriptor(
                        Type.getType(m.getReturnType()),
                        Arrays.stream(m.getParameterTypes())
                                .map(Type::getType)
                                .toArray(Type[]::new)
                );
                nativeMethodSignatures.add(signature);
            }
        }

        ClassVisitor cv = new NativeConversionVisitor(Opcodes.ASM9, cw, nativeMethodSignatures, libName);
        cr.accept(cv, ClassReader.EXPAND_FRAMES);

        byte[] modifiedClass = cw.toByteArray();

        String packagePath = "";
        if (clazz.getPackage() != null) {
            packagePath = clazz.getPackage().getName().replace('.', '/');
        }

        Path outputPath = Paths.get(outputDir, packagePath);
        Files.createDirectories(outputPath);

        String outputFilePath = Paths.get(outputDir, packagePath, clazz.getSimpleName() + ".class").toString();
        try (FileOutputStream fos = new FileOutputStream(outputFilePath)) {
            fos.write(modifiedClass);
        }

        System.out.println("Modified class written to: " + outputFilePath);
    }

    private static class NativeConversionVisitor extends ClassVisitor {
        private final Set<String> nativeMethodSignatures;
        private final String libName;
        private boolean hasStaticBlock = false;
        private String className;

        public NativeConversionVisitor(int api, ClassVisitor cv, Set<String> nativeMethodSignatures, String libName) {
            super(api, cv);
            this.nativeMethodSignatures = nativeMethodSignatures;
            this.libName = libName;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            String methodSignature = name + descriptor;

            if (nativeMethodSignatures.contains(methodSignature)) {
                int newAccess = access | Opcodes.ACC_NATIVE;
                newAccess &= ~Opcodes.ACC_ABSTRACT;
                newAccess &= ~Opcodes.ACC_SYNCHRONIZED;

                System.out.println("Converting to native: " + name + descriptor);

                // НЕ ВОЗВРАЩАЕМ MethodVisitor - ЭТО КЛЮЧЕВОЕ ИЗМЕНЕНИЕ
                // Метод будет создан без тела (Code attribute)
                super.visitMethod(newAccess, name, descriptor, signature, exceptions);
                return null; // ← ВАЖНО: null означает "не генерировать тело метода"
            }

            if (name.equals("<clinit>")) {
                hasStaticBlock = true;
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new StaticBlockInjector(api, mv, libName);
            }

            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }

        @Override
        public void visitEnd() {
            if (!hasStaticBlock && !nativeMethodSignatures.isEmpty()) {
                System.out.println("Creating static initializer for native library");

                MethodVisitor mv = super.visitMethod(
                        Opcodes.ACC_STATIC,
                        "<clinit>",
                        "()V",
                        null,
                        null
                );

                mv.visitCode();

                mv.visitLdcInsn("/native/" + new File(libName).getName());
                mv.visitLdcInsn(className.replace('/', '.'));
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "ru/sarkolsss/NativeLoader",
                        "loadLibrary",
                        "(Ljava/lang/String;Ljava/lang/String;)V",
                        false
                );

                mv.visitInsn(Opcodes.RETURN);
                mv.visitMaxs(2, 0);
                mv.visitEnd();
            }

            super.visitEnd();
        }
    }

    private static class StaticBlockInjector extends MethodVisitor {
        private final String libName;
        private boolean injected = false;

        public StaticBlockInjector(int api, MethodVisitor mv, String libName) {
            super(api, mv);
            this.libName = libName;
        }

        @Override
        public void visitCode() {
            super.visitCode();

            if (!injected) {
                mv.visitLdcInsn("/native/" + new File(libName).getName());
                mv.visitLdcInsn("");
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "ru/sarkolsss/NativeLoader",
                        "loadLibrary",
                        "(Ljava/lang/String;Ljava/lang/String;)V",
                        false
                );
                injected = true;
                System.out.println("Injected library load into existing static block");
            }
        }
    }
}