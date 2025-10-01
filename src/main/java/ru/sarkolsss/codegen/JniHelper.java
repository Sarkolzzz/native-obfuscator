package ru.sarkolsss.codegen;

import ru.sarkolsss.codegen.TypeMapper;

public class JniHelper {
    private final TypeMapper typeMapper = new TypeMapper();
    
    public String getFieldMethod(String descriptor) {
        char typeChar = descriptor.charAt(0);
        return switch (typeChar) {
            case 'Z' -> "GetBooleanField";
            case 'B' -> "GetByteField";
            case 'C' -> "GetCharField";
            case 'S' -> "GetShortField";
            case 'I' -> "GetIntField";
            case 'J' -> "GetLongField";
            case 'F' -> "GetFloatField";
            case 'D' -> "GetDoubleField";
            default -> "GetObjectField";
        };
    }
    
    public String setFieldMethod(String descriptor) {
        char typeChar = descriptor.charAt(0);
        return switch (typeChar) {
            case 'Z' -> "SetBooleanField";
            case 'B' -> "SetByteField";
            case 'C' -> "SetCharField";
            case 'S' -> "SetShortField";
            case 'I' -> "SetIntField";
            case 'J' -> "SetLongField";
            case 'F' -> "SetFloatField";
            case 'D' -> "SetDoubleField";
            default -> "SetObjectField";
        };
    }
    
    public String getStaticFieldMethod(String descriptor) {
        char typeChar = descriptor.charAt(0);
        return switch (typeChar) {
            case 'Z' -> "GetStaticBooleanField";
            case 'B' -> "GetStaticByteField";
            case 'C' -> "GetStaticCharField";
            case 'S' -> "GetStaticShortField";
            case 'I' -> "GetStaticIntField";
            case 'J' -> "GetStaticLongField";
            case 'F' -> "GetStaticFloatField";
            case 'D' -> "GetStaticDoubleField";
            default -> "GetStaticObjectField";
        };
    }
    
    public String setStaticFieldMethod(String descriptor) {
        char typeChar = descriptor.charAt(0);
        return switch (typeChar) {
            case 'Z' -> "SetStaticBooleanField";
            case 'B' -> "SetStaticByteField";
            case 'C' -> "SetStaticCharField";
            case 'S' -> "SetStaticShortField";
            case 'I' -> "SetStaticIntField";
            case 'J' -> "SetStaticLongField";
            case 'F' -> "SetStaticFloatField";
            case 'D' -> "SetStaticDoubleField";
            default -> "SetStaticObjectField";
        };
    }
    
    public String getCallMethod(String descriptor, boolean isStatic) {
        int returnStart = descriptor.indexOf(')') + 1;
        char typeChar = descriptor.charAt(returnStart);
        
        String prefix = isStatic ? "CallStatic" : "Call";
        
        return switch (typeChar) {
            case 'V' -> prefix + "VoidMethod";
            case 'Z' -> prefix + "BooleanMethod";
            case 'B' -> prefix + "ByteMethod";
            case 'C' -> prefix + "CharMethod";
            case 'S' -> prefix + "ShortMethod";
            case 'I' -> prefix + "IntMethod";
            case 'J' -> prefix + "LongMethod";
            case 'F' -> prefix + "FloatMethod";
            case 'D' -> prefix + "DoubleMethod";
            default -> prefix + "ObjectMethod";
        };
    }
    
    public String convertToJniReturn(String value, String javaType, StringBuilder code) {
        return switch (javaType) {
            case "boolean" -> "(jboolean)" + value;
            case "byte" -> "(jbyte)" + value;
            case "char" -> "(jchar)" + value;
            case "short" -> "(jshort)" + value;
            case "int" -> "(jint)" + value;
            case "long" -> "(jlong)" + value;
            case "float" -> "(jfloat)" + value;
            case "double" -> "(jdouble)" + value;
            default -> value;
        };
    }
}
