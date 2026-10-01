package com.kyroxova.continuumlib.pipeline.config;

import com.kyroxova.continuumlib.model.diagnostic.Diagnostic;
import com.kyroxova.continuumlib.model.diagnostic.DiagnosticCode;
import com.kyroxova.continuumlib.model.diagnostic.Severity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Unified project configuration discovery service.
 * Finds canonical config under src/main/resources/continuumlib,
 * detects legacy config under src/main/resources/data/continuumlib,
 * and detects conflicting dual configuration.
 */
public final class ProjectConfigurationLocator {
    public static final String CANONICAL_PATH = "src/main/resources/continuumlib";
    public static final String LEGACY_PATH = "src/main/resources/data/continuumlib";

    public record DiscoveredConfiguration(
            Path root,
            boolean isLegacy,
            Path targetsFile,
            Path targetsDir,
            Path transformFile,
            Path knowledgeDir,
            Path inclusionsDir,
            Path exclusionsDir,
            List<Diagnostic> diagnostics
    ) {
        public boolean exists() {
            return root != null && Files.isDirectory(root);
        }
    }

    public DiscoveredConfiguration locate(Path projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Path canonical = projectRoot.resolve(CANONICAL_PATH).toAbsolutePath().normalize();
        Path legacy = projectRoot.resolve(LEGACY_PATH).toAbsolutePath().normalize();

        boolean canonicalHasFiles = hasConfigFiles(canonical);
        boolean legacyHasFiles = hasConfigFiles(legacy);

        List<Diagnostic> diagnostics = new ArrayList<>();

        if (canonicalHasFiles && legacyHasFiles) {
            String msg = "DUAL CONFIGURATION CONFLICT: ContinuumLib configuration detected in both canonical '"
                    + CANONICAL_PATH + "' and legacy '" + LEGACY_PATH + "'. Please consolidate configuration into '" + CANONICAL_PATH + "'.";
            Diagnostic diag = Diagnostic.builder()
                    .code(DiagnosticCode.DUAL_CONFIGURATION_CONFLICT)
                    .severity(Severity.ERROR)
                    .message(msg)
                    .path(canonical)
                    .build();
            diagnostics.add(diag);
            throw new DualConfigurationException(msg, diagnostics);
        }

        Path selectedRoot;
        boolean isLegacy = false;

        if (canonicalHasFiles || Files.isDirectory(canonical)) {
            selectedRoot = canonical;
        } else if (legacyHasFiles || Files.isDirectory(legacy)) {
            selectedRoot = legacy;
            isLegacy = true;
        } else {
            selectedRoot = canonical;
        }

        Path targetsFile = selectedRoot.resolve("targets.properties");
        Path targetsDir = selectedRoot.resolve("targets");
        Path transformFile = selectedRoot.resolve("transform.properties");
        Path knowledgeDir = selectedRoot.resolve("knowledge");
        Path inclusionsDir = selectedRoot.resolve("inclusions");
        Path exclusionsDir = selectedRoot.resolve("exclusions");

        return new DiscoveredConfiguration(
                selectedRoot,
                isLegacy,
                targetsFile,
                targetsDir,
                transformFile,
                knowledgeDir,
                inclusionsDir,
                exclusionsDir,
                List.copyOf(diagnostics)
        );
    }

    private static boolean hasConfigFiles(Path dir) {
        if (!Files.isDirectory(dir)) return false;
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.anyMatch(p -> {
                if (Files.isRegularFile(p)) {
                    String name = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    return name.endsWith(".properties") || name.endsWith(".json") || name.endsWith(".xml");
                }
                return false;
            });
        } catch (IOException e) {
            return false;
        }
    }

    public static final class DualConfigurationException extends RuntimeException {
        private final List<Diagnostic> diagnostics;

        public DualConfigurationException(String message, List<Diagnostic> diagnostics) {
            super(message);
            this.diagnostics = List.copyOf(diagnostics);
        }

        public List<Diagnostic> diagnostics() {
            return diagnostics;
        }
    }
}
