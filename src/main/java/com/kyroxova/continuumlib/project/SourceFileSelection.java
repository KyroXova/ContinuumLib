package com.kyroxova.continuumlib.project;

import com.kyroxova.continuumlib.filter.condition.TargetContext;
import com.kyroxova.continuumlib.filter.config.ContinuumProjectConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Performs target-aware source-file selection before Java parsing.
 * Class and registry filtering deliberately remain later semantic stages.
 */
public record SourceFileSelection(List<Path> activeFiles, List<Path> excludedFiles) {
    public SourceFileSelection {
        activeFiles = List.copyOf(Objects.requireNonNull(activeFiles, "activeFiles"));
        excludedFiles = List.copyOf(Objects.requireNonNull(excludedFiles, "excludedFiles"));
    }

    public static SourceFileSelection discover(Path sourceRoot,
                                               TargetContext context,
                                               ContinuumProjectConfiguration configuration) throws IOException {
        Objects.requireNonNull(sourceRoot, "sourceRoot");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(configuration, "configuration");

        Path root = sourceRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return new SourceFileSelection(List.of(), List.of());
        }

        var activeExclusions = configuration.exclusions().filterFor(context);
        // Inclusion rule parsing is retained, but explicit include-only semantics are intentionally not activated yet.
        configuration.inclusions().filterFor(context);

        List<Path> active = new ArrayList<>();
        List<Path> excluded = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path file : stream
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted()
                    .toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (activeExclusions.rules().matchesSource(relative)) {
                    excluded.add(file.toAbsolutePath().normalize());
                } else {
                    active.add(file.toAbsolutePath().normalize());
                }
            }
        }

        return new SourceFileSelection(active, excluded);
    }
}
