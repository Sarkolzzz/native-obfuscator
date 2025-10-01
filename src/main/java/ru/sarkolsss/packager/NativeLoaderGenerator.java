package ru.sarkolsss.packager;

import org.objectweb.asm.*;
import ru.sarkolsss.utils.Logger;
import java.io.File;
import java.io.InputStream;

public class NativeLoaderGenerator {

    public static byte[] generateLoaderClass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC,
                "ru/sarkolsss/NativeLoader", null,
                "java/lang/Object", null);

        generateExtractLibraryMethod(cw);
        generateLoadNativeLibraryMethod(cw);

        cw.visitEnd();

        Logger.detail("Generated NativeLoader class");
        return cw.toByteArray();
    }

    private static void generateExtractLibraryMethod(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "extractLibrary",
                "()Ljava/io/File;",
                null,
                new String[]{"java/io/IOException"}
        );
        mv.visitCode();

        mv.visitLdcInsn("java.io.tmpdir");
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System",
                "getProperty", "(Ljava/lang/String;)Ljava/lang/String;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 0);

        mv.visitTypeInsn(Opcodes.NEW, "java/io/File");
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitLdcInsn("java2cpp_native_" + System.currentTimeMillis() + ".dll");
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/File",
                "<init>", "(Ljava/lang/String;Ljava/lang/String;)V", false);
        mv.visitVarInsn(Opcodes.ASTORE, 1);

        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/File",
                "exists", "()Z", false);
        Label notExistsLabel = new Label();
        mv.visitJumpInsn(Opcodes.IFEQ, notExistsLabel);

        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitInsn(Opcodes.ARETURN);

        mv.visitLabel(notExistsLabel);
        mv.visitFrame(Opcodes.F_APPEND, 2,
                new Object[]{"java/lang/String", "java/io/File"}, 0, null);

        mv.visitLdcInsn(Type.getType("Lru/sarkolsss/NativeLoader;"));
        mv.visitLdcInsn("/native/java2cpp_native.dll");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class",
                "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 2);

        mv.visitVarInsn(Opcodes.ALOAD, 2);
        Label streamNotNullLabel = new Label();
        mv.visitJumpInsn(Opcodes.IFNONNULL, streamNotNullLabel);

        mv.visitTypeInsn(Opcodes.NEW, "java/io/IOException");
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn("Native library not found in JAR");
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/IOException",
                "<init>", "(Ljava/lang/String;)V", false);
        mv.visitInsn(Opcodes.ATHROW);

        mv.visitLabel(streamNotNullLabel);
        mv.visitFrame(Opcodes.F_APPEND, 1,
                new Object[]{"java/io/InputStream"}, 0, null);

        mv.visitTypeInsn(Opcodes.NEW, "java/io/FileOutputStream");
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/FileOutputStream",
                "<init>", "(Ljava/io/File;)V", false);
        mv.visitVarInsn(Opcodes.ASTORE, 3);

        mv.visitIntInsn(Opcodes.SIPUSH, 8192);
        mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE);
        mv.visitVarInsn(Opcodes.ASTORE, 4);

        Label loopStart = new Label();
        mv.visitLabel(loopStart);
        mv.visitFrame(Opcodes.F_APPEND, 2,
                new Object[]{"java/io/FileOutputStream", "[B"}, 0, null);

        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitVarInsn(Opcodes.ALOAD, 4);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/InputStream",
                "read", "([B)I", false);
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ISTORE, 5);

        mv.visitInsn(Opcodes.ICONST_0);
        Label loopEnd = new Label();
        mv.visitJumpInsn(Opcodes.IF_ICMPLE, loopEnd);

        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitVarInsn(Opcodes.ALOAD, 4);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitVarInsn(Opcodes.ILOAD, 5);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/FileOutputStream",
                "write", "([BII)V", false);

        mv.visitJumpInsn(Opcodes.GOTO, loopStart);

        mv.visitLabel(loopEnd);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);

        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/FileOutputStream",
                "close", "()V", false);

        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/InputStream",
                "close", "()V", false);

        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitInsn(Opcodes.ARETURN);

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void generateLoadNativeLibraryMethod(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNCHRONIZED,
                "loadNativeLibrary",
                "()V",
                null,
                null
        );
        mv.visitCode();

        Label tryStart = new Label();
        Label tryEnd = new Label();
        Label catchStart = new Label();
        Label methodEnd = new Label();

        mv.visitTryCatchBlock(tryStart, tryEnd, catchStart, "java/lang/Throwable");

        mv.visitLabel(tryStart);

        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "ru/sarkolsss/NativeLoader",
                "extractLibrary", "()Ljava/io/File;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 0);

        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/File",
                "getAbsolutePath", "()Ljava/lang/String;", false);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System",
                "load", "(Ljava/lang/String;)V", false);

        mv.visitLabel(tryEnd);
        mv.visitJumpInsn(Opcodes.GOTO, methodEnd);

        mv.visitLabel(catchStart);
        mv.visitFrame(Opcodes.F_SAME1, 0, null, 1,
                new Object[]{"java/lang/Throwable"});
        mv.visitVarInsn(Opcodes.ASTORE, 0);

        mv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException");
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn("Failed to load native library");
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/RuntimeException",
                "<init>", "(Ljava/lang/String;Ljava/lang/Throwable;)V", false);
        mv.visitInsn(Opcodes.ATHROW);

        mv.visitLabel(methodEnd);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitInsn(Opcodes.RETURN);

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
}   