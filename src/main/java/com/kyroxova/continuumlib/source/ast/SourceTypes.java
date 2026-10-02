package com.kyroxova.continuumlib.source.ast;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

public final class SourceTypes {
    private SourceTypes() {
    }

    public static List<TypeDeclaration<?>> all(Node root) {
        List<TypeDeclaration<?>> types = new ArrayList<>();
        root.accept(new VoidVisitorAdapter<List<TypeDeclaration<?>>>() {
            @Override
            public void visit(ClassOrInterfaceDeclaration type, List<TypeDeclaration<?>> found) {
                add(type, found);
                super.visit(type, found);
            }

            @Override
            public void visit(EnumDeclaration type, List<TypeDeclaration<?>> found) {
                add(type, found);
                super.visit(type, found);
            }

            @Override
            public void visit(AnnotationDeclaration type, List<TypeDeclaration<?>> found) {
                add(type, found);
                super.visit(type, found);
            }

            @Override
            public void visit(RecordDeclaration type, List<TypeDeclaration<?>> found) {
                add(type, found);
                super.visit(type, found);
            }

            private void add(TypeDeclaration<?> type, List<TypeDeclaration<?>> found) {
                if (memberOrTopLevel(type)) {
                    found.add(type);
                }
            }
        }, types);

        types.sort(Comparator.comparingInt(SourceTypes::sourceOrder));
        return List.copyOf(types);
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
        return type.getFullyQualifiedName().orElseGet(() -> {
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
        });
    }

    private static boolean memberOrTopLevel(TypeDeclaration<?> type) {
        return type.getFullyQualifiedName().isPresent();
    }

    private static int sourceOrder(TypeDeclaration<?> type) {
        return type.getBegin()
                .map(position -> position.line * 1_000_000 + position.column)
                .orElse(Integer.MAX_VALUE);
    }
}
