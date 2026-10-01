package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.continuumlib.api.config.ClasspathArtifact;
import com.kyroxova.continuumlib.api.config.MappingRequest;
import com.kyroxova.continuumlib.filter.condition.TargetContext;
import com.kyroxova.continuumlib.filter.config.ContinuumProjectConfiguration;
import com.kyroxova.continuumlib.knowledge.rule.RulePack;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.Loader;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;

import java.nio.file.Path;
import java.util.*;

/**
 * Unified canonical resolved target representation.
 * Consolidates environment properties, artifact manifests, classpaths, mappings,
 * rule packs, isolated workspace, and project configuration.
 */
public record ResolvedTarget(
        String targetId,
        EnvironmentId sourceEnvironment,
        EnvironmentId targetEnvironment,
        String loaderVersion,
        String outputMode,
        Map<String, Path> sourceArtifacts,
        Map<String, Path> targetArtifacts,
        Map<String, ClasspathArtifact> sourceClasspath,
        Map<String, ClasspathArtifact> targetClasspath,
        MappingRequest sourceMapping,
        MappingRequest targetMapping,
        MappingNamespace outputNamespace,
        List<RulePack> rulePacks,
        GeneratedWorkspace workspace,
        ContinuumProjectConfiguration projectConfiguration
) {
    public ResolvedTarget {
        targetId = GeneratedWorkspace.validateTargetId(targetId);
        Objects.requireNonNull(sourceEnvironment, "sourceEnvironment");
        Objects.requireNonNull(targetEnvironment, "targetEnvironment");
        Objects.requireNonNull(workspace, "workspace");
        if (!workspace.targetId().equals(targetId)) {
            throw new IllegalArgumentException("Workspace target ID '" + workspace.targetId()
                    + "' does not match resolved target '" + targetId + "'");
        }
        if (outputMode == null || outputMode.isBlank()) {
            outputMode = "per_version";
        }
        sourceArtifacts = sourceArtifacts != null ? Map.copyOf(sourceArtifacts) : Map.of();
        targetArtifacts = targetArtifacts != null ? Map.copyOf(targetArtifacts) : Map.of();
        sourceClasspath = sourceClasspath != null ? Map.copyOf(sourceClasspath) : Map.of();
        targetClasspath = targetClasspath != null ? Map.copyOf(targetClasspath) : Map.of();
        rulePacks = rulePacks != null ? List.copyOf(rulePacks) : List.of();
    }

    public String minecraftVersion() {
        return targetEnvironment.minecraftVersion();
    }

    public Loader loader() {
        return targetEnvironment.loader();
    }

    public int javaVersion() {
        return targetEnvironment.javaVersion();
    }

    public MappingNamespace mappingNamespace() {
        return outputNamespace != null ? outputNamespace : targetEnvironment.mappings();
    }

    public TargetContext toTargetContext() {
        return new TargetContext(targetEnvironment, loaderVersion, outputMode);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String targetId;
        private EnvironmentId sourceEnvironment;
        private EnvironmentId targetEnvironment;
        private String loaderVersion;
        private String outputMode = "per_version";
        private final Map<String, Path> sourceArtifacts = new TreeMap<>();
        private final Map<String, Path> targetArtifacts = new TreeMap<>();
        private final Map<String, ClasspathArtifact> sourceClasspath = new TreeMap<>();
        private final Map<String, ClasspathArtifact> targetClasspath = new TreeMap<>();
        private MappingRequest sourceMapping;
        private MappingRequest targetMapping;
        private MappingNamespace outputNamespace;
        private final List<RulePack> rulePacks = new ArrayList<>();
        private GeneratedWorkspace workspace;
        private ContinuumProjectConfiguration projectConfiguration;

        public Builder targetId(String id) { this.targetId = id; return this; }
        public Builder sourceEnvironment(EnvironmentId env) { this.sourceEnvironment = env; return this; }
        public Builder targetEnvironment(EnvironmentId env) { this.targetEnvironment = env; return this; }
        public Builder loaderVersion(String ver) { this.loaderVersion = ver; return this; }
        public Builder outputMode(String mode) { this.outputMode = mode; return this; }
        public Builder sourceArtifacts(Map<String, Path> map) { if (map != null) this.sourceArtifacts.putAll(map); return this; }
        public Builder targetArtifacts(Map<String, Path> map) { if (map != null) this.targetArtifacts.putAll(map); return this; }
        public Builder sourceClasspath(Map<String, ClasspathArtifact> map) { if (map != null) this.sourceClasspath.putAll(map); return this; }
        public Builder targetClasspath(Map<String, ClasspathArtifact> map) { if (map != null) this.targetClasspath.putAll(map); return this; }
        public Builder sourceMapping(MappingRequest req) { this.sourceMapping = req; return this; }
        public Builder targetMapping(MappingRequest req) { this.targetMapping = req; return this; }
        public Builder outputNamespace(MappingNamespace ns) { this.outputNamespace = ns; return this; }
        public Builder addRulePack(RulePack pack) { if (pack != null) this.rulePacks.add(pack); return this; }
        public Builder rulePacks(Collection<RulePack> packs) { if (packs != null) this.rulePacks.addAll(packs); return this; }
        public Builder workspace(GeneratedWorkspace ws) { this.workspace = ws; return this; }
        public Builder projectConfiguration(ContinuumProjectConfiguration cfg) { this.projectConfiguration = cfg; return this; }

        public ResolvedTarget build() {
            return new ResolvedTarget(
                    targetId,
                    sourceEnvironment,
                    targetEnvironment,
                    loaderVersion,
                    outputMode,
                    sourceArtifacts,
                    targetArtifacts,
                    sourceClasspath,
                    targetClasspath,
                    sourceMapping,
                    targetMapping,
                    outputNamespace,
                    rulePacks,
                    workspace,
                    projectConfiguration
            );
        }
    }
}
