package ru.sarkolsss.util;

import org.objectweb.asm.Type;
import ru.sarkolsss.annotation.Native;
import ru.sarkolsss.annotation.NotNative;

import java.lang.reflect.Method;

public class Util {
    public static String convertType(Type type) {
        switch (type.getDescriptor()) {
            case "B": return "jbyte";
            case "C": return "jchar";
            case "S": return "jshort";
            case "I": return "jint";
            case "F": return "jfloat";
            case "J": return "jlong";
            case "D": return "jdouble";
            case "Z": return "jboolean";
            case "V": return "void";

            default:
                if (type.getDescriptor().startsWith("[")) {
                    return switch (type.getDescriptor().charAt(1)) {
                        case 'B' -> "jbyteArray";
                        case 'C' -> "jcharArray";
                        case 'S' -> "jshortArray";
                        case 'I' -> "jintArray";
                        case 'F' -> "jfloatArray";
                        case 'J' -> "jlongArray";
                        case 'D' -> "jdoubleArray";
                        case 'Z' -> "jbooleanArray";
                        default -> "jobjectArray";
                    };
                }

                else {
                    return "jobject";
                }
        }
    }

    public static boolean canProcess(Class<?> clazz) {
        return clazz.isAnnotationPresent(Native.class);
    }

    public static boolean canProcess(Method method) {
        return method.getDeclaringClass().isAnnotationPresent(Native.class) ?
                !method.isAnnotationPresent(NotNative.class)
                        && notInit(method.getName())
                : method.isAnnotationPresent(Native.class)
                && notInit(method.getName());
    }

    public static boolean notInit(String name) {
        return !name.equals("<init>") && !name.equals("<clinit>");
    }

    public static String getString(String str) {
        return String.format("L%s;", str);
    }

    public static String getMethodSignature(Method method) {
        Type returnType = Type.getType(method.getReturnType());
        Type[] paramTypes = new Type[method.getParameterTypes().length];

        for (int i = 0; i < method.getParameterTypes().length; i++) {
            paramTypes[i] = Type.getType(method.getParameterTypes()[i]);
        }

        return Type.getMethodDescriptor(returnType, paramTypes);
    }
}