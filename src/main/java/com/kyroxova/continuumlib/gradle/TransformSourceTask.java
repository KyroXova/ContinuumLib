package com.kyroxova.continuumlib.gradle;

import com.kyroxova.continuumlib.knowledge.rule.RulePack;
import com.kyroxova.continuumlib.pipeline.GeneratedWorkspace;
import com.kyroxova.continuumlib.pipeline.ResolvedTarget;
import com.kyroxova.continuumlib.pipeline.TargetGenerationPipeline;
import com.kyroxova.continuumlib.pipeline.TargetGenerationResult;
import com.kyroxova.continuumlib.pipeline.TargetResolver;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@CacheableTask
public abstract class TransformSourceTask extends ArtifactRequestTask {
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSourceDirectory();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getResourceFiles();

    @OutputDirectory
    public abstract DirectoryProperty getTargetWorkspaceDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getGeneratedSourceDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getCompiledClassesDirectory();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @TaskAction
    public void transformSource() throws Exception {
        Path projectRoot = getProjectDirectory().get().getAsFile().toPath();
        Path buildRoot = projectRoot.resolve("build");
        Path sourceRoot = getSourceDirectory().get().getAsFile().toPath();
        Path resourcesRoot = projectRoot.resolve("src/main/resources");
        Path configFile = getConfigFile().get().getAsFile().toPath();
        Path generatedSourceOutput = getGeneratedSourceDirectory().get().getAsFile().toPath();
        Path compiledClassesOutput = getCompiledClassesDirectory().get().getAsFile().toPath();
        Path finalTaskJar = getOutputJar().get().getAsFile().toPath();

        protectOutput(finalTaskJar, List.of(sourceRoot));
        List<RulePack> packs = rulePacks();

        GeneratedWorkspace workspace = GeneratedWorkspace.atTargetRoot(
                getTargetWorkspaceDirectory().get().getAsFile().toPath(),
                "source"
        );
        ResolvedTarget target = new TargetResolver().resolve(
                projectRoot,
                "source",
                buildRoot,
                packs,
                configFile,
                workspace
        );

        TargetGenerationResult result = new TargetGenerationPipeline().execute(
                target,
                projectRoot,
                List.of(sourceRoot),
                List.of(resourcesRoot),
                finalTaskJar.getFileName().toString()
        );
        if (!result.isSuccess()) {
            throw new GradleException("ContinuumLib source transformation failed. See report: "
                    + workspace.reportsDir().resolve("generation.txt"));
        }

        syncDirectory(workspace.sourceDir(), generatedSourceOutput);
        syncDirectory(workspace.classesDir(), compiledClassesOutput);
        copyFile(result.outputJar(), finalTaskJar);

        getLogger().lifecycle(
                "ContinuumLib source transformation completed through unified target pipeline. Output: {}",
                finalTaskJar
        );
    }

    private static void syncDirectory(Path source, Path target) throws IOException {
        Path normalizedSource = source.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (normalizedSource.equals(normalizedTarget)) return;

        cleanDirectory(normalizedTarget);
        Files.createDirectories(normalizedTarget);
        try (Stream<Path> stream = Files.walk(normalizedSource)) {
            for (Path path : stream.sorted().toList()) {
                Path relative = normalizedSource.relativize(path);
                Path destination = normalizedTarget.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void copyFile(Path source, Path target) throws IOException {
        Path normalizedSource = source.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (normalizedSource.equals(normalizedTarget)) return;
        Files.createDirectories(normalizedTarget.getParent());
        Files.copy(normalizedSource, normalizedTarget, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void cleanDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (Stream<Path> stream = Files.walk(directory)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
