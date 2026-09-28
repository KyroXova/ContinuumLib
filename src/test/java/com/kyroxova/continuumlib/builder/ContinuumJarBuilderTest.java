package com.kyroxova.continuumlib.builder;

import com.kyroxova.bootstrapper.config.BootstrapperConfig;
import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

public class ContinuumJarBuilderTest {

    @Test
    public void testJarGenerationAndManifestInjection(@TempDir Path tempDir) throws IOException {
        BootstrapperConfig config = BootstrapperConfig.loadFromClasspath(getClass().getClassLoader());
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        ContinuumJarBuilder builder = new ContinuumJarBuilder(config, kb);

        Map<String, byte[]> mockFiles = new HashMap<>();
        mockFiles.put("com/example/MyClass.txt", "Sample Resource Content".getBytes(StandardCharsets.UTF_8));

        // 1. Build NeoForge Target
        TargetSpec neoTarget = TargetSpec.of("1.20.4", "neoforge");
        File neoJar = builder.buildTargetJar(neoTarget, mockFiles, tempDir.toFile());

        assertTrue(neoJar.exists());
        assertEquals("examplemod-neoforge-1.20.4.jar", neoJar.getName());
        assertEquals("neoforge", neoJar.getParentFile().getName());

        try (JarFile jar = new JarFile(neoJar)) {
            assertNotNull(jar.getJarEntry("com/example/MyClass.txt"));
            assertNotNull(jar.getJarEntry("META-INF/neoforge.mods.toml"));
        }

        // 2. Build Fabric Target
        TargetSpec fabricTarget = TargetSpec.of("1.20.1", "fabric");
        File fabricJar = builder.buildTargetJar(fabricTarget, mockFiles, tempDir.toFile());

        assertTrue(fabricJar.exists());
        assertEquals("examplemod-fabric-1.20.1.jar", fabricJar.getName());
        assertEquals("fabric", fabricJar.getParentFile().getName());

        try (JarFile jar = new JarFile(fabricJar)) {
            assertNotNull(jar.getJarEntry("com/example/MyClass.txt"));
            assertNotNull(jar.getJarEntry("fabric.mod.json"));
        }
    }
}
