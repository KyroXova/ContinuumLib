package com.kyroxova.continuumlib.artifact;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public final class ArtifactPublisher {
    private ArtifactPublisher() {
    }

    public static void publish(Path source, Path destination) throws IOException {
        Path input = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
        Path output = Objects.requireNonNull(destination, "destination").toAbsolutePath().normalize();

        if (!Files.isRegularFile(input)) {
            throw new IOException("Generated artifact does not exist: " + input);
        }
        if (input.equals(output)) return;

        Path parent = output.getParent();
        if (parent == null) {
            throw new IOException("Output artifact must have a parent directory: " + output);
        }
        Files.createDirectories(parent);

        Path temporary = Files.createTempFile(parent, "." + output.getFileName() + "-", ".tmp");
        try {
            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(
                        temporary,
                        output,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException unavailable) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
