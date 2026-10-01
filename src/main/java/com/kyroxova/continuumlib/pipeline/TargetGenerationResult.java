package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.continuumlib.bytecode.TargetReferenceAudit;
import com.kyroxova.continuumlib.filter.registry.RegistryEntry;
import com.kyroxova.continuumlib.model.diagnostic.Diagnostic;
import com.kyroxova.continuumlib.model.diagnostic.Severity;
import com.kyroxova.continuumlib.pipeline.migration.AppliedMigration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Structured, inspectable result of running the target generation pipeline on a resolved target.
 */
public record TargetGenerationResult(
        ResolvedTarget resolvedTarget,
        GeneratedWorkspace workspace,
        List<Path> discoveredSourceFiles,
        List<Path> includedSourceFiles,
        List<Path> excludedSourceFiles,
        Map<String, Path> discoveredResources,
        Map<String, Path> includedResources,
        Map<String, Path> excludedResources,
        List<RegistryEntry> excludedRegistryEntries,
        List<AppliedMigration> appliedMigrations,
        boolean compilationSuccess,
        int compiledClassesCount,
        int bytecodeAdaptedCount,
        List<TargetReferenceAudit.Finding> auditFindings,
        List<Diagnostic> diagnostics,
        Path outputJar
) {
    public TargetGenerationResult {
        Objects.requireNonNull(resolvedTarget, "resolvedTarget");
        Objects.requireNonNull(workspace, "workspace");
        discoveredSourceFiles = List.copyOf(discoveredSourceFiles);
        includedSourceFiles = List.copyOf(includedSourceFiles);
        excludedSourceFiles = List.copyOf(excludedSourceFiles);
        discoveredResources = Map.copyOf(discoveredResources);
        includedResources = Map.copyOf(includedResources);
        excludedResources = Map.copyOf(excludedResources);
        excludedRegistryEntries = List.copyOf(excludedRegistryEntries);
        appliedMigrations = List.copyOf(appliedMigrations);
        auditFindings = List.copyOf(auditFindings);
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean isSuccess() {
        return compilationSuccess
                && outputJar != null
                && Files.exists(outputJar)
                && diagnostics.stream().noneMatch(d -> d.severity() == Severity.ERROR);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ResolvedTarget resolvedTarget;
        private GeneratedWorkspace workspace;
        private final List<Path> discoveredSourceFiles = new ArrayList<>();
        private final List<Path> includedSourceFiles = new ArrayList<>();
        private final List<Path> excludedSourceFiles = new ArrayList<>();
        private final Map<String, Path> discoveredResources = new TreeMap<>();
        private final Map<String, Path> includedResources = new TreeMap<>();
        private final Map<String, Path> excludedResources = new TreeMap<>();
        private final List<RegistryEntry> excludedRegistryEntries = new ArrayList<>();
        private final List<AppliedMigration> appliedMigrations = new ArrayList<>();
        private boolean compilationSuccess;
        private int compiledClassesCount;
        private int bytecodeAdaptedCount;
        private final List<TargetReferenceAudit.Finding> auditFindings = new ArrayList<>();
        private final List<Diagnostic> diagnostics = new ArrayList<>();
        private Path outputJar;

        public Builder resolvedTarget(ResolvedTarget target) { this.resolvedTarget = target; return this; }
        public Builder workspace(GeneratedWorkspace ws) { this.workspace = ws; return this; }
        public Builder discoveredSourceFiles(Collection<Path> paths) { this.discoveredSourceFiles.addAll(paths); return this; }
        public Builder includedSourceFiles(Collection<Path> paths) { this.includedSourceFiles.addAll(paths); return this; }
        public Builder excludedSourceFiles(Collection<Path> paths) { this.excludedSourceFiles.addAll(paths); return this; }
        public Builder discoveredResources(Map<String, Path> map) { this.discoveredResources.putAll(map); return this; }
        public Builder includedResources(Map<String, Path> map) { this.includedResources.putAll(map); return this; }
        public Builder excludedResources(Map<String, Path> map) { this.excludedResources.putAll(map); return this; }
        public Builder excludedRegistryEntries(Collection<RegistryEntry> entries) { this.excludedRegistryEntries.addAll(entries); return this; }
        public Builder appliedMigrations(Collection<AppliedMigration> list) { this.appliedMigrations.addAll(list); return this; }
        public Builder compilationSuccess(boolean success) { this.compilationSuccess = success; return this; }
        public Builder compiledClassesCount(int count) { this.compiledClassesCount = count; return this; }
        public Builder bytecodeAdaptedCount(int count) { this.bytecodeAdaptedCount = count; return this; }
        public Builder auditFindings(Collection<TargetReferenceAudit.Finding> findings) { this.auditFindings.addAll(findings); return this; }
        public Builder diagnostics(Collection<Diagnostic> diags) { this.diagnostics.addAll(diags); return this; }
        public Builder addDiagnostic(Diagnostic diag) { this.diagnostics.add(diag); return this; }
        public Builder outputJar(Path jar) { this.outputJar = jar; return this; }

        public TargetGenerationResult build() {
            return new TargetGenerationResult(
                    resolvedTarget,
                    workspace,
                    discoveredSourceFiles,
                    includedSourceFiles,
                    excludedSourceFiles,
                    discoveredResources,
                    includedResources,
                    excludedResources,
                    excludedRegistryEntries,
                    appliedMigrations,
                    compilationSuccess,
                    compiledClassesCount,
                    bytecodeAdaptedCount,
                    auditFindings,
                    diagnostics,
                    outputJar
            );
        }
    }
}
