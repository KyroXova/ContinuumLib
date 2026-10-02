package com.kyroxova.continuumlib.filter.validation;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.kyroxova.continuumlib.filter.condition.TargetContext;
import com.kyroxova.continuumlib.filter.registry.RegistryEntry;
import com.kyroxova.continuumlib.source.ast.SourceUnit;

import java.util.*;

public final class ExclusionConflictDetector {

    public void validate(
            TargetContext context,
            List<RegistryEntry> excludedEntries,
            Collection<SourceUnit> activeSourceUnits
    ) {
        if (excludedEntries == null || excludedEntries.isEmpty() || activeSourceUnits == null) {
            return;
        }

        String targetEnvDesc = "Minecraft " + context.environmentId().minecraftVersion()
                + (context.loaderVersion() != null
                ? " (" + context.environmentId().loader() + " " + context.loaderVersion() + ")"
                : "");

        for (RegistryEntry entry : excludedEntries) {
            String fieldName = entry.fieldName();

            for (SourceUnit unit : activeSourceUnits) {
                CompilationUnit ast = unit.ast();
                boolean isDeclaringFile = unit.relativePath().replace('\\', '/')
                        .equals(entry.sourcePath().replace('\\', '/'));
                boolean importsFieldStatic = importsStaticField(ast, entry);

                for (FieldAccessExpr access : ast.findAll(FieldAccessExpr.class)) {
                    if (!access.getNameAsString().equals(fieldName) || isInsideDeclaration(access, fieldName)) {
                        continue;
                    }
                    if (referencesEntry(access, entry)) {
                        fail(targetEnvDesc, entry, unit, access);
                    }
                }

                for (NameExpr name : ast.findAll(NameExpr.class)) {
                    if (!name.getNameAsString().equals(fieldName) || isInsideDeclaration(name, fieldName)) {
                        continue;
                    }
                    Optional<Boolean> resolved = resolvedNameReferencesEntry(name, entry);
                    boolean references = resolved.orElseGet(() ->
                            importsFieldStatic || (isDeclaringFile && isInsideOwnerType(name, entry.ownerClass())));
                    if (references) {
                        fail(targetEnvDesc, entry, unit, name);
                    }
                }
            }
        }
    }

    private static boolean referencesEntry(FieldAccessExpr expression, RegistryEntry entry) {
        Optional<Boolean> resolved = resolvedFieldAccessReferencesEntry(expression, entry);
        if (resolved.isPresent()) return resolved.get();

        String scope = expression.getScope().toString();
        String owner = entry.ownerClass();
        return scope.equals(owner) || owner.endsWith("." + scope);
    }

    private static Optional<Boolean> resolvedFieldAccessReferencesEntry(
            FieldAccessExpr expression,
            RegistryEntry entry
    ) {
        try {
            var resolved = expression.resolve();
            if (!resolved.isField()) return Optional.of(false);
            var field = resolved.asField();
            return Optional.of(field.getName().equals(entry.fieldName())
                    && sameOwner(field.declaringType().getQualifiedName(), entry.ownerClass()));
        } catch (Throwable unresolved) {
            return Optional.empty();
        }
    }

    private static Optional<Boolean> resolvedNameReferencesEntry(NameExpr expression, RegistryEntry entry) {
        try {
            var value = expression.resolve();
            if (!value.isField()) return Optional.of(false);
            var field = value.asField();
            return Optional.of(field.getName().equals(entry.fieldName())
                    && sameOwner(field.declaringType().getQualifiedName(), entry.ownerClass()));
        } catch (Throwable unresolved) {
            return Optional.empty();
        }
    }

    private static boolean importsStaticField(CompilationUnit ast, RegistryEntry entry) {
        for (ImportDeclaration imported : ast.getImports()) {
            if (!imported.isStatic()) continue;
            String name = imported.getNameAsString();
            if (imported.isAsterisk() && name.equals(entry.ownerClass())) return true;
            if (!imported.isAsterisk() && name.equals(entry.ownerClass() + "." + entry.fieldName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInsideOwnerType(Node node, String ownerClass) {
        Node current = node;
        while (current != null) {
            if (current instanceof TypeDeclaration<?> type) {
                String sourceName = sourceTypeName(type);
                String normalizedOwner = ownerClass.replace('$', '.');
                if (sameOwner(sourceName, ownerClass)
                        || sameOwner(ownerClass, sourceName)
                        || normalizedOwner.endsWith("." + sourceName)) {
                    return true;
                }
            }
            current = current.getParentNode().orElse(null);
        }
        return false;
    }

    private static String sourceTypeName(TypeDeclaration<?> type) {
        Deque<String> names = new ArrayDeque<>();
        Node current = type;
        while (current != null) {
            if (current instanceof TypeDeclaration<?> declaration) {
                names.addFirst(declaration.getNameAsString());
            }
            current = current.getParentNode().orElse(null);
        }
        return String.join(".", names);
    }

    private static boolean sameOwner(String left, String right) {
        return left.replace('$', '.').equals(right.replace('$', '.'));
    }

    private static void fail(
            String targetEnvDesc,
            RegistryEntry entry,
            SourceUnit unit,
            Node reference
    ) {
        int line = reference.getRange().map(r -> r.begin.line).orElse(1);
        throw new ExclusionConflictException(
                targetEnvDesc,
                entry.registryType().name().toUpperCase() + " " + entry.fullId(),
                entry.declaration(),
                unit.relativePath() + ":" + line
        );
    }

    private static boolean isInsideDeclaration(Node node, String fieldName) {
        Node current = node;
        while (current != null) {
            if (current instanceof FieldDeclaration field) {
                for (var variable : field.getVariables()) {
                    if (variable.getNameAsString().equals(fieldName)) {
                        return true;
                    }
                }
            }
            current = current.getParentNode().orElse(null);
        }
        return false;
    }
}
