package com.kyroxova.continuumlib.builder;

import com.kyroxova.bootstrapper.config.BootstrapperConfig;
import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.transformer.ContinuumBytecodeTransformer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.logging.Logger;

/**
 * Automates the packaging and generation of multi-version / multi-loader JAR files.
 * Output path format: build/libs/%loader%/%modid%-%loader%-%version%.jar
 */
public final class ContinuumJarBuilder {

    private static final Logger LOGGER = Logger.getLogger(ContinuumJarBuilder.class.getName());

    private final BootstrapperConfig config;
    private final ApiKnowledgeBase knowledgeBase;

    public ContinuumJarBuilder(BootstrapperConfig config, ApiKnowledgeBase knowledgeBase) {
        this.config = config;
        this.knowledgeBase = knowledgeBase;
    }

    /**
     * Builds a specific target JAR from compiled input classes and resources.
     *
     * @param target the target specification (e.g. 1.20.4 NeoForge)
     * @param inputFiles map of entry name -> raw byte content (compiled classes & resources)
     * @param outputDirectoryRoot base output directory (e.g. "build/libs")
     * @return File handle to the generated JAR
     */
    public File buildTargetJar(TargetSpec target, Map<String, byte[]> inputFiles, File outputDirectoryRoot) throws IOException {
        String destDirRel = config.formatDestinationDir(target.getLoader().getId());
        File targetDir = new File(outputDirectoryRoot, target.getLoader().getId());
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Failed to create target directory: " + targetDir.getAbsolutePath());
        }

        String jarName = config.formatJarName(target.getVersion().getRaw(), target.getLoader().getId());
        File outputJar = new File(targetDir, jarName);

        LOGGER.info(String.format("[ContinuumJarBuilder] Building %s for Target: %s", jarName, target));

        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(knowledgeBase, config.getBaseSpec(), target);

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outputJar))) {
            // 1. Process and write input files
            for (Map.Entry<String, byte[]> entry : inputFiles.entrySet()) {
                String entryName = entry.getKey();
                byte[] content = entry.getValue();

                // Normalize plural vs singular datapack paths (recipes/ vs recipe/, tags/blocks/ vs tags/block/)
                entryName = DataPackResourcePathNormalizer.normalizePath(entryName, target.getVersion());

                if (entryName.endsWith(".class")) {
                    String className = entryName.substring(0, entryName.length() - 6);
                    content = transformer.transform(className, content);
                }

                jos.putNextEntry(new JarEntry(entryName));
                jos.write(content);
                jos.closeEntry();
            }

            // 2. Synthesize loader manifest if not present
            String manifestPath = ManifestGenerator.getManifestPath(target);
            if (!inputFiles.containsKey(manifestPath)) {
                String manifestContent = ManifestGenerator.generateManifest(config, target);
                jos.putNextEntry(new JarEntry(manifestPath));
                jos.write(manifestContent.getBytes(StandardCharsets.UTF_8));
                jos.closeEntry();
            }
        }

        LOGGER.info(String.format("[ContinuumJarBuilder] Successfully generated %s (%d bytes)",
                outputJar.getAbsolutePath(), outputJar.length()));
        return outputJar;
    }
}
