package com.kyroxova.continuumlib.project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Owns discovery of consumer ContinuumLib configuration locations.
 * The canonical root is preferred while the legacy data/continuumlib root remains a compatibility fallback.
 */
public record ProjectConfigurationLayout(Path canonicalRoot, Path legacyRoot) {
    public ProjectConfigurationLayout {
        canonicalRoot = normalize(canonicalRoot, "canonicalRoot");
        legacyRoot = normalize(legacyRoot, "legacyRoot");
    }

    public static ProjectConfigurationLayout discover(Path projectRoot) {
        Path root = normalize(projectRoot, "projectRoot");
        return new ProjectConfigurationLayout(
                root.resolve("src/main/resources/continuumlib"),
                root.resolve("src/main/resources/data/continuumlib")
        );
    }

    /**
     * Returns the root used for inclusion/exclusion configuration.
     * Defining filters in both roots is rejected instead of merging ambiguous project intent.
     */
    public Path filterConfigurationRoot() {
        boolean canonical = hasFilterConfiguration(canonicalRoot);
        boolean legacy = hasFilterConfiguration(legacyRoot);
        if (canonical && legacy) {
            throw new IllegalStateException("ContinuumLib filter configuration is defined in both "
                    + canonicalRoot + " and " + legacyRoot + "; move project filters to the canonical root");
        }
        if (canonical) return canonicalRoot;
        if (legacy) return legacyRoot;
        return canonicalRoot;
    }

    public Path resolveFile(String relativePath) {
        return resolve(relativePath, Files::isRegularFile, "file");
    }

    public Path resolveDirectory(String relativePath) {
        return resolve(relativePath, Files::isDirectory, "directory");
    }

    private Path resolve(String relativePath, Predicate<Path> exists, String kind) {
        Path relative = safeRelative(relativePath);
        Path canonical = canonicalRoot.resolve(relative).normalize();
        Path legacy = legacyRoot.resolve(relative).normalize();
        boolean canonicalExists = exists.test(canonical);
        boolean legacyExists = exists.test(legacy);
        if (canonicalExists && legacyExists) {
            throw new IllegalStateException("ContinuumLib " + kind + " '" + relativePath
                    + "' is defined in both " + canonicalRoot + " and " + legacyRoot);
        }
        if (canonicalExists) return canonical;
        if (legacyExists) return legacy;
        return canonical;
    }

    private static boolean hasFilterConfiguration(Path root) {
        return Files.isDirectory(root.resolve("inclusions")) || Files.isDirectory(root.resolve("exclusions"));
    }

    private static Path safeRelative(String value) {
        Objects.requireNonNull(value, "relativePath");
        Path path = Path.of(value).normalize();
        if (path.isAbsolute() || path.toString().isBlank() || path.startsWith("..")) {
            throw new IllegalArgumentException("Configuration path must be project-relative: " + value);
        }
        return path;
    }

    private static Path normalize(Path path, String name) {
        return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
    }
}
