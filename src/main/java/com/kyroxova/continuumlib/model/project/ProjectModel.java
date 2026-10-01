package com.kyroxova.continuumlib.model.project;

import com.kyroxova.continuumlib.filter.config.ContinuumProjectConfiguration;
import com.kyroxova.continuumlib.filter.registry.RegistryEntry;
import com.kyroxova.continuumlib.model.diagnostic.Diagnostic;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.operation.SemanticOperation;
import com.kyroxova.continuumlib.source.ast.SourceUnit;

import java.nio.file.Path;
import java.util.*;

/**
 * Canonical project representation across discovery, filtering, AST transformation, and compilation.
 * Maintains immutable staged state without god-object mutability.
 */
public record ProjectModel(
        Path projectRoot,
        List<Path> sourceRoots,
        List<Path> resourceRoots,
        List<Path> candidateSourceFiles,
        List<Path> activeSourceFiles,
        List<Path> excludedSourceFiles,
        Map<String, Path> candidateResources,
        Map<String, Path> activeResources,
        Map<String, Path> excludedResources,
        List<SourceUnit> sourceUnits,
        List<RegistryEntry> registryEntries,
        List<RegistryEntry> excludedRegistryEntries,
        ContinuumProjectConfiguration configuration,
        EnvironmentId sourceEnvironment,
        EnvironmentId targetEnvironment,
        List<SemanticOperation> operations,
        List<Diagnostic> diagnostics
) {
    public ProjectModel(List<SemanticOperation> operations, List<Diagnostic> diagnostics) {
        this(
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                operations,
                diagnostics
        );
    }

    public ProjectModel {
        sourceRoots = sourceRoots != null ? List.copyOf(sourceRoots) : List.of();
        resourceRoots = resourceRoots != null ? List.copyOf(resourceRoots) : List.of();
        candidateSourceFiles = candidateSourceFiles != null ? List.copyOf(candidateSourceFiles) : List.of();
        activeSourceFiles = activeSourceFiles != null ? List.copyOf(activeSourceFiles) : List.of();
        excludedSourceFiles = excludedSourceFiles != null ? List.copyOf(excludedSourceFiles) : List.of();
        candidateResources = candidateResources != null ? Map.copyOf(candidateResources) : Map.of();
        activeResources = activeResources != null ? Map.copyOf(activeResources) : Map.of();
        excludedResources = excludedResources != null ? Map.copyOf(excludedResources) : Map.of();
        sourceUnits = sourceUnits != null ? List.copyOf(sourceUnits) : List.of();
        registryEntries = registryEntries != null ? List.copyOf(registryEntries) : List.of();
        excludedRegistryEntries = excludedRegistryEntries != null ? List.copyOf(excludedRegistryEntries) : List.of();
        operations = operations != null ? List.copyOf(operations) : List.of();
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
    }

    public <T extends SemanticOperation> List<T> operations(Class<T> type) {
        return operations.stream().filter(type::isInstance).map(type::cast).toList();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Path projectRoot;
        private final List<Path> sourceRoots = new ArrayList<>();
        private final List<Path> resourceRoots = new ArrayList<>();
        private final List<Path> candidateSourceFiles = new ArrayList<>();
        private final List<Path> activeSourceFiles = new ArrayList<>();
        private final List<Path> excludedSourceFiles = new ArrayList<>();
        private final Map<String, Path> candidateResources = new TreeMap<>();
        private final Map<String, Path> activeResources = new TreeMap<>();
        private final Map<String, Path> excludedResources = new TreeMap<>();
        private final List<SourceUnit> sourceUnits = new ArrayList<>();
        private final List<RegistryEntry> registryEntries = new ArrayList<>();
        private final List<RegistryEntry> excludedRegistryEntries = new ArrayList<>();
        private ContinuumProjectConfiguration configuration;
        private EnvironmentId sourceEnvironment;
        private EnvironmentId targetEnvironment;
        private final List<SemanticOperation> operations = new ArrayList<>();
        private final List<Diagnostic> diagnostics = new ArrayList<>();

        public Builder projectRoot(Path path) { this.projectRoot = path; return this; }
        public Builder addSourceRoot(Path path) { this.sourceRoots.add(path); return this; }
        public Builder addResourceRoot(Path path) { this.resourceRoots.add(path); return this; }
        public Builder candidateSourceFiles(Collection<Path> paths) { this.candidateSourceFiles.addAll(paths); return this; }
        public Builder activeSourceFiles(Collection<Path> paths) { this.activeSourceFiles.addAll(paths); return this; }
        public Builder excludedSourceFiles(Collection<Path> paths) { this.excludedSourceFiles.addAll(paths); return this; }
        public Builder candidateResources(Map<String, Path> map) { this.candidateResources.putAll(map); return this; }
        public Builder activeResources(Map<String, Path> map) { this.activeResources.putAll(map); return this; }
        public Builder excludedResources(Map<String, Path> map) { this.excludedResources.putAll(map); return this; }
        public Builder sourceUnits(Collection<SourceUnit> units) { this.sourceUnits.addAll(units); return this; }
        public Builder registryEntries(Collection<RegistryEntry> entries) { this.registryEntries.addAll(entries); return this; }
        public Builder excludedRegistryEntries(Collection<RegistryEntry> entries) { this.excludedRegistryEntries.addAll(entries); return this; }
        public Builder configuration(ContinuumProjectConfiguration config) { this.configuration = config; return this; }
        public Builder sourceEnvironment(EnvironmentId env) { this.sourceEnvironment = env; return this; }
        public Builder targetEnvironment(EnvironmentId env) { this.targetEnvironment = env; return this; }
        public Builder operations(Collection<SemanticOperation> ops) { this.operations.addAll(ops); return this; }
        public Builder addDiagnostic(Diagnostic diag) { this.diagnostics.add(diag); return this; }
        public Builder diagnostics(Collection<Diagnostic> diags) { this.diagnostics.addAll(diags); return this; }

        public ProjectModel build() {
            return new ProjectModel(
                    projectRoot,
                    sourceRoots,
                    resourceRoots,
                    candidateSourceFiles,
                    activeSourceFiles,
                    excludedSourceFiles,
                    candidateResources,
                    activeResources,
                    excludedResources,
                    sourceUnits,
                    registryEntries,
                    excludedRegistryEntries,
                    configuration,
                    sourceEnvironment,
                    targetEnvironment,
                    operations,
                    diagnostics
            );
        }
    }
}
