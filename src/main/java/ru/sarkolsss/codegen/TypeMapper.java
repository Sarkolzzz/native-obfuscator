package ru.sarkolsss.codegen;

import java.util.ArrayList;
import java.util.List;

public class TypeMapper {

    public String mapJavaTypeToCpp(String javaType) {
        return switch (javaType) {
            case "void" -> "void";
            case "boolean" -> "jboolean";
            case "byte" -> "jbyte";
            case "char" -> "jchar";
            case "short" -> "jshort";
            case "int" -> "jint";
            case "long" -> "jlong";
            case "float" -> "jfloat";
            case "double" -> "jdouble";
            default -> "jobject";
        };
    }

    public String getReturnType(String descriptor) {
        int returnStart = descriptor.indexOf(')') + 1;
        return parseType(descriptor.substring(returnStart));
    }

    public String[] getParameterTypes(String descriptor) {
        int paramsEnd = descriptor.indexOf(')');
        String paramsDesc = descriptor.substring(1, paramsEnd);

        List<String> params = new ArrayList<>();
        int i = 0;

        while (i < paramsDesc.length()) {
            String type = parseTypeAtIndex(paramsDesc, i);
            params.add(type);
            i += getTypeLength(paramsDesc, i);
        }

        return params.toArray(new String[0]);
    }

    public String getParameters(String descriptor) {
        String[] types = getParameterTypes(descriptor);
        List<String> params = new ArrayList<>();

        for (int i = 0; i < types.length; i++) {
            String cppType = mapJavaTypeToCpp(types[i]);
            params.add(cppType + " param" + i);
        }

        return String.join(", ", params);
    }

    public String parseType(String typeDesc) {
        if (typeDesc.startsWith("L")) {
            return typeDesc.substring(1, typeDesc.length() - 1).replace('/', '.');
        }

        return switch (typeDesc.charAt(0)) {
            case 'V' -> "void";
            case 'Z' -> "boolean";
            case 'B' -> "byte";
            case 'C' -> "char";
            case 'S' -> "short";
            case 'I' -> "int";
            case 'J' -> "long";
            case 'F' -> "float";
            case 'D' -> "double";
            case '[' -> "array";
            default -> "object";
        };
    }

    private String parseTypeAtIndex(String desc, int index) {
        char c = desc.charAt(index);

        if (c == 'L') {
            int end = desc.indexOf(';', index);
            return desc.substring(index + 1, end).replace('/', '.');
        }

        return parseType(String.valueOf(c));
    }

    private int getTypeLength(String desc, int index) {
        char c = desc.charAt(index);

        if (c == 'L') {
            return desc.indexOf(';', index) - index + 1;
        }

        return 1;
    }

    public String getDefaultValue(String type) {
        return switch (type) {
            case "boolean" -> "false";
            case "byte", "char", "short", "int" -> "0";
            case "long" -> "0L";
            case "float" -> "0.0f";
            case "double" -> "0.0";
            default -> "nullptr";
        };
    }
}