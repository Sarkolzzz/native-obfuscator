package ru.sarkolsss.bytecode;

public class NativeMethodInfo {
    private final String className;
    private final String methodName;
    private final String descriptor;
    private final int access;

    public NativeMethodInfo(String className, String methodName,
                            String descriptor, int access) {
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.access = access;
    }

    public String getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public String getDescriptor() { return descriptor; }
    public int getAccess() { return access; }

    public String getJniMethodName() {
        return "Java_" + className.replace('/', '_') + "_" + methodName;
    }

    public String getSimpleName() {
        return className.substring(className.lastIndexOf('/') + 1) + "." + methodName;
    }
}