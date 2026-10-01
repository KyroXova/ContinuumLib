package com.kyroxova.continuumlib.builder;

import com.kyroxova.bootstrapper.config.BootstrapperConfig;
import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.MCVersion;
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

public class DataPackAndRecordItemTest {

    @Test
    public void testDataPackPathNormalization() {
        MCVersion v1_18_2 = MCVersion.of("1.18.2");
        MCVersion v1_21_0 = MCVersion.of("1.21.0");

        // 1. Plural -> Singular on 1.21+
        String modernRecipe = DataPackResourcePathNormalizer.normalizePath("data/mymod/recipes/my_sword.json", v1_21_0);
        assertEquals("data/mymod/recipe/my_sword.json", modernRecipe);

        String modernTag = DataPackResourcePathNormalizer.normalizePath("data/mymod/tags/blocks/ores.json", v1_21_0);
        assertEquals("data/mymod/tags/block/ores.json", modernTag);

        String modernLoot = DataPackResourcePathNormalizer.normalizePath("data/mymod/loot_tables/blocks/ore.json", v1_21_0);
        assertEquals("data/mymod/loot_table/blocks/ore.json", modernLoot);

        // 2. Singular -> Plural on 1.18.2
        String legacyRecipe = DataPackResourcePathNormalizer.normalizePath("data/mymod/recipe/my_sword.json", v1_18_2);
        assertEquals("data/mymod/recipes/my_sword.json", legacyRecipe);

        String legacyTag = DataPackResourcePathNormalizer.normalizePath("data/mymod/tags/block/ores.json", v1_18_2);
        assertEquals("data/mymod/tags/blocks/ores.json", legacyTag);
    }

    @Test
    public void testJarBuilderNormalizesDatapackPathsInJar(@TempDir Path tempDir) throws IOException {
        BootstrapperConfig config = BootstrapperConfig.loadFromClasspath(getClass().getClassLoader());
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        ContinuumJarBuilder builder = new ContinuumJarBuilder(config, kb);

        Map<String, byte[]> mockFiles = new HashMap<>();
        mockFiles.put("data/examplemod/recipes/iron_sword.json", "{}".getBytes(StandardCharsets.UTF_8));
        mockFiles.put("data/examplemod/tags/blocks/custom_ores.json", "{}".getBytes(StandardCharsets.UTF_8));

        // Target: 1.21 NeoForge
        TargetSpec target121 = TargetSpec.of("1.21.0", "neoforge");
        File jarFile = builder.buildTargetJar(target121, mockFiles, tempDir.toFile());

        try (JarFile jar = new JarFile(jarFile)) {
            // Verify plural folders were converted to singular in the output JAR!
            assertNotNull(jar.getJarEntry("data/examplemod/recipe/iron_sword.json"), "Expected recipe/ in 1.21+ JAR");
            assertNotNull(jar.getJarEntry("data/examplemod/tags/block/custom_ores.json"), "Expected tags/block/ in 1.21+ JAR");
            assertNull(jar.getJarEntry("data/examplemod/recipes/iron_sword.json"), "recipes/ should NOT exist in 1.21+ JAR");
        }
    }
}
