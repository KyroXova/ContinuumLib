package com.kyroxova.continuumlib.gradle;

import com.kyroxova.continuumlib.api.config.TransformRequest;
import com.kyroxova.continuumlib.knowledge.rule.RulePack;
import com.kyroxova.continuumlib.knowledge.rule.RulePackReader;
import com.kyroxova.continuumlib.source.ast.SourceParser;
import com.kyroxova.continuumlib.source.ast.SourceUnit;
import com.kyroxova.continuumlib.source.compile.SourceCompiler;
import com.kyroxova.continuumlib.source.compile.TargetJarPackager;
import com.kyroxova.continuumlib.source.rule.SourceMigrationPlan;
import com.kyroxova.continuumlib.source.rule.SourceMigrationRule;
import com.kyroxova.continuumlib.source.transform.SourceTransformer;
import com.kyroxova.continuumlib.filter.condition.TargetContext;
import com.kyroxova.continuumlib.filter.config.ContinuumProjectConfiguration;
import com.kyroxova.continuumlib.filter.config.FilterConfigurationReader;
import com.kyroxova.continuumlib.filter.engine.FilterEngine;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@CacheableTask
public abstract class TransformSourceTask extends ArtifactRequestTask {
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSourceDirectory();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getResourceFiles();

    @OutputDirectory
    public abstract DirectoryProperty getGeneratedSourceDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getCompiledClassesDirectory();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @TaskAction
    public void transformSource() throws IOException {
        TransformRequest req = request();
        Path projectRoot = getProjectDirectory().get().getAsFile().toPath();
        Path srcDir = getSourceDirectory().get().getAsFile().toPath();
        Path genDir = getGeneratedSourceDirectory().get().getAsFile().toPath();
        Path classesDir = getCompiledClassesDirectory().get().getAsFile().toPath();
        Path outJar = getOutputJar().get().getAsFile().toPath();

        List<RulePack> packs = new ArrayList<>();
        RulePackReader reader = new RulePackReader();
        RulePack matchedPack = null;
        for (var file : getRuleFiles()) {
            try (var in = Files.newInputStream(file.toPath())) {
                RulePack pack = reader.read(in);
                packs.add(pack);
                if (pack.id().equals(req.packId())) {
                    matchedPack = pack;
                }
            }
        }

        if (matchedPack == null) {
            throw new org.gradle.api.GradleException("No rule pack found matching id: " + req.packId());
        }

        SourceMigrationPlan plan = SourceMigrationPlan.fromRulePack(matchedPack);

        List<Path> srcClasspath = new ArrayList<>(req.sourceArtifacts().values());
        for (var art : req.sourceClasspath().values()) srcClasspath.add(art.file());

        SourceParser parser = new SourceParser(List.of(srcDir), srcClasspath);
        List<SourceUnit> units = parser.parseDirectory(srcDir);

        // Discover and load target-aware project configuration (inclusions & exclusions)
        Path configRoot = projectRoot.resolve("src/main/resources/continuumlib");
        if (!Files.exists(configRoot)) {
            configRoot = projectRoot.resolve("src/main/resources/data/continuumlib");
        }
        ContinuumProjectConfiguration projConfig = new FilterConfigurationReader().load(configRoot);

        // Discover mod resources
        Path resourcesDir = projectRoot.resolve("src/main/resources");
        Map<String, Path> rawResources = FilterEngine.discoverResources(resourcesDir);
        Map<String, Path> projectResources = new TreeMap<>();
        for (var entry : rawResources.entrySet()) {
            String path = entry.getKey();
            // Filter out continuumlib configuration from final jar resources
            if (!path.startsWith("continuumlib/") && !path.startsWith("data/continuumlib/")) {
                projectResources.put(path, entry.getValue());
            }
        }

        // Apply filtering (source files, registry declarations, and resources)
        TargetContext targetContext = TargetContext.of(matchedPack.target());
        FilterEngine filterEngine = new FilterEngine();
        FilterEngine.FilterResult filterResult = filterEngine.process(
                targetContext,
                projConfig.inclusions(),
                projConfig.exclusions(),
                units,
                projectResources
        );

        // Transform only active (non-excluded) source units
        SourceTransformer transformer = new SourceTransformer(plan);
        List<Path> generatedSources = transformer.transformAndWrite(filterResult.activeSources(), genDir);

        List<Path> targetClasspath = new ArrayList<>(req.targetArtifacts().values());
        for (var art : req.targetClasspath().values()) targetClasspath.add(art.file());

        SourceCompiler.compile(generatedSources, targetClasspath, classesDir, matchedPack.target().javaVersion());

        // Package target JAR with filtered active resources
        TargetJarPackager.packageJarWithResources(classesDir, filterResult.activeResources(), outJar);
    }
}
