package com.kyroxova.continuumlib.source.ast;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SourceTypes {
    private SourceTypes() {
    }

    public static List<TypeDeclaration<?>> all(Node root) {
        List<TypeDeclaration<?>> types = new ArrayList<>();
        collect(root, types);
        types.sort(Comparator.comparingInt(SourceTypes::sourceOrder));
        return List.copyOf(types);
    }

    private static void collect(Node node, List<TypeDeclaration<?>> types) {
        if (node instanceof TypeDeclaration<?> type && type.getFullyQualifiedName().isPresent()) {
            types.add(type);
        }
        for (Node child : node.getChildNodes()) {
            collect(child, types);
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
        return type.isNestedType();
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
        return type.getFullyQualifiedName()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Type is not a top-level or member declaration: " + type.getNameAsString()));
    }

    private static int sourceOrder(TypeDeclaration<?> type) {
        return type.getBegin()
                .map(position -> position.line * 1_000_000 + position.column)
                .orElse(Integer.MAX_VALUE);
    }
}
