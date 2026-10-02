package com.kyroxova.continuumlib.source.ast;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class SourceTypes {
    private SourceTypes() {
    }

    public static List<TypeDeclaration<?>> all(Node root) {
        List<TypeDeclaration<?>> types = new ArrayList<>();
        if (root instanceof com.github.javaparser.ast.CompilationUnit unit) {
            for (TypeDeclaration<?> type : unit.getTypes()) {
                collect(type, types);
            }
        } else if (root instanceof TypeDeclaration<?> type) {
            collect(type, types);
        } else {
            root.findCompilationUnit().ifPresent(unit -> {
                for (TypeDeclaration<?> type : unit.getTypes()) {
                    collect(type, types);
                }
            });
        }
        return List.copyOf(types);
    }

    private static void collect(TypeDeclaration<?> type, List<TypeDeclaration<?>> types) {
        types.add(type);
        for (var member : type.getMembers()) {
            if (member instanceof TypeDeclaration<?> nested) {
                collect(nested, types);
            }
        }
    }

    public static List<FieldDeclaration> fields(TypeDeclaration<?> type) {
        return type.getMembers().stream()
                .filter(FieldDeclaration.class::isInstance)
                .map(FieldDeclaration.class::cast)
                .toList();
    }

    public static boolean remove(TypeDeclaration<?> type) {
        Node parent = type.getParentNode().orElse(null);
        if (parent instanceof TypeDeclaration<?> owner) {
            return owner.getMembers().remove(type);
        }
        if (parent instanceof com.github.javaparser.ast.CompilationUnit unit) {
            return unit.getTypes().remove(type);
        }
        return type.remove();
    }

    public static boolean nested(TypeDeclaration<?> type) {
        return type.isNestedType();
    }

    public static int depth(TypeDeclaration<?> type) {
        int depth = 0;
        Node current = type;
        while (current instanceof TypeDeclaration<?> declaration && declaration.isNestedType()) {
            depth++;
            current = declaration.getParentNode().orElse(null);
            while (current != null && !(current instanceof TypeDeclaration<?>)) {
                current = current.getParentNode().orElse(null);
            }
        }
        return depth;
    }

    public static String qualifiedName(TypeDeclaration<?> type) {
        return type.getFullyQualifiedName().orElseGet(() -> {
            String pkg = type.findCompilationUnit()
                    .flatMap(unit -> unit.getPackageDeclaration())
                    .map(declaration -> declaration.getNameAsString() + ".")
                    .orElse("");
            return pkg + type.getNameAsString();
        });
    }
}
