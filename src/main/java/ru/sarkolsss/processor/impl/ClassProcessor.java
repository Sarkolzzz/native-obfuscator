package ru.sarkolsss.processor.impl;

import ru.sarkolsss.util.ClassContext;
import ru.sarkolsss.util.MethodContext;
import ru.sarkolsss.util.Util;

import java.lang.reflect.Method;

public class ClassProcessor {
    private final MethodProcessor methodProcessor = new MethodProcessor();

    public void process(ClassContext ctx) {
        if (!Util.canProcess(ctx.clazz)) {
            return;
        }

        ctx.content.append("#include <jni.h>\n");
        ctx.content.append("#include <cstdlib>\n");
        ctx.content.append("#include <cstring>\n");
        ctx.content.append("#include <iostream>\n\n");

        ctx.content.append("// Generated native code for class ")
                .append(ctx.clazz.getName()).append("\n\n");

        for (Method method : ctx.clazz.getDeclaredMethods()) {
            if (Util.canProcess(method)) {
                MethodContext methodCtx = new MethodContext();
                methodCtx.method = method;
                methodProcessor.process(methodCtx);
                ctx.content.append(methodCtx.content);
            }
        }

        generateRegistrationFunction(ctx);
    }

    private void generateRegistrationFunction(ClassContext ctx) {
        String className = ctx.clazz.getName().replace('.', '/');
        Method[] methods = ctx.clazz.getDeclaredMethods();

        ctx.content.append("\n// JNI Native Methods Registration\n");
        ctx.content.append("static JNINativeMethod methods[] = {\n");

        boolean first = true;
        for (Method method : methods) {
            if (Util.canProcess(method)) {
                if (!first) {
                    ctx.content.append(",\n");
                }
                String methodName = method.getName();
                String signature = Util.getMethodSignature(method);
                String nativeName = "Java_" + ctx.clazz.getName().replace('.', '_') + "_" + methodName;

                ctx.content.append("    {\"").append(methodName)
                        .append("\", \"").append(signature)
                        .append("\", (void*)").append(nativeName).append("}");
                first = false;
            }
        }

        ctx.content.append("\n};\n\n");

        ctx.content.append("JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {\n");
        ctx.content.append("    JNIEnv* env;\n");
        ctx.content.append("    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_8) != JNI_OK) {\n");
        ctx.content.append("        return JNI_ERR;\n");
        ctx.content.append("    }\n\n");

        ctx.content.append("    jclass clazz = env->FindClass(\"").append(className).append("\");\n");
        ctx.content.append("    if (clazz == nullptr) {\n");
        ctx.content.append("        return JNI_ERR;\n");
        ctx.content.append("    }\n\n");

        ctx.content.append("    if (env->RegisterNatives(clazz, methods, sizeof(methods)/sizeof(methods[0])) < 0) {\n");
        ctx.content.append("        return JNI_ERR;\n");
        ctx.content.append("    }\n\n");

        ctx.content.append("    return JNI_VERSION_1_8;\n");
        ctx.content.append("}\n");
    }
}