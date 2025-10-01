package ru.sarkolsss.bytecode;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import java.util.List;

public class ClassVisitor extends org.objectweb.asm.ClassVisitor {
    private final List<NativeMethodInfo> nativeMethods;
    private final boolean skipAnnotationCheck;
    private String currentClassName;
    private boolean currentClassHasNativeAnnotation = false;

    public ClassVisitor(List<NativeMethodInfo> nativeMethods, boolean skipAnnotationCheck) {
        super(Opcodes.ASM9);
        this.nativeMethods = nativeMethods;
        this.skipAnnotationCheck = skipAnnotationCheck;
    }

    @Override
    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {
        this.currentClassName = name;
        this.currentClassHasNativeAnnotation = false;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public org.objectweb.asm.AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if (descriptor.equals("Lru/sarkolsss/annotations/Native;")) {
            currentClassHasNativeAnnotation = true;
        }
        return super.visitAnnotation(descriptor, visible);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                     String signature, String[] exceptions) {
        return new MethodTransformer(access, name, descriptor,
                currentClassName, nativeMethods,
                skipAnnotationCheck, currentClassHasNativeAnnotation);
    }
}