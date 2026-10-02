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
    public abstract ConfigurableFileCollection getSourceRoots();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getResourceFiles();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getResourceRoots();

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
        List<Path> sourceRoots = new java.util.ArrayList<>(getSourceRoots().getFiles().stream()
                .map(file -> file.toPath().toAbsolutePath().normalize())
                .sorted()
                .toList());
        Path normalizedSourceRoot = sourceRoot.toAbsolutePath().normalize();
        if (!sourceRoots.contains(normalizedSourceRoot)) {
            sourceRoots.add(normalizedSourceRoot);
        }
        sourceRoots = sourceRoots.stream().distinct().sorted().toList();

        List<Path> resourceRoots = getResourceRoots().getFiles().stream()
                .map(file -> file.toPath().toAbsolutePath().normalize())
                .sorted()
                .toList();
        if (resourceRoots.isEmpty()) {
            resourceRoots = List.of(projectRoot.resolve("src/main/resources").toAbsolutePath().normalize());
        }
        Path configFile = getConfigFile().get().getAsFile().toPath();
        Path generatedSourceOutput = getGeneratedSourceDirectory().get().getAsFile().toPath();
        Path compiledClassesOutput = getCompiledClassesDirectory().get().getAsFile().toPath();
        Path finalTaskJar = getOutputJar().get().getAsFile().toPath();

        Path workspaceRoot = getTargetWorkspaceDirectory().get().getAsFile().toPath();
        List<Path> consumerInputs = new java.util.ArrayList<>();
        consumerInputs.addAll(sourceRoots);
        consumerInputs.addAll(resourceRoots);

        List<Path> workspaceInputs = new java.util.ArrayList<>(consumerInputs);
        workspaceInputs.add(generatedSourceOutput);
        workspaceInputs.add(compiledClassesOutput);
        workspaceInputs.add(finalTaskJar);
        protectGeneratedDirectory(workspaceRoot, workspaceInputs);

        List<Path> generatedSourceInputs = new java.util.ArrayList<>(consumerInputs);
        generatedSourceInputs.add(workspaceRoot);
        generatedSourceInputs.add(compiledClassesOutput);
        generatedSourceInputs.add(finalTaskJar);
        protectGeneratedDirectory(generatedSourceOutput, generatedSourceInputs);

        List<Path> classesInputs = new java.util.ArrayList<>(consumerInputs);
        classesInputs.add(workspaceRoot);
        classesInputs.add(generatedSourceOutput);
        classesInputs.add(finalTaskJar);
        protectGeneratedDirectory(compiledClassesOutput, classesInputs);

        List<Path> outputInputs = new java.util.ArrayList<>(consumerInputs);
        outputInputs.add(workspaceRoot);
        outputInputs.add(generatedSourceOutput);
        outputInputs.add(compiledClassesOutput);
        protectOutput(finalTaskJar, outputInputs);

        List<RulePack> packs = rulePacks();

        GeneratedWorkspace workspace = GeneratedWorkspace.atTargetRoot(
                workspaceRoot,
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

        TargetGenerationResult result = new TargetGenerationPipeline(
                targetCompilationStrategy(target.javaVersion())
        ).execute(
                target,
                projectRoot,
                sourceRoots,
                resourceRoots,
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
