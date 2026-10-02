package com.kyroxova.continuumlib.source.ast;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

public final class SourceTypes {
    private SourceTypes() {
    }

    public static List<TypeDeclaration<?>> all(Node root) {
        return root.findAll(TypeDeclaration.class).stream()
                .map(type -> (TypeDeclaration<?>) type)
                .filter(SourceTypes::memberOrTopLevel)
                .sorted(Comparator.comparingInt(SourceTypes::sourceOrder))
                .toList();
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

    private static boolean memberOrTopLevel(TypeDeclaration<?> type) {
        Node parent = type.getParentNode().orElse(null);
        return parent instanceof TypeDeclaration<?>
                || parent instanceof com.github.javaparser.ast.CompilationUnit;
    }

    private static int sourceOrder(TypeDeclaration<?> type) {
        return type.getBegin()
                .map(position -> position.line * 1_000_000 + position.column)
                .orElse(Integer.MAX_VALUE);
    }
}
