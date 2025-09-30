package ru.sarkolsss.processor.impl;

import org.objectweb.asm.*;
import ru.sarkolsss.processor.IProcessor;
import ru.sarkolsss.util.MethodContext;
import ru.sarkolsss.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class MethodProcessor implements IProcessor {

    @Override
    public void process(MethodContext ctx) {
        if (!Util.canProcess(ctx.method)) {
            return;
        }

        Method method = ctx.method;
        String className = method.getDeclaringClass().getName().replace('.', '_');
        String methodName = method.getName();
        String jniName = String.format("Java_%s_%s", className, methodName);

        Type returnType = Type.getType(method.getReturnType());
        String cppReturnType = Util.convertType(returnType);

        StringBuilder params = new StringBuilder();
        Class<?>[] paramTypes = method.getParameterTypes();

        for (int i = 0; i < paramTypes.length; i++) {
            Type paramType = Type.getType(paramTypes[i]);
            params.append(Util.convertType(paramType))
                    .append(" arg").append(i);
            if (i < paramTypes.length - 1) {
                params.append(", ");
            }
        }

        ctx.content.append("\nJNIEXPORT ").append(cppReturnType)
                .append(" JNICALL ").append(jniName)
                .append("(JNIEnv* env, ")
                .append(Modifier.isStatic(method.getModifiers()) ? "jclass clazz" : "jobject obj");

        if (paramTypes.length > 0) {
            ctx.content.append(", ").append(params);
        }

        ctx.content.append(") {\n");

        try {
            generateMethodBodyFromBytecode(ctx, method);
        } catch (IOException e) {
            generateDefaultMethodBody(ctx, method);
        }

        String bodyContent = ctx.content.toString();
        bodyContent = cleanupUnreachableLabels(bodyContent);

        ctx.content.setLength(0);
        ctx.content.append(bodyContent);

        ctx.content.append("}\n");
    }

    private String cleanupUnreachableLabels(String code) {
        String[] lines = code.split("\n");
        StringBuilder cleaned = new StringBuilder();
        boolean afterReturn = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.equals("return;") || trimmed.startsWith("return ")) {
                cleaned.append(line).append("\n");
                afterReturn = true;
                continue;
            }

            if (afterReturn) {
                if (trimmed.matches("L\\d+:")) {
                    continue;
                }
                if (trimmed.equals("}")) {
                    cleaned.append(line).append("\n");
                    afterReturn = false;
                    continue;
                }
            }

            if (!afterReturn) {
                cleaned.append(line).append("\n");
            }
        }

        return cleaned.toString();
    }

    private void generateMethodBodyFromBytecode(MethodContext ctx, Method method) throws IOException {
        String className = method.getDeclaringClass().getName().replace('.', '/');
        InputStream is = method.getDeclaringClass().getClassLoader()
                .getResourceAsStream(className + ".class");

        if (is == null) {
            generateDefaultMethodBody(ctx, method);
            return;
        }

        ClassReader cr = new ClassReader(is);
        BytecodeTranslator translator = new BytecodeTranslator(ctx, method);
        cr.accept(translator, 0);
    }

    private void generateDefaultMethodBody(MethodContext ctx, Method method) {
        Type returnType = Type.getType(method.getReturnType());
        if (!returnType.getDescriptor().equals("V")) {
            String defaultReturn = getDefaultReturn(returnType);
            ctx.content.append("    return ").append(defaultReturn).append(";\n");
        } else {
            ctx.content.append("    return;\n");
        }
    }

    private String getDefaultReturn(Type type) {
        switch (type.getDescriptor()) {
            case "B":
            case "C":
            case "S":
            case "I":
                return "0";
            case "F":
                return "0.0f";
            case "J":
                return "0L";
            case "D":
                return "0.0";
            case "Z":
                return "false";
            default:
                return "nullptr";
        }
    }
}