package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.continuumlib.filter.config.ContinuumProjectConfiguration;
import com.kyroxova.continuumlib.filter.config.FilterConfigurationReader;
import com.kyroxova.continuumlib.filter.engine.FilterEngine;
import com.kyroxova.continuumlib.project.ProjectConfigurationLayout;
import com.kyroxova.continuumlib.project.SourceFileSelection;
import com.kyroxova.continuumlib.source.ast.SourceParser;
import com.kyroxova.continuumlib.source.ast.SourceUnit;
import com.kyroxova.continuumlib.source.compile.SourceCompiler;
import com.kyroxova.continuumlib.source.compile.TargetJarPackager;
import com.kyroxova.continuumlib.source.rule.SourceMigrationPlan;
import com.kyroxova.continuumlib.source.transform.SourceTransformer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Reusable source-generation engine for one resolved target.
 * Gradle tasks should configure inputs/outputs and delegate here rather than owning build semantics.
 */
public final class SourceTargetGenerator {

    public SourceTargetGenerationResult generate(
            Path projectRoot,
            Path sourceRoot,
            ResolvedTarget target,
            GeneratedWorkspace workspace
    ) throws IOException {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(sourceRoot, "sourceRoot");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(workspace, "workspace");

        workspace.prepareCompilationOutputs();

        ProjectConfigurationLayout layout = ProjectConfigurationLayout.discover(projectRoot);
        ContinuumProjectConfiguration projectConfiguration =
                new FilterConfigurationReader().load(layout.filterConfigurationRoot());

        SourceFileSelection sourceSelection =
                SourceFileSelection.discover(sourceRoot, target.context(), projectConfiguration);

        SourceParser parser = new SourceParser(List.of(sourceRoot), target.sourceClasspath());
        List<SourceUnit> units = parser.parseFiles(sourceRoot, sourceSelection.activeFiles());

        Map<String, Path> projectResources = discoverProjectResources(projectRoot.resolve("src/main/resources"));

        FilterEngine.FilterResult filterResult = new FilterEngine().process(
                target.context(),
                projectConfiguration.inclusions(),
                projectConfiguration.exclusions(),
                units,
                projectResources
        );

        SourceMigrationPlan plan = SourceMigrationPlan.fromRulePack(target.rulePack());
        List<Path> generatedSources = new SourceTransformer(plan)
                .transformAndWrite(filterResult.activeSources(), workspace.sourceDirectory());

        SourceCompiler.compile(
                generatedSources,
                target.targetClasspath(),
                workspace.classesDirectory(),
                target.targetEnvironment().javaVersion()
        );

        TargetJarPackager.packageJarWithResources(
                workspace.classesDirectory(),
                filterResult.activeResources(),
                workspace.outputJar()
        );

        return new SourceTargetGenerationResult(
                target,
                workspace,
                generatedSources,
                sourceSelection.excludedFiles(),
                filterResult.excludedRegistryEntries(),
                filterResult.activeResources(),
                filterResult.excludedResources()
        );
    }

    private static Map<String, Path> discoverProjectResources(Path resourcesRoot) throws IOException {
        Map<String, Path> raw = FilterEngine.discoverResources(resourcesRoot);
        Map<String, Path> result = new TreeMap<>();
        for (var entry : raw.entrySet()) {
            String path = entry.getKey().replace('\\', '/');
            if (!path.startsWith("continuumlib/") && !path.startsWith("data/continuumlib/")) {
                result.put(path, entry.getValue());
            }
        }
        return result;
    }
}
