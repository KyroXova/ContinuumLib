package com.kyroxova.continuumlib.gradle;

import com.kyroxova.continuumlib.api.config.TransformRequest;
import com.kyroxova.continuumlib.filter.config.ContinuumProjectConfiguration;
import com.kyroxova.continuumlib.filter.config.FilterConfigurationReader;
import com.kyroxova.continuumlib.filter.engine.FilterEngine;
import com.kyroxova.continuumlib.pipeline.ResolvedTarget;
import com.kyroxova.continuumlib.pipeline.TargetResolver;
import com.kyroxova.continuumlib.project.ProjectConfigurationLayout;
import com.kyroxova.continuumlib.project.SourceFileSelection;
import com.kyroxova.continuumlib.source.ast.SourceParser;
import com.kyroxova.continuumlib.source.ast.SourceUnit;
import com.kyroxova.continuumlib.source.compile.SourceCompiler;
import com.kyroxova.continuumlib.source.compile.TargetJarPackager;
import com.kyroxova.continuumlib.source.rule.SourceMigrationPlan;
import com.kyroxova.continuumlib.source.transform.SourceTransformer;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

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
        Path srcDir = getSourceDirectory().get().getAsFile().toPath();
        Path genDir = getGeneratedSourceDirectory().get().getAsFile().toPath();
        Path classesDir = getCompiledClassesDirectory().get().getAsFile().toPath();
        Path outJar = getOutputJar().get().getAsFile().toPath();

        ResolvedTarget target = new TargetResolver().resolve("source", request, rulePacks());
        SourceMigrationPlan plan = SourceMigrationPlan.fromRulePack(target.rulePack());

        ProjectConfigurationLayout layout = ProjectConfigurationLayout.discover(projectRoot);
        ContinuumProjectConfiguration projectConfiguration =
                new FilterConfigurationReader().load(layout.filterConfigurationRoot());

        // Source-path exclusions are evaluated before JavaParser. Class/registry rules stay semantic and run after parse.
        SourceFileSelection sourceSelection =
                SourceFileSelection.discover(srcDir, target.context(), projectConfiguration);

        SourceParser parser = new SourceParser(List.of(srcDir), target.sourceClasspath());
        List<SourceUnit> units = parser.parseFiles(srcDir, sourceSelection.activeFiles());

        Path resourcesDir = projectRoot.resolve("src/main/resources");
        Map<String, Path> rawResources = FilterEngine.discoverResources(resourcesDir);
        Map<String, Path> projectResources = new TreeMap<>();
        for (var entry : rawResources.entrySet()) {
            String path = entry.getKey();
            if (!path.startsWith("continuumlib/") && !path.startsWith("data/continuumlib/")) {
                projectResources.put(path, entry.getValue());
            }
        }

        FilterEngine.FilterResult filterResult = new FilterEngine().process(
                target.context(),
                projectConfiguration.inclusions(),
                projectConfiguration.exclusions(),
                units,
                projectResources
        );

        SourceTransformer transformer = new SourceTransformer(plan);
        List<Path> generatedSources = transformer.transformAndWrite(filterResult.activeSources(), genDir);

        SourceCompiler.compile(
                generatedSources,
                target.targetClasspath(),
                classesDir,
                target.targetEnvironment().javaVersion()
        );

        TargetJarPackager.packageJarWithResources(classesDir, filterResult.activeResources(), outJar);

        getLogger().lifecycle(
                "ContinuumLib source target {} selected {} source files, excluded {} before parsing, generated {} files. Output: {}",
                target.id(),
                sourceSelection.activeFiles().size(),
                sourceSelection.excludedFiles().size(),
                generatedSources.size(),
                outJar
        );
    }
}
