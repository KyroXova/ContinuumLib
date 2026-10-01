package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.continuumlib.api.config.OutputTargets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

/** Deterministic, isolated generated state for one ContinuumLib target. */
public record GeneratedWorkspace(
        Path root,
        Path sourceDirectory,
        Path resourcesDirectory,
        Path classesDirectory,
        Path reportsDirectory,
        Path metadataDirectory,
        Path outputDirectory,
        Path outputJar
) {
    public GeneratedWorkspace {
        root = normalize(root, "root");
        sourceDirectory = child(root, sourceDirectory, "sourceDirectory");
        resourcesDirectory = child(root, resourcesDirectory, "resourcesDirectory");
        classesDirectory = child(root, classesDirectory, "classesDirectory");
        reportsDirectory = child(root, reportsDirectory, "reportsDirectory");
        metadataDirectory = child(root, metadataDirectory, "metadataDirectory");
        outputDirectory = child(root, outputDirectory, "outputDirectory");
        outputJar = child(outputDirectory, outputJar, "outputJar");
    }

    public static GeneratedWorkspace under(Path continuumBuildDirectory, String targetId, String outputFileName) {
        Objects.requireNonNull(continuumBuildDirectory, "continuumBuildDirectory");
        OutputTargets.validateTargetId(targetId);
        Path outputName = Path.of(Objects.requireNonNull(outputFileName, "outputFileName")).normalize();
        if (outputName.isAbsolute() || outputName.getNameCount() != 1 || outputName.toString().isBlank()) {
            throw new IllegalArgumentException("Output JAR name must be a single filename: " + outputFileName);
        }

        Path root = continuumBuildDirectory.toAbsolutePath().normalize().resolve("targets").resolve(targetId).normalize();
        Path output = root.resolve("output");
        return new GeneratedWorkspace(
                root,
                root.resolve("source"),
                root.resolve("resources"),
                root.resolve("classes"),
                root.resolve("reports"),
                root.resolve("metadata"),
                output,
                output.resolve(outputName)
        );
    }

    /**
     * Adapts existing Gradle output properties into a workspace without forcing consumers to
     * immediately migrate custom task configuration. New defaults should still use {@link #under}.
     */
    public static GeneratedWorkspace fromOutputs(Path sourceDirectory, Path classesDirectory, Path outputJar) {
        Path source = normalize(sourceDirectory, "sourceDirectory");
        Path classes = normalize(classesDirectory, "classesDirectory");
        Path jar = normalize(outputJar, "outputJar");
        Path output = Objects.requireNonNull(jar.getParent(), "outputJar parent");
        Path root = commonAncestor(source, classes, output);
        Path generatedBase = Objects.requireNonNullElse(source.getParent(), root);
        return new GeneratedWorkspace(
                root,
                source,
                generatedBase.resolve("resources"),
                classes,
                generatedBase.resolve("reports"),
                generatedBase.resolve("metadata"),
                output,
                jar
        );
    }

    /**
     * Clears all target-generated transient state while deliberately preserving output/ so a failed
     * rebuild cannot destroy the last successfully packaged target artifact.
     */
    public void prepare() throws IOException {
        cleanDirectory(sourceDirectory);
        cleanDirectory(resourcesDirectory);
        cleanDirectory(classesDirectory);
        cleanDirectory(reportsDirectory);
        cleanDirectory(metadataDirectory);
        Files.createDirectories(sourceDirectory);
        Files.createDirectories(resourcesDirectory);
        Files.createDirectories(classesDirectory);
        Files.createDirectories(reportsDirectory);
        Files.createDirectories(metadataDirectory);
        Files.createDirectories(outputDirectory);
    }

    /**
     * Incremental compatibility preparation used while Gradle still declares source/classes/output
     * separately. Only those declared outputs are touched.
     */
    public void prepareCompilationOutputs() throws IOException {
        cleanDirectory(sourceDirectory);
        cleanDirectory(classesDirectory);
        Files.createDirectories(sourceDirectory);
        Files.createDirectories(classesDirectory);
        Files.createDirectories(outputDirectory);
    }

    private static void cleanDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (Stream<Path> stream = Files.walk(directory)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static Path commonAncestor(Path first, Path second, Path third) {
        Path candidate = first;
        while (candidate != null && (!second.startsWith(candidate) || !third.startsWith(candidate))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalArgumentException("Generated outputs do not share a filesystem root");
        }
        return candidate;
    }

    private static Path child(Path parent, Path child, String name) {
        Path normalized = normalize(child, name);
        if (!normalized.startsWith(parent)) {
            throw new IllegalArgumentException(name + " must be inside " + parent + ": " + normalized);
        }
        return normalized;
    }

    private static Path normalize(Path path, String name) {
        return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
    }
}
