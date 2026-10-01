package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.BootstrapperConfig;
import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.builder.ContinuumJarBuilder;
import com.kyroxova.continuumlib.builder.ManifestGenerator;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification Suite for MultiJarManifestVerificationTest:
 * Verifies ContinuumJarBuilder & ManifestGenerator multi-loader hygiene:
 * 1. Forge: mcmod.info (<= 1.12.2) and META-INF/mods.toml (1.13+).
 * 2. NeoForge: META-INF/neoforge.mods.toml (1.20.4+ / 26.3+).
 * 3. Fabric: fabric.mod.json with accurate schema and dependency constraints.
 * 4. Universal multi-loader bundle manifests.
 * 5. Full JAR build & entry verification with embedded Wave 5 shims.
 * 6. High-throughput concurrency.
 */
public class MultiJarManifestVerificationTest {

    private BootstrapperConfig config;
    private ApiKnowledgeBase kb;

    @BeforeEach
    public void setup() {
        this.config = BootstrapperConfig.loadFromClasspath(getClass().getClassLoader());
        this.kb = ApiKnowledgeBase.createDefault();
    }

    // =========================================================================
    // Tier 1: Isolation Tests (Manifest Synthesis)
    // =========================================================================

    @Nested
    @DisplayName("Tier 1: Isolation Tests")
    class Tier1Isolation {

        @Test
        @DisplayName("1.1 Forge manifest synthesis: mcmod.info (<= 1.12.2) and mods.toml (1.13+)")
        public void testForgeManifestSynthesis() {
            // Legacy Forge 1.12.2
            TargetSpec forge112 = TargetSpec.of("1.12.2", "forge");
            assertEquals("mcmod.info", ManifestGenerator.getManifestPath(forge112));
            String mcmodInfo = ManifestGenerator.generateManifest(config, forge112);
            assertNotNull(mcmodInfo);
            assertTrue(mcmodInfo.contains(config.getModId()), "mcmod.info must contain modId");
            assertTrue(mcmodInfo.contains("\"modid\""), "mcmod.info must follow valid JSON structure");

            // Modern Forge 1.18.2
            TargetSpec forge118 = TargetSpec.of("1.18.2", "forge");
            assertEquals("META-INF/mods.toml", ManifestGenerator.getManifestPath(forge118));
            String modsToml = ManifestGenerator.generateManifest(config, forge118);
            assertNotNull(modsToml);
            assertTrue(modsToml.contains("modLoader=\"javafml\""));
            assertTrue(modsToml.contains("modId=\"" + config.getModId() + "\""));
            assertTrue(modsToml.contains("version=\"1.18.2\""));
        }

        @Test
        @DisplayName("1.2 NeoForge manifest synthesis: neoforge.mods.toml (1.20.4+ / 26.3+)")
        public void testNeoForgeManifestSynthesis() {
            TargetSpec neo204 = TargetSpec.of("1.20.4", "neoforge");
            assertEquals("META-INF/neoforge.mods.toml", ManifestGenerator.getManifestPath(neo204));
            String neoToml204 = ManifestGenerator.generateManifest(config, neo204);
            assertNotNull(neoToml204);
            assertTrue(neoToml204.contains("modLoader=\"javafml\""));
            assertTrue(neoToml204.contains("loaderVersion=\"[1,)\""));
            assertTrue(neoToml204.contains("modId=\"" + config.getModId() + "\""));

            TargetSpec neo263 = TargetSpec.of("26.3", "neoforge");
            assertEquals("META-INF/neoforge.mods.toml", ManifestGenerator.getManifestPath(neo263));
            String neoToml263 = ManifestGenerator.generateManifest(config, neo263);
            assertTrue(neoToml263.contains("version=\"26.3\""));
        }

        @Test
        @DisplayName("1.3 Fabric manifest synthesis: fabric.mod.json structure and dependencies")
        public void testFabricManifestSynthesis() {
            TargetSpec fabricSpec = TargetSpec.of("1.20.1", "fabric");
            assertEquals("fabric.mod.json", ManifestGenerator.getManifestPath(fabricSpec));
            String fabricJson = ManifestGenerator.generateManifest(config, fabricSpec);
            assertNotNull(fabricJson);
            assertTrue(fabricJson.contains("\"schemaVersion\": 1"));
            assertTrue(fabricJson.contains("\"id\": \"" + config.getModId() + "\""));
            assertTrue(fabricJson.contains("\"version\": \"1.20.1\""));
            assertTrue(fabricJson.contains("\"fabricloader\": \">=0.14.0\""));
            assertTrue(fabricJson.contains("\"minecraft\": \"~1.20.1\""));
        }

        @Test
        @DisplayName("1.4 Universal multi-loader bundle manifests generation")
        public void testUniversalManifestsGeneration() {
            Map<String, String> manifests = ManifestGenerator.generateUniversalManifests(config, MCVersion.of("1.20.4"));
            assertNotNull(manifests);

            assertTrue(manifests.containsKey("META-INF/mods.toml"), "Universal bundle must contain mods.toml");
            assertTrue(manifests.containsKey("META-INF/neoforge.mods.toml"), "Universal bundle must contain neoforge.mods.toml");
            assertTrue(manifests.containsKey("fabric.mod.json"), "Universal bundle must contain fabric.mod.json");
            assertTrue(manifests.containsKey("quilt.mod.json"), "Universal bundle must contain quilt.mod.json");
            assertTrue(manifests.containsKey("mcmod.info"), "Universal bundle must contain mcmod.info");
            assertTrue(manifests.containsKey("META-INF/services/cpw.mods.modlauncher.serviceapi.ITransformationService"),
                    "Universal bundle must contain ModLauncher transformation service descriptor");

            String fabricUniversal = manifests.get("fabric.mod.json");
            assertTrue(fabricUniversal.contains("FabricPreLaunchHook"), "Universal fabric manifest must declare preLaunch hook");
        }
    }

    // =========================================================================
    // Tier 2: JAR Builder Manifest Packaging & Shims Verification
    // =========================================================================

    @Nested
    @DisplayName("Tier 2: JAR Builder Manifest Packaging & Shims")
    class Tier2JarPackaging {

        @Test
        @DisplayName("2.1 Build target JARs across Forge, NeoForge, and Fabric, verifying embedded manifests and Wave 5 shims")
        public void testBuildTargetJarsWithManifestsAndShims(@TempDir Path tempDir) throws IOException {
            ContinuumJarBuilder builder = new ContinuumJarBuilder(config, kb);

            Map<String, byte[]> mockFiles = new HashMap<>();
            mockFiles.put("assets/examplemod/textures/item/sample.png", new byte[]{1, 2, 3, 4});

            // 1. Build NeoForge Target
            TargetSpec neoTarget = TargetSpec.of("1.20.4", "neoforge");
            File neoJar = builder.buildTargetJar(neoTarget, mockFiles, tempDir.toFile());
            assertTrue(neoJar.exists());

            try (JarFile jf = new JarFile(neoJar)) {
                assertNotNull(jf.getJarEntry("META-INF/neoforge.mods.toml"), "NeoForge JAR must contain neoforge.mods.toml");
                assertNotNull(jf.getJarEntry("assets/examplemod/textures/item/sample.png"));

                // Verify embedded Wave 5 shims
                assertNotNull(jf.getJarEntry("com/kyroxova/continuumlib/shims/AdvancementShim.class"), "AdvancementShim must be embedded");
                assertNotNull(jf.getJarEntry("com/kyroxova/continuumlib/shims/ExplosionShim.class"), "ExplosionShim must be embedded");
                assertNotNull(jf.getJarEntry("com/kyroxova/continuumlib/shims/SoundPlaybackShim.class"), "SoundPlaybackShim must be embedded");
            }

            // 2. Build Modern Forge Target (1.18.2)
            TargetSpec forgeTarget = TargetSpec.of("1.18.2", "forge");
            File forgeJar = builder.buildTargetJar(forgeTarget, mockFiles, tempDir.toFile());
            assertTrue(forgeJar.exists());

            try (JarFile jf = new JarFile(forgeJar)) {
                assertNotNull(jf.getJarEntry("META-INF/mods.toml"), "Forge 1.18.2 JAR must contain META-INF/mods.toml");
                assertNotNull(jf.getJarEntry("com/kyroxova/continuumlib/shims/AdvancementShim.class"));
            }

            // 3. Build Fabric Target (1.20.1)
            TargetSpec fabricTarget = TargetSpec.of("1.20.1", "fabric");
            File fabricJar = builder.buildTargetJar(fabricTarget, mockFiles, tempDir.toFile());
            assertTrue(fabricJar.exists());

            try (JarFile jf = new JarFile(fabricJar)) {
                assertNotNull(jf.getJarEntry("fabric.mod.json"), "Fabric JAR must contain fabric.mod.json");
                assertNotNull(jf.getJarEntry("com/kyroxova/continuumlib/shims/SoundPlaybackShim.class"));
            }

            // 4. Build Legacy Forge Target (1.12.2)
            TargetSpec legacyForge = TargetSpec.of("1.12.2", "forge");
            File legacyJar = builder.buildTargetJar(legacyForge, mockFiles, tempDir.toFile());
            assertTrue(legacyJar.exists());

            try (JarFile jf = new JarFile(legacyJar)) {
                assertNotNull(jf.getJarEntry("mcmod.info"), "Legacy Forge 1.12.2 JAR must contain mcmod.info");
            }
        }
    }

    // =========================================================================
    // Tier 3: Concurrency & Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 3: Concurrency & Stress Tests")
    class Tier3Concurrency {

        @Test
        @DisplayName("3.1 Highly concurrent manifest generation across multiple threads")
        public void testConcurrentManifestGeneration() throws InterruptedException {
            int threadCount = 16;
            int opsPerThread = 200;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger verifiedManifests = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < opsPerThread; i++) {
                            // Alternate loaders
                            LoaderType loader = switch (i % 4) {
                                case 0 -> LoaderType.NEOFORGE;
                                case 1 -> LoaderType.FORGE;
                                case 2 -> LoaderType.FABRIC;
                                default -> LoaderType.QUILT;
                            };

                            TargetSpec spec = new TargetSpec(MCVersion.of("1.20.4"), loader, "mojmap", 0);
                            String path = ManifestGenerator.getManifestPath(spec);
                            String content = ManifestGenerator.generateManifest(config, spec);

                            if (path != null && content != null && content.contains(config.getModId())) {
                                verifiedManifests.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent manifest generation timed out");
            executor.shutdown();

            assertEquals(threadCount * opsPerThread, verifiedManifests.get());
        }
    }
}
