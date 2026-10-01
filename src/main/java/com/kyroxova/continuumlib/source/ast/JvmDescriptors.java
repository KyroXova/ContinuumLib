package com.kyroxova.continuumlib.source.ast;

import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.kyroxova.continuumlib.bytecode.MemberReference;

import java.util.Optional;

public final class JvmDescriptors {
    private JvmDescriptors() {
    }

    public static Optional<MemberReference> method(ResolvedMethodDeclaration method) {
        try {
            return Optional.of(new MemberReference(
                    internal(method.declaringType().getQualifiedName()),
                    method.getName(),
                    method.toDescriptor()
            ));
        } catch (RuntimeException unresolved) {
            return Optional.empty();
        }
    }

    public static Optional<MemberReference> constructor(ResolvedConstructorDeclaration constructor) {
        try {
            StringBuilder descriptor = new StringBuilder("(");
            for (int i = 0; i < constructor.getNumberOfParams(); i++) {
                descriptor.append(constructor.getParam(i).getType().toDescriptor());
            }
            descriptor.append(")V");
            return Optional.of(new MemberReference(
                    internal(constructor.declaringType().getQualifiedName()),
                    "<init>",
                    descriptor.toString()
            ));
        } catch (RuntimeException unresolved) {
            return Optional.empty();
        }
    }

    public static Optional<MemberReference> field(ResolvedFieldDeclaration field) {
        try {
            return Optional.of(new MemberReference(
                    internal(field.declaringType().getQualifiedName()),
                    field.getName(),
                    field.getType().toDescriptor()
            ));
        } catch (RuntimeException unresolved) {
            return Optional.empty();
        }
    }

    private static String internal(String qualifiedName) {
        return qualifiedName.replace('.', '/');
    }
}
