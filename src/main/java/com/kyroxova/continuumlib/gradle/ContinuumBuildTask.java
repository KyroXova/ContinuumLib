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
 * Gradle task that compiles and transforms an input JAR into loader-specific target JARs
 * and universal in-memory multi-loader bootstrap bundles.
 */
public abstract class ContinuumBuildTask extends DefaultTask {

    @InputFile
    public abstract RegularFileProperty getInputJar();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Input
    public abstract Property<String> getModId();

    @Input
    @Optional
    public abstract Property<String> getMode();

    @Input
    @Optional
    public abstract Property<Boolean> getUniversalBundle();

    @Input
    @Optional
    public abstract Property<String> getJarNamingFormat();

    @Input
    @Optional
    public abstract Property<String> getDestinationPath();

    @Input
    public abstract Property<TargetSpec> getBaseSpec();

    @Input
    public abstract ListProperty<TargetSpec> getTargets();

    @TaskAction
    public void buildTargets() throws IOException {
        File inputJarFile = getInputJar().get().getAsFile();
        File outputDir = getOutputDirectory().get().getAsFile();
        String modId = getModId().get();
        String mode = getMode().getOrElse("hybrid");
        boolean wantUniversal = getUniversalBundle().getOrElse(true);
        String naming = getJarNamingFormat().getOrElse("%modid%-%loader%-%version%.jar");
        String dest = getDestinationPath().getOrElse("build/libs/%loader%/");
        TargetSpec base = getBaseSpec().get();
        List<TargetSpec> targetList = getTargets().get();

        getLogger().lifecycle(String.format("[Continuum] Initiating build for mod '%s' (Mode: %s, Base: %s)",
                modId, mode, base));

        BootstrapperConfig config = new BootstrapperConfig(
                modId,
                mode,
                base,
                targetList,
                naming,
                dest
        );

        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        ContinuumJarBuilder jarBuilder = new ContinuumJarBuilder(config, kb);

        // 1. Build Loader-Specific Target JARs (unless in pure runtime single-jar mode)
        if (!"runtime".equalsIgnoreCase(mode)) {
            getLogger().lifecycle(String.format("[Continuum] Building %d target JARs...", targetList.size()));
            for (TargetSpec target : targetList) {
                getLogger().lifecycle(String.format("[Continuum] Processing Target: %s", target));
                File builtJar = jarBuilder.buildTargetJar(target, inputJarFile, outputDir);
                getLogger().lifecycle(String.format("[Continuum] -> Generated: %s (%d KB)",
                        builtJar.getName(), builtJar.length() / 1024));
            }
        }

        // 2. Build Universal In-Memory Multi-Loader Bootstrap Bundle
        if (wantUniversal && ("hybrid".equalsIgnoreCase(mode) || "runtime".equalsIgnoreCase(mode))) {
            getLogger().lifecycle(String.format("[Continuum] Packaging Universal In-Memory Multi-Loader Bootstrap Bundle..."));
            File universalJar = jarBuilder.buildUniversalBootstrapBundle(inputJarFile, outputDir);
            getLogger().lifecycle(String.format("[Continuum] -> Generated Universal Bundle: %s (%d KB)",
                    universalJar.getName(), universalJar.length() / 1024));
        }

        getLogger().lifecycle("[Continuum] All target builds completed successfully!");
    }
}
