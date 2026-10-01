package com.kyroxova.continuumlib.structure;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryLayoutTest {
    private static final Path ROOT = Path.of("").toAbsolutePath().normalize();

    @Test
    void prototypeIsBackedUpOutsideTheActiveSourceTree() throws IOException {
        assertTrue(Files.isDirectory(ROOT.resolve("backup/src/main")));
        assertTrue(Files.isDirectory(ROOT.resolve("backup/src/test")));
        assertTrue(Files.isDirectory(ROOT.resolve("src/main/java/com/kyroxova/continuumlib")));
        assertTrue(Files.isDirectory(ROOT.resolve("src/test/java/com/kyroxova/continuumlib")));

        try (Stream<Path> activeFiles = Files.walk(ROOT.resolve("src"))) {
            assertFalse(activeFiles.anyMatch(path -> path.normalize().startsWith(ROOT.resolve("backup"))));
        }
    }
}
