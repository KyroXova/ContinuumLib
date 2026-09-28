package com.kyroxova.continuumlib.gradle;

import com.kyroxova.bootstrapper.config.BootstrapperConfig;
import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.builder.ContinuumJarBuilder;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Gradle task that compiles and transforms an input JAR into all target loaders and versions.
 */
public abstract class ContinuumBuildTask extends DefaultTask {

    @InputFile
    public abstract RegularFileProperty getInputJar();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Input
    public abstract Property<String> getModId();

    @Input
    public abstract Property<TargetSpec> getBaseSpec();

    @Input
    public abstract ListProperty<TargetSpec> getTargets();

    @TaskAction
    public void buildTargets() throws IOException {
        File inputJarFile = getInputJar().get().getAsFile();
        File outputDir = getOutputDirectory().get().getAsFile();
        String modId = getModId().get();
        TargetSpec base = getBaseSpec().get();
        List<TargetSpec> targetList = getTargets().get();

        getLogger().lifecycle(String.format("[Continuum] Building %d target JARs for mod '%s' (Base: %s)",
                targetList.size(), modId, base));

        BootstrapperConfig config = new BootstrapperConfig(
                modId,
                "multi-jar",
                base,
                targetList,
                "build/libs/%loader%/%modid%-%loader%-%version%.jar",
                "build/libs/%loader%"
        );

        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        ContinuumJarBuilder jarBuilder = new ContinuumJarBuilder(config, kb);

        for (TargetSpec target : targetList) {
            getLogger().lifecycle(String.format("[Continuum] Processing Target: %s", target));
            File builtJar = jarBuilder.buildTargetJar(target, inputJarFile, outputDir);
            getLogger().lifecycle(String.format("[Continuum] -> Generated: %s (%d KB)",
                    builtJar.getName(), builtJar.length() / 1024));
        }

        getLogger().lifecycle("[Continuum] All target builds completed successfully!");
    }
}
