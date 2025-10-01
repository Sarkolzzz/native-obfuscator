package ru.sarkolsss.packager;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.List;

public class StaticInitializerInjector {
    
    public static byte[] injectStaticInitializer(byte[] classBytes, String className) {
        ClassReader reader = new ClassReader(classBytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        
        MethodNode clinit = findClinit(classNode);
        
        if (clinit == null) {
            clinit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            clinit.instructions = new InsnList();
            classNode.methods.add(clinit);
        }
        
        InsnList loaderCall = new InsnList();
        loaderCall.add(new MethodInsnNode(Opcodes.INVOKESTATIC, 
                "ru/sarkolsss/NativeLoader", "loadNativeLibrary", "()V", false));
        
        if (clinit.instructions.size() == 0) {
            clinit.instructions.add(loaderCall);
            clinit.instructions.add(new InsnNode(Opcodes.RETURN));
        } else {
            clinit.instructions.insert(loaderCall);
        }
        
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        
        return writer.toByteArray();
    }
    
    private static MethodNode findClinit(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            if (method.name.equals("<clinit>")) {
                return method;
            }
        }
        return null;
    }
}
