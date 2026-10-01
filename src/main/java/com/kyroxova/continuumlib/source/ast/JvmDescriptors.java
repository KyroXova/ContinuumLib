package com.kyroxova.continuumlib.source.ast;

import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.kyroxova.continuumlib.bytecode.MemberReference;

import java.util.Optional;

public final class JvmDescriptors {
    private JvmDescriptors() {
    }

    public static Optional<MemberReference> method(ResolvedMethodDeclaration method) {
        try {
            StringBuilder descriptor = new StringBuilder("(");
            for (int i = 0; i < method.getNumberOfParams(); i++) {
                Optional<String> parameter = descriptor(method.getParam(i).getType());
                if (parameter.isEmpty()) return Optional.empty();
                descriptor.append(parameter.get());
            }
            Optional<String> result = descriptor(method.getReturnType());
            if (result.isEmpty()) return Optional.empty();
            descriptor.append(')').append(result.get());
            return Optional.of(new MemberReference(
                    internal(method.declaringType()),
                    method.getName(),
                    descriptor.toString()
            ));
        } catch (RuntimeException unresolved) {
            return Optional.empty();
        }
    }

    public static Optional<MemberReference> constructor(ResolvedConstructorDeclaration constructor) {
        try {
            StringBuilder descriptor = new StringBuilder("(");
            for (int i = 0; i < constructor.getNumberOfParams(); i++) {
                Optional<String> parameter = descriptor(constructor.getParam(i).getType());
                if (parameter.isEmpty()) return Optional.empty();
                descriptor.append(parameter.get());
            }
            descriptor.append(")V");
            return Optional.of(new MemberReference(
                    internal(constructor.declaringType()),
                    "<init>",
                    descriptor.toString()
            ));
        } catch (RuntimeException unresolved) {
            return Optional.empty();
        }
    }

    public static Optional<MemberReference> field(ResolvedFieldDeclaration field) {
        try {
            return descriptor(field.getType()).map(type -> new MemberReference(
                    internal(field.declaringType()),
                    field.getName(),
                    type
            ));
        } catch (RuntimeException unresolved) {
            return Optional.empty();
        }
    }

    private static Optional<String> descriptor(ResolvedType type) {
        if (type.isVoid()) return Optional.of("V");

        if (type.isPrimitive()) {
            return Optional.of(switch (type.describe()) {
                case "boolean" -> "Z";
                case "byte" -> "B";
                case "char" -> "C";
                case "short" -> "S";
                case "int" -> "I";
                case "long" -> "J";
                case "float" -> "F";
                case "double" -> "D";
                default -> throw new IllegalArgumentException("Unknown primitive type: " + type.describe());
            });
        }

        if (type.isArray()) {
            return descriptor(type.asArrayType().getComponentType()).map(component -> "[" + component);
        }

        if (type.isReferenceType()) {
            var reference = type.asReferenceType();
            String name = reference.getTypeDeclaration()
                    .map(JvmDescriptors::internal)
                    .orElseGet(() -> reference.getQualifiedName().replace('.', '/'));
            return Optional.of("L" + name + ";");
        }

        return Optional.empty();
    }

    private static String internal(ResolvedReferenceTypeDeclaration type) {
        return type.containerType()
                .map(container -> internal(container) + "$" + type.getName())
                .orElseGet(() -> type.getQualifiedName().replace('.', '/'));
    }
}
