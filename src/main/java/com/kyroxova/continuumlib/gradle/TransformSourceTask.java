package com.kyroxova.continuumlib.gradle;

import com.kyroxova.continuumlib.api.config.TransformRequest;
import com.kyroxova.continuumlib.pipeline.GeneratedWorkspace;
import com.kyroxova.continuumlib.pipeline.ResolvedTarget;
import com.kyroxova.continuumlib.pipeline.SourceTargetGenerationResult;
import com.kyroxova.continuumlib.pipeline.SourceTargetGenerator;
import com.kyroxova.continuumlib.pipeline.TargetResolver;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;

import java.io.IOException;
import java.nio.file.Path;

@CacheableTask
public abstract class TransformSourceTask extends ArtifactRequestTask {
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSourceDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getGeneratedSourceDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getCompiledClassesDirectory();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @TaskAction
    public void transformSource() throws IOException {
        TransformRequest request = request();
        Path projectRoot = getProjectDirectory().get().getAsFile().toPath();
        Path sourceRoot = getSourceDirectory().get().getAsFile().toPath();

        ResolvedTarget target = new TargetResolver().resolve("source", request, rulePacks());
        GeneratedWorkspace workspace = GeneratedWorkspace.fromOutputs(
                getGeneratedSourceDirectory().get().getAsFile().toPath(),
                getCompiledClassesDirectory().get().getAsFile().toPath(),
                getOutputJar().get().getAsFile().toPath()
        );

        SourceTargetGenerationResult result =
                new SourceTargetGenerator().generate(projectRoot, sourceRoot, target, workspace);

        getLogger().lifecycle(
                "ContinuumLib source target {} generated {} Java files, excluded {} files before parsing and {} registry entries. Output: {}",
                result.target().id(),
                result.generatedSources().size(),
                result.excludedBeforeParsing().size(),
                result.excludedRegistryEntries().size(),
                result.workspace().outputJar()
        );
    }
}
