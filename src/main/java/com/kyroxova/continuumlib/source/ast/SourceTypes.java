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
        for (Node child : type.getChildNodes()) {
            if (child instanceof TypeDeclaration<?> nested) {
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
        return type.remove();
    }

    public static boolean nested(TypeDeclaration<?> type) {
        return type.getParentNode().filter(TypeDeclaration.class::isInstance).isPresent();
    }

    public static int depth(TypeDeclaration<?> type) {
        int depth = 0;
        Node current = type.getParentNode().orElse(null);
        while (current != null) {
            if (current instanceof TypeDeclaration<?>) {
                depth++;
            }
            current = current.getParentNode().orElse(null);
        }
        return depth;
    }

    public static String qualifiedName(TypeDeclaration<?> type) {
        Deque<String> names = new ArrayDeque<>();
        Node current = type;
        while (current != null) {
            if (current instanceof TypeDeclaration<?> declaration) {
                names.addFirst(declaration.getNameAsString());
            }
            current = current.getParentNode().orElse(null);
        }

        String pkg = type.findCompilationUnit()
                .flatMap(unit -> unit.getPackageDeclaration())
                .map(declaration -> declaration.getNameAsString() + ".")
                .orElse("");
        return pkg + String.join(".", names);
    }
}
