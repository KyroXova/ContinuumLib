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
        root.walk(Node.TreeTraversal.PREORDER, node -> {
            if (node instanceof TypeDeclaration<?> type) {
                types.add(type);
            }
        });
        return List.copyOf(types);
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

    public static int depth(TypeDeclaration<?> type) {
        int depth = 0;
        Node current = type.getParentNode().orElse(null);
        while (current != null) {
            if (current instanceof TypeDeclaration<?>) depth++;
            current = current.getParentNode().orElse(null);
        }
        return depth;
    }

    public static String qualifiedName(TypeDeclaration<?> type) {
        String pkg = type.findCompilationUnit()
                .flatMap(unit -> unit.getPackageDeclaration())
                .map(declaration -> declaration.getNameAsString() + ".")
                .orElse("");

        Deque<String> names = new ArrayDeque<>();
        Node current = type;
        while (current != null) {
            if (current instanceof TypeDeclaration<?> declaration) {
                names.addFirst(declaration.getNameAsString());
            }
            current = current.getParentNode().orElse(null);
        }
        return pkg + String.join(".", names);
    }
}
