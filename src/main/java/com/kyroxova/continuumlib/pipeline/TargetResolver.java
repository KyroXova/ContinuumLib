package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.continuumlib.api.config.TransformRequest;
import com.kyroxova.continuumlib.filter.config.ContinuumProjectConfiguration;
import com.kyroxova.continuumlib.filter.config.FilterConfigurationReader;
import com.kyroxova.continuumlib.knowledge.rule.BuiltinRulePacks;
import com.kyroxova.continuumlib.knowledge.rule.RulePack;
import com.kyroxova.continuumlib.pipeline.config.ProjectConfigurationLocator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Resolves canonical ResolvedTarget from project configuration, environment properties, and rule packs.
 */
public final class TargetResolver {
    private final ProjectConfigurationLocator locator = new ProjectConfigurationLocator();
    private final FilterConfigurationReader filterReader = new FilterConfigurationReader();

    public ResolvedTarget resolve(
            Path projectRoot,
            String targetId,
            Path buildRoot,
            List<RulePack> availablePacks
    ) throws IOException {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(buildRoot, "buildRoot");

        var config = locator.locate(projectRoot);

        // Find the target property file
        Path targetConfigFile = null;
        if (config.targetsDir() != null && Files.isDirectory(config.targetsDir())) {
            Path targetFile = config.targetsDir().resolve(targetId + ".properties");
            if (Files.isRegularFile(targetFile)) {
                targetConfigFile = targetFile;
            }
        }
        if (targetConfigFile == null && config.transformFile() != null && Files.isRegularFile(config.transformFile())) {
            targetConfigFile = config.transformFile();
        }
        if (targetConfigFile == null) {
            throw new IOException("Cannot locate target configuration for '" + targetId + "' under " + config.root());
        }

        TransformRequest req = TransformRequest.read(targetConfigFile, projectRoot);

        List<RulePack> allPacks = new ArrayList<>();
        if (availablePacks != null) allPacks.addAll(availablePacks);
        allPacks.addAll(BuiltinRulePacks.load());

        RulePack matchedPack = allPacks.stream()
                .filter(p -> p.id().equals(req.packId()))
                .findFirst()
                .orElseThrow(() -> new IOException("No rule pack found matching id: " + req.packId()));

        ContinuumProjectConfiguration projectConfig = filterReader.load(config.root());
        GeneratedWorkspace workspace = new GeneratedWorkspace(buildRoot, targetId);

        return ResolvedTarget.builder()
                .targetId(targetId)
                .sourceEnvironment(matchedPack.source())
                .targetEnvironment(matchedPack.target())
                .outputMode("per_version")
                .sourceArtifacts(req.sourceArtifacts())
                .targetArtifacts(req.targetArtifacts())
                .sourceClasspath(req.sourceClasspath())
                .targetClasspath(req.targetClasspath())
                .sourceMapping(req.sourceMapping())
                .targetMapping(req.targetMapping())
                .outputNamespace(req.outputNamespace())
                .addRulePack(matchedPack)
                .workspace(workspace)
                .projectConfiguration(projectConfig)
                .build();
    }
}
