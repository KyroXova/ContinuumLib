package com.kyroxova.continuumlib.source;

import com.kyroxova.continuumlib.source.ast.SourceParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
