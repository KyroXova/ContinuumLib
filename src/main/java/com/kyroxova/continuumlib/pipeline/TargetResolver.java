package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.continuumlib.api.config.TransformRequest;
import com.kyroxova.continuumlib.filter.config.ContinuumProjectConfiguration;
import com.kyroxova.continuumlib.filter.config.FilterConfigurationReader;
import com.kyroxova.continuumlib.knowledge.rule.BuiltinRulePacks;
import com.kyroxova.continuumlib.knowledge.rule.RulePack;
import com.kyroxova.continuumlib.knowledge.rule.RuleCatalog;
import com.kyroxova.continuumlib.pipeline.config.ProjectConfigurationLocator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class TargetResolver {
    private final ProjectConfigurationLocator locator = new ProjectConfigurationLocator();
    private final FilterConfigurationReader filterReader = new FilterConfigurationReader();

    public ResolvedTarget resolve(
            Path projectRoot,
            String targetId,
            Path buildRoot,
            List<RulePack> availablePacks
    ) throws IOException {
        return resolve(projectRoot, targetId, buildRoot, availablePacks, null);
    }

    public ResolvedTarget resolve(
            Path projectRoot,
            String targetId,
            Path buildRoot,
            List<RulePack> availablePacks,
            Path explicitTargetConfig
    ) throws IOException {
        return resolve(projectRoot, targetId, buildRoot, availablePacks, explicitTargetConfig, null);
    }

    public ResolvedTarget resolve(
            Path projectRoot,
            String targetId,
            Path buildRoot,
            List<RulePack> availablePacks,
            Path explicitTargetConfig,
            GeneratedWorkspace explicitWorkspace
    ) throws IOException {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(buildRoot, "buildRoot");

        var config = locator.locate(projectRoot);
        Path targetConfigFile = explicitTargetConfig != null
                ? explicitTargetConfig.toAbsolutePath().normalize()
                : discoverTargetConfig(config, targetId);

        if (!Files.isRegularFile(targetConfigFile)) {
            throw new IOException("Cannot locate target configuration for '" + targetId + "': " + targetConfigFile);
        }

        TransformRequest req = TransformRequest.read(targetConfigFile, projectRoot);

        List<RulePack> allPacks = availablePacks == null
                ? new ArrayList<>(BuiltinRulePacks.load())
                : new ArrayList<>(availablePacks);
        new RuleCatalog(allPacks);

        RulePack selected = allPacks.stream()
                .filter(pack -> pack.id().equals(req.packId()))
                .findFirst()
                .orElseThrow(() -> new IOException("No rule pack found matching id: " + req.packId()));

        List<RulePack> route = allPacks.stream()
                .filter(pack -> pack.source().equals(selected.source()) && pack.target().equals(selected.target()))
                .sorted(Comparator.comparing(RulePack::id))
                .toList();

        ContinuumProjectConfiguration projectConfig = filterReader.load(config.root());
        GeneratedWorkspace workspace = explicitWorkspace != null
                ? explicitWorkspace
                : new GeneratedWorkspace(buildRoot, targetId);

        return ResolvedTarget.builder()
                .targetId(targetId)
                .sourceEnvironment(selected.source())
                .targetEnvironment(selected.target())
                .loaderVersion(req.targetLoaderVersion())
                .outputMode("per_version")
                .sourceArtifacts(req.sourceArtifacts())
                .targetArtifacts(req.targetArtifacts())
                .sourceClasspath(req.sourceClasspath())
                .targetClasspath(req.targetClasspath())
                .sourceMapping(req.sourceMapping())
                .targetMapping(req.targetMapping())
                .outputNamespace(req.outputNamespace())
                .rulePacks(route)
                .workspace(workspace)
                .projectConfiguration(projectConfig)
                .build();
    }

    private static Path discoverTargetConfig(
            ProjectConfigurationLocator.DiscoveredConfiguration config,
            String targetId
    ) throws IOException {
        if (config.targetsDir() != null && Files.isDirectory(config.targetsDir())) {
            Path targetFile = config.targetsDir().resolve(targetId + ".properties");
            if (Files.isRegularFile(targetFile)) {
                return targetFile;
            }
        }
        if (config.transformFile() != null && Files.isRegularFile(config.transformFile())) {
            return config.transformFile();
        }
        throw new IOException("Cannot locate target configuration for '" + targetId + "' under " + config.root());
    }
}
