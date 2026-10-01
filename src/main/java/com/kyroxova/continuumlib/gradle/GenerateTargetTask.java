package com.kyroxova.continuumlib.gradle;

import com.kyroxova.continuumlib.knowledge.rule.RulePack;
import com.kyroxova.continuumlib.pipeline.*;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Gradle task executing the unified target-generation pipeline for a single target.
 */
@CacheableTask
public abstract class GenerateTargetTask extends ArtifactRequestTask {
    @Input
    public abstract Property<String> getTargetId();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSourceDirectory();

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

        List<RulePack> packs = rulePacks();
        ResolvedTarget target = new TargetResolver().resolve(projectRoot, targetId, buildRoot, packs);

        TargetGenerationPipeline pipeline = new TargetGenerationPipeline();
        String outputJarName = getOutputJar().get().getAsFile().getName();
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
        Path finalTaskJar = getOutputJar().get().getAsFile().toPath();
        if (generatedJar != null && !generatedJar.equals(finalTaskJar) && Files.exists(generatedJar)) {
            Files.createDirectories(finalTaskJar.getParent());
            Files.copy(generatedJar, finalTaskJar, StandardCopyOption.REPLACE_EXISTING);
        }

        getLogger().lifecycle("ContinuumLib successfully generated target '{}' at {}", targetId, finalTaskJar);
    }
}
