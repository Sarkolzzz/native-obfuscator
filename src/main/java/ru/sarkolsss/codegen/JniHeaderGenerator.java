package ru.sarkolsss.codegen;

import ru.sarkolsss.bytecode.NativeMethodInfo;

public class JniHeaderGenerator {
    private final TypeMapper typeMapper = new TypeMapper();

    public String generateSignature(NativeMethodInfo method) {
        StringBuilder sig = new StringBuilder();

        String returnType = typeMapper.mapJavaTypeToCpp(
                typeMapper.getReturnType(method.getDescriptor())
        );

        sig.append("JNIEXPORT ").append(returnType).append(" JNICALL ");
        sig.append(method.getJniMethodName());
        sig.append("(JNIEnv* env, jobject obj");

        String params = typeMapper.getParameters(method.getDescriptor());
        if (!params.isEmpty()) {
            sig.append(", ").append(params);
        }

        sig.append(")");

        return sig.toString();
    }
}