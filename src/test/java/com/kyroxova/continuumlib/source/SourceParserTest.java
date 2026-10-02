package com.kyroxova.continuumlib.source;

import com.github.javaparser.ast.expr.MethodCallExpr;
import com.kyroxova.continuumlib.source.ast.SourceParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceParserTest {
    @Test
    void choosesMostSpecificSourceRootForRelativePath(@TempDir Path project) throws Exception {
        Path root = project.resolve("src/main/java");
        Path nested = root.resolve("generated");
        Path source = nested.resolve("example/Nested.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package example; public class Nested {}");

        var units = new SourceParser(List.of(root, nested), List.of())
                .parseFiles(List.of(source), List.of(root, nested));

        assertEquals(1, units.size());
        assertEquals("example/Nested.java", units.get(0).relativePath());
    }

    @Test
    void sourceTypeSolverUsesConfiguredJavaLevel(@TempDir Path project) throws Exception {
        Path root = project.resolve("src/main/java");
        Path record = root.resolve("api/Pair.java");
        Path use = root.resolve("example/Use.java");
        Files.createDirectories(record.getParent());
        Files.createDirectories(use.getParent());

        Files.writeString(record, """
                package api;
                public record Pair(int left, int right) {}
                """);
        Files.writeString(use, """
                package example;
                import api.Pair;
                public class Use {
                    public int value() { return new Pair(1, 2).left(); }
                }
                """);

        var units = new SourceParser(List.of(root), List.of(), 17).parseDirectory(root);
        var useUnit = units.stream()
                .filter(unit -> unit.relativePath().equals("example/Use.java"))
                .findFirst()
                .orElseThrow();

        var call = useUnit.ast().findAll(MethodCallExpr.class).stream()
                .filter(method -> method.getNameAsString().equals("left"))
                .findFirst()
                .orElseThrow();

        assertEquals("api.Pair", call.resolve().declaringType().getQualifiedName());
        assertTrue(units.stream().anyMatch(unit -> unit.relativePath().equals("api/Pair.java")));
    }
}
