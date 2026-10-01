package com.kyroxova.continuumlib.gradle;

import com.kyroxova.continuumlib.knowledge.rule.RulePack;
import com.kyroxova.continuumlib.pipeline.*;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@CacheableTask
public abstract class GenerateTargetTask extends ArtifactRequestTask {
    @Input
    public abstract Property<String> getTargetId();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSourceDirectory();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getResourceFiles();

    @OutputDirectory
    public abstract DirectoryProperty getTargetWorkspaceDirectory();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @TaskAction
    public void generate() throws Exception {
        String targetId = getTargetId().get();
        Path projectRoot = getProjectDirectory().get().getAsFile().toPath();
        Path buildRoot = getProjectDirectory().get().dir("build").getAsFile().toPath();
        Path srcDir = getSourceDirectory().get().getAsFile().toPath();
        Path resDir = projectRoot.resolve("src/main/resources");
        Path configFile = getConfigFile().get().getAsFile().toPath();
        Path finalTaskJar = getOutputJar().get().getAsFile().toPath();

        Path workspaceRoot = getTargetWorkspaceDirectory().get().getAsFile().toPath();
        protectGeneratedDirectory(workspaceRoot, List.of(srcDir, resDir, finalTaskJar));
        protectOutput(finalTaskJar, List.of(srcDir, resDir, workspaceRoot));

        List<RulePack> packs = rulePacks();
        GeneratedWorkspace workspace = GeneratedWorkspace.atTargetRoot(
                workspaceRoot,
                targetId
        );
        ResolvedTarget target = new TargetResolver().resolve(
                projectRoot,
                targetId,
                buildRoot,
                packs,
                configFile,
                workspace
        );

        TargetGenerationPipeline pipeline = new TargetGenerationPipeline();
        String outputJarName = finalTaskJar.getFileName().toString();
        TargetGenerationResult result = pipeline.execute(
                target,
                projectRoot,
                List.of(srcDir),
                List.of(resDir),
                outputJarName
        );

        if (!result.isSuccess()) {
            throw new GradleException("ContinuumLib target generation failed for target '" + targetId + "'. See report: "
                    + result.workspace().reportsDir().resolve("generation.txt"));
        }

        Path generatedJar = result.outputJar();
        if (generatedJar != null && !generatedJar.equals(finalTaskJar) && Files.exists(generatedJar)) {
            Files.createDirectories(finalTaskJar.getParent());
            Files.copy(generatedJar, finalTaskJar, StandardCopyOption.REPLACE_EXISTING);
        }

        getLogger().lifecycle("ContinuumLib successfully generated target '{}' at {}", targetId, finalTaskJar);
    }
}
