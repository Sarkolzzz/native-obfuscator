package ru.sarkolsss.bytecode;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import java.util.List;

public class MethodTransformer extends MethodVisitor {
    private final int access;
    private final String methodName;
    private final String descriptor;
    private final String className;
    private final List<NativeMethodInfo> nativeMethods;
    private final boolean skipAnnotationCheck;
    private final boolean classHasNativeAnnotation;
    private boolean methodHasNativeAnnotation = false;

    public MethodTransformer(int access, String name, String descriptor,
                             String className, List<NativeMethodInfo> nativeMethods,
                             boolean skipAnnotationCheck, boolean classHasNativeAnnotation) {
        super(Opcodes.ASM9);
        this.access = access;
        this.methodName = name;
        this.descriptor = descriptor;
        this.className = className;
        this.nativeMethods = nativeMethods;
        this.skipAnnotationCheck = skipAnnotationCheck;
        this.classHasNativeAnnotation = classHasNativeAnnotation;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if (descriptor.equals("Lru/sarkolsss/annotations/Native;")) {
            methodHasNativeAnnotation = true;
        }
        return super.visitAnnotation(descriptor, visible);
    }

    @Override
    public void visitCode() {
        super.visitCode();
    }

    @Override
    public void visitEnd() {
        boolean shouldAddMethod = false;

        if (skipAnnotationCheck) {
            if (!methodName.equals("<init>") && !methodName.equals("<clinit>") &&
                    (access & Opcodes.ACC_ABSTRACT) == 0 &&
                    (access & Opcodes.ACC_NATIVE) == 0) {
                shouldAddMethod = true;
            }
        } else {
            if (methodHasNativeAnnotation || classHasNativeAnnotation) {
                shouldAddMethod = true;
            }
        }

        if (shouldAddMethod) {
            nativeMethods.add(new NativeMethodInfo(
                    className, methodName, descriptor, access
            ));
        }

        super.visitEnd();
    }
}