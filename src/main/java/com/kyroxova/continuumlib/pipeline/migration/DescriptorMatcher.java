package com.kyroxova.continuumlib.pipeline.migration;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for parsing and matching JVM method and field descriptors.
 */
public final class DescriptorMatcher {
    private DescriptorMatcher() {}

    /**
     * Parses parameter types from a JVM method descriptor like "(Ljava/lang/String;I)V"
     * into normalized Java type names like ["java.lang.String", "int"].
     */
    public static List<String> parseParameterTypes(String descriptor) {
        List<String> types = new ArrayList<>();
        if (descriptor == null || !descriptor.startsWith("(") || !descriptor.contains(")")) {
            return types;
        }
        int end = descriptor.indexOf(')');
        String params = descriptor.substring(1, end);
        int i = 0;
        while (i < params.length()) {
            int arrayDim = 0;
            while (i < params.length() && params.charAt(i) == '[') {
                arrayDim++;
                i++;
            }
            if (i >= params.length()) break;
            char c = params.charAt(i);
            String typeName;
            if (c == 'L') {
                int semi = params.indexOf(';', i);
                if (semi == -1) break;
                typeName = params.substring(i + 1, semi).replace('/', '.');
                i = semi + 1;
            } else {
                typeName = switch (c) {
                    case 'Z' -> "boolean";
                    case 'B' -> "byte";
                    case 'C' -> "char";
                    case 'S' -> "short";
                    case 'I' -> "int";
                    case 'J' -> "long";
                    case 'F' -> "float";
                    case 'D' -> "double";
                    default -> "java.lang.Object";
                };
                i++;
            }
            if (arrayDim > 0) {
                typeName = typeName + "[]".repeat(arrayDim);
            }
            types.add(typeName);
        }
        return types;
    }

    /**
     * Extracts return type from method descriptor like "(Ljava/lang/String;)V" -> "void".
     */
    public static String parseReturnType(String descriptor) {
        if (descriptor == null || !descriptor.contains(")")) return "void";
        int end = descriptor.indexOf(')');
        String ret = descriptor.substring(end + 1);
        if (ret.isEmpty()) return "void";
        int arrayDim = 0;
        int i = 0;
        while (i < ret.length() && ret.charAt(i) == '[') {
            arrayDim++;
            i++;
        }
        if (i >= ret.length()) return "void";
        char c = ret.charAt(i);
        String typeName;
        if (c == 'L') {
            int semi = ret.indexOf(';', i);
            if (semi == -1) semi = ret.length();
            typeName = ret.substring(i + 1, semi).replace('/', '.');
        } else if (c == 'V') {
            typeName = "void";
        } else {
            typeName = switch (c) {
                case 'Z' -> "boolean";
                case 'B' -> "byte";
                case 'C' -> "char";
                case 'S' -> "short";
                case 'I' -> "int";
                case 'J' -> "long";
                case 'F' -> "float";
                case 'D' -> "double";
                default -> "java.lang.Object";
            };
        }
        if (arrayDim > 0) {
            typeName = typeName + "[]".repeat(arrayDim);
        }
        return typeName;
    }

    public static int parameterCount(String descriptor) {
        return parseParameterTypes(descriptor).size();
    }
}
