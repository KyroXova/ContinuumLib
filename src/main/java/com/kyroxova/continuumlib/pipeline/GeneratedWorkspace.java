package com.kyroxova.continuumlib.pipeline;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public final class GeneratedWorkspace {
    private final String targetId;
    private final Path rootDir;
    private final Path sourceDir;
    private final Path resourcesDir;
    private final Path classesDir;
    private final Path reportsDir;
    private final Path metadataDir;
    private final Path stagingDir;
    private final Path outputDir;

    public GeneratedWorkspace(Path buildRoot, String targetId) {
        this(validateTargetId(targetId), workspaceRoot(buildRoot, validateTargetId(targetId)));
    }

    private static Path workspaceRoot(Path buildRoot, String targetId) {
        return Objects.requireNonNull(buildRoot, "buildRoot")
                .resolve("continuum/targets")
                .resolve(targetId)
                .toAbsolutePath()
                .normalize();
    }

    private GeneratedWorkspace(String targetId, Path rootDir) {
        this.targetId = validateTargetId(targetId);
        this.rootDir = Objects.requireNonNull(rootDir, "rootDir").toAbsolutePath().normalize();
        this.sourceDir = this.rootDir.resolve("source");
        this.resourcesDir = this.rootDir.resolve("resources");
        this.classesDir = this.rootDir.resolve("classes");
        this.reportsDir = this.rootDir.resolve("reports");
        this.metadataDir = this.rootDir.resolve("metadata");
        this.stagingDir = this.rootDir.resolve("staging");
        this.outputDir = this.rootDir.resolve("output");
    }

    public static GeneratedWorkspace atTargetRoot(Path targetRoot, String targetId) {
        return new GeneratedWorkspace(validateTargetId(targetId), targetRoot);
    }

    public static String validateTargetId(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("Target ID cannot be blank");
        }
        String trimmed = targetId.trim();
        String base = trimmed.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
        if (!trimmed.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}") || base.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) {
            throw new IllegalArgumentException("Unsafe target ID: " + trimmed);
        }
        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..")) {
            throw new IllegalArgumentException("Unsafe path traversal in target ID: " + trimmed);
        }
        return trimmed;
    }

    public void init() throws IOException {
        Files.createDirectories(sourceDir);
        Files.createDirectories(resourcesDir);
        Files.createDirectories(classesDir);
        Files.createDirectories(reportsDir);
        Files.createDirectories(metadataDir);
        Files.createDirectories(stagingDir);
        Files.createDirectories(outputDir);
    }

    public void prepare() throws IOException {
        clean(sourceDir);
        clean(resourcesDir);
        clean(classesDir);
        clean(reportsDir);
        clean(metadataDir);
        clean(stagingDir);
        init();
    }

    public String targetId() { return targetId; }
    public Path rootDir() { return rootDir; }
    public Path sourceDir() { return sourceDir; }
    public Path resourcesDir() { return resourcesDir; }
    public Path classesDir() { return classesDir; }
    public Path reportsDir() { return reportsDir; }
    public Path metadataDir() { return metadataDir; }
    public Path stagingDir() { return stagingDir; }
    public Path outputDir() { return outputDir; }

    public Path stagingJar(String jarName) {
        return stagingDir.resolve(validateArtifactName(jarName));
    }

    public Path finalJar(String jarName) {
        return outputDir.resolve(validateArtifactName(jarName));
    }

    private static String validateArtifactName(String jarName) {
        Objects.requireNonNull(jarName, "jarName");
        Path candidate = Path.of(jarName).normalize();
        if (candidate.isAbsolute()
                || candidate.getNameCount() != 1
                || candidate.toString().isBlank()
                || candidate.toString().equals(".")
                || candidate.toString().equals("..")) {
            throw new IllegalArgumentException("Output artifact name must be a single filename: " + jarName);
        }
        String base = candidate.toString().split("\\.", 2)[0].toUpperCase(Locale.ROOT);
        if (base.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) {
            throw new IllegalArgumentException("Unsafe output artifact name: " + jarName);
        }
        return candidate.toString();
    }

    public Path finalizeJar(Path stagedJar, String jarName) throws IOException {
        Objects.requireNonNull(stagedJar, "stagedJar");
        Path normalizedStaged = stagedJar.toAbsolutePath().normalize();
        Path normalizedStagingDir = stagingDir.toAbsolutePath().normalize();
        if (!normalizedStaged.startsWith(normalizedStagingDir)) {
            throw new IOException("Staged JAR must be inside this target workspace: " + stagedJar);
        }
        if (Files.isSymbolicLink(normalizedStaged)) {
            throw new IOException("Staged JAR must not be a symbolic link: " + stagedJar);
        }
        if (!Files.exists(normalizedStaged) || !Files.isRegularFile(normalizedStaged)) {
            throw new IOException("Staged JAR does not exist: " + stagedJar);
        }

        Path realStagingDir = normalizedStagingDir.toRealPath();
        Path realStaged = normalizedStaged.toRealPath();
        if (!realStaged.startsWith(realStagingDir)) {
            throw new IOException("Staged JAR resolves outside this target workspace: "
                    + stagedJar + " -> " + realStaged);
        }

        Path finalDest = finalJar(jarName);
        Files.createDirectories(finalDest.getParent());
        try {
            Files.move(normalizedStaged, finalDest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(normalizedStaged, finalDest, StandardCopyOption.REPLACE_EXISTING);
        }
        return finalDest;
    }

    public void cleanStaging() throws IOException {
        clean(stagingDir);
        Files.createDirectories(stagingDir);
    }

    private static void clean(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (Stream<Path> stream = Files.walk(directory)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                if (!path.equals(directory)) Files.deleteIfExists(path);
            }
        }
    }
}
