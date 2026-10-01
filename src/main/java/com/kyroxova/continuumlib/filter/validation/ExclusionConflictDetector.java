package com.kyroxova.continuumlib.filter.validation;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.kyroxova.continuumlib.filter.condition.TargetContext;
import com.kyroxova.continuumlib.filter.registry.RegistryEntry;
import com.kyroxova.continuumlib.source.ast.SourceUnit;

import java.util.*;

/**
 * Detects whether active target-enabled source code still references an excluded declaration.
 */
public final class ExclusionConflictDetector {

    public void validate(TargetContext context,
                         List<RegistryEntry> excludedEntries,
                         Collection<SourceUnit> activeSourceUnits) {
        if (excludedEntries == null || excludedEntries.isEmpty() || activeSourceUnits == null) {
            return;
        }

        String targetEnvDesc = "Minecraft " + context.environmentId().minecraftVersion()
                + (context.loaderVersion() != null ? " (" + context.environmentId().loader() + " " + context.loaderVersion() + ")" : "");

        for (RegistryEntry entry : excludedEntries) {
            String ownerSimple = simpleName(entry.ownerClass());
            String fieldName = entry.fieldName();

            for (SourceUnit unit : activeSourceUnits) {
                CompilationUnit ast = unit.ast();
                boolean isDeclaringFile = unit.relativePath().replace('\\', '/').equals(entry.sourcePath().replace('\\', '/'));

                boolean importsOwner = false;
                boolean importsFieldStatic = false;

                for (ImportDeclaration imp : ast.getImports()) {
                    String impName = imp.getNameAsString();
                    if (imp.isStatic() && impName.equals(entry.ownerClass() + "." + fieldName)) {
                        importsFieldStatic = true;
                    } else if (impName.equals(entry.ownerClass())) {
                        importsOwner = true;
                    }
                }

                // Check field accesses: e.g. ModBlocks.TEST or com.example.ModBlocks.TEST
                for (FieldAccessExpr fa : ast.findAll(FieldAccessExpr.class)) {
                    if (fa.getNameAsString().equals(fieldName)) {
                        String scope = fa.getScope().toString();
                        if (scope.equals(ownerSimple) || scope.equals(entry.ownerClass()) || importsOwner) {
                            // If this is the declaring file, make sure it is not the declaration itself
                            if (!isDeclaringFile || !isInsideDeclaration(fa, fieldName)) {
                                int line = fa.getRange().map(r -> r.begin.line).orElse(1);
                                String refLoc = unit.relativePath() + ":" + line;
                                throw new ExclusionConflictException(
                                        targetEnvDesc,
                                        entry.registryType().name().toUpperCase() + " " + entry.fullId(),
                                        entry.declaration(),
                                        refLoc
                                );
                            }
                        }
                    }
                }

                // Check static import direct references: e.g. TEST.get()
                if (importsFieldStatic) {
                    for (NameExpr ne : ast.findAll(NameExpr.class)) {
                        if (ne.getNameAsString().equals(fieldName)) {
                            if (!isDeclaringFile || !isInsideDeclaration(ne, fieldName)) {
                                int line = ne.getRange().map(r -> r.begin.line).orElse(1);
                                String refLoc = unit.relativePath() + ":" + line;
                                throw new ExclusionConflictException(
                                        targetEnvDesc,
                                        entry.registryType().name().toUpperCase() + " " + entry.fullId(),
                                        entry.declaration(),
                                        refLoc
                                );
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isInsideDeclaration(Node node, String fieldName) {
        Node curr = node;
        while (curr != null) {
            if (curr instanceof FieldDeclaration fd) {
                for (var v : fd.getVariables()) {
                    if (v.getNameAsString().equals(fieldName)) {
                        return true;
                    }
                }
            }
            curr = curr.getParentNode().orElse(null);
        }
        return false;
    }

    private static String simpleName(String qualified) {
        int dot = qualified.lastIndexOf('.');
        return dot >= 0 ? qualified.substring(dot + 1) : qualified;
    }
}
