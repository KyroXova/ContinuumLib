package com.kyroxova.continuumlib.artifact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactPublisherTest {
    @Test
    void replacesOutputOnlyAfterSourceExists(@TempDir Path root) throws Exception {
        Path source = root.resolve("workspace/target.jar");
        Path output = root.resolve("dist/target.jar");
        Files.createDirectories(source.getParent());
        Files.createDirectories(output.getParent());
        Files.writeString(source, "new-artifact");
        Files.writeString(output, "last-good");

        ArtifactPublisher.publish(source, output);

        assertEquals("new-artifact", Files.readString(output));
        assertEquals("new-artifact", Files.readString(source));
        try (var children = Files.list(output.getParent())) {
            assertEquals(1, children.count());
        }
    }

    @Test
    void missingSourceDoesNotTouchLastGoodOutput(@TempDir Path root) throws Exception {
        Path missing = root.resolve("missing.jar");
        Path output = root.resolve("dist/target.jar");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "last-good");

        assertThrows(IOException.class, () -> ArtifactPublisher.publish(missing, output));

        assertEquals("last-good", Files.readString(output));
    }
}
