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
import java.util.List;
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
            assertNotNull(jar.getJarEntry("com/kyroxova/continuumlib/shims/BlockInteractionShim.class"));
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
            assertNotNull(jar.getJarEntry("com/kyroxova/continuumlib/shims/RecipeShim.class"));
        }

        // 3. Build Quilt Target
        TargetSpec quiltTarget = TargetSpec.of("1.20.1", "quilt");
        File quiltJar = builder.buildTargetJar(quiltTarget, mockFiles, tempDir.toFile());

        assertTrue(quiltJar.exists());
        assertEquals("examplemod-quilt-1.20.1.jar", quiltJar.getName());
        assertEquals("quilt", quiltJar.getParentFile().getName());

        try (JarFile jar = new JarFile(quiltJar)) {
            assertNotNull(jar.getJarEntry("quilt.mod.json"));
        }

        // 4. Build Modern Forge Target (1.18.2)
        TargetSpec forgeModern = TargetSpec.of("1.18.2", "forge");
        File forgeModernJar = builder.buildTargetJar(forgeModern, mockFiles, tempDir.toFile());

        assertTrue(forgeModernJar.exists());
        assertEquals("examplemod-forge-1.18.2.jar", forgeModernJar.getName());
        try (JarFile jar = new JarFile(forgeModernJar)) {
            assertNotNull(jar.getJarEntry("META-INF/mods.toml"));
        }

        // 5. Build Legacy Forge Target (1.12.2 and 1.7.10)
        TargetSpec forge112 = TargetSpec.of("1.12.2", "forge");
        File forge112Jar = builder.buildTargetJar(forge112, mockFiles, tempDir.toFile());

        assertTrue(forge112Jar.exists());
        assertEquals("examplemod-forge-1.12.2.jar", forge112Jar.getName());
        try (JarFile jar = new JarFile(forge112Jar)) {
            assertNotNull(jar.getJarEntry("mcmod.info"));
            assertNull(jar.getJarEntry("META-INF/mods.toml"));
        }

        TargetSpec forge1710 = TargetSpec.of("1.7.10", "forge");
        File forge1710Jar = builder.buildTargetJar(forge1710, mockFiles, tempDir.toFile());

        assertTrue(forge1710Jar.exists());
        assertEquals("examplemod-forge-1.7.10.jar", forge1710Jar.getName());
        try (JarFile jar = new JarFile(forge1710Jar)) {
            assertNotNull(jar.getJarEntry("mcmod.info"));
        }
    }

    @Test
    public void testUniversalBootstrapBundleGeneration(@TempDir Path tempDir) throws IOException {
        BootstrapperConfig config = BootstrapperConfig.loadFromClasspath(getClass().getClassLoader());
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        ContinuumJarBuilder builder = new ContinuumJarBuilder(config, kb);

        Map<String, byte[]> mockFiles = new HashMap<>();
        mockFiles.put("com/example/MyMod.class", new byte[]{ (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE });
        mockFiles.put("assets/examplemod/icon.png", new byte[]{ 1, 2, 3 });

        File universalJar = builder.buildUniversalBootstrapBundle(mockFiles, tempDir.toFile());

        assertNotNull(universalJar);
        assertTrue(universalJar.exists());
        assertEquals("examplemod-universal-1.18.2.jar", universalJar.getName());
        assertEquals("universal", universalJar.getParentFile().getName());

        try (JarFile jar = new JarFile(universalJar)) {
            // Mod classes and resources
            assertNotNull(jar.getJarEntry("com/example/MyMod.class"));
            assertNotNull(jar.getJarEntry("assets/examplemod/icon.png"));

            // All 4 loader manifests simultaneously present
            assertNotNull(jar.getJarEntry("META-INF/mods.toml"), "Forge manifest must be present in universal bundle");
            assertNotNull(jar.getJarEntry("META-INF/neoforge.mods.toml"), "NeoForge manifest must be present");
            assertNotNull(jar.getJarEntry("fabric.mod.json"), "Fabric manifest must be present");
            assertNotNull(jar.getJarEntry("quilt.mod.json"), "Quilt manifest must be present");
            assertNotNull(jar.getJarEntry("mcmod.info"), "Legacy Forge mcmod.info must be present");

            // Service descriptors & coremod plugin
            assertNotNull(jar.getJarEntry("META-INF/services/cpw.mods.modlauncher.serviceapi.ITransformationService"));
            assertEquals("com.kyroxova.bootstrapper.hooks.LegacyCoreModHook",
                    jar.getManifest().getMainAttributes().getValue("FMLCorePlugin"));

            // Bootstrapper core & hook classes
            assertNotNull(jar.getJarEntry("com/kyroxova/bootstrapper/ContinuumBootstrapper.class"));
            assertNotNull(jar.getJarEntry("com/kyroxova/bootstrapper/hooks/FabricPreLaunchHook.class"));
            assertNotNull(jar.getJarEntry("com/kyroxova/bootstrapper/hooks/ModLauncherPluginHook.class"));
            assertNotNull(jar.getJarEntry("com/kyroxova/bootstrapper/hooks/LegacyCoreModHook.class"));
            assertNotNull(jar.getJarEntry("com/kyroxova/continuumlib/shims/BlockInteractionShim.class"));

            // Configuration resources
            assertNotNull(jar.getJarEntry("data/continuumlib/config.json"));
        }
    }

    @Test
    public void testBuildAllBatchTargets(@TempDir Path tempDir) throws IOException {
        BootstrapperConfig config = BootstrapperConfig.loadFromClasspath(getClass().getClassLoader());
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        ContinuumJarBuilder builder = new ContinuumJarBuilder(config, kb);

        Map<String, byte[]> mockFiles = new HashMap<>();
        mockFiles.put("com/example/Dummy.txt", "data".getBytes(StandardCharsets.UTF_8));

        List<File> builtJars = builder.buildAll(mockFiles, tempDir.toFile(), true);

        // Config has targets + 1 universal bundle
        assertTrue(builtJars.size() >= 2);
        boolean foundUniversal = false;
        for (File f : builtJars) {
            assertTrue(f.exists());
            if ("universal".equals(f.getParentFile().getName())) {
                foundUniversal = true;
            }
        }
        assertTrue(foundUniversal, "buildAll with includeUniversal=true must produce universal bundle");
    }
}
