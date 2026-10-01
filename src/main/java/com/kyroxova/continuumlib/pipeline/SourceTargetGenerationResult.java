package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.continuumlib.filter.registry.RegistryEntry;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Inspectable result of the source-generation portion of one target build. */
public record SourceTargetGenerationResult(
        ResolvedTarget target,
        GeneratedWorkspace workspace,
        List<Path> generatedSources,
        List<Path> excludedBeforeParsing,
        List<RegistryEntry> excludedRegistryEntries,
        Map<String, Path> activeResources,
        Map<String, Path> excludedResources
) {
    public SourceTargetGenerationResult {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(workspace, "workspace");
        generatedSources = List.copyOf(Objects.requireNonNull(generatedSources, "generatedSources"));
        excludedBeforeParsing = List.copyOf(Objects.requireNonNull(excludedBeforeParsing, "excludedBeforeParsing"));
        excludedRegistryEntries = List.copyOf(Objects.requireNonNull(excludedRegistryEntries, "excludedRegistryEntries"));
        activeResources = Map.copyOf(Objects.requireNonNull(activeResources, "activeResources"));
        excludedResources = Map.copyOf(Objects.requireNonNull(excludedResources, "excludedResources"));
    }
}
