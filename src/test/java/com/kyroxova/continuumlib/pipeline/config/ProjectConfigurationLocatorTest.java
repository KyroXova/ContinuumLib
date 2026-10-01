package com.kyroxova.continuumlib.pipeline.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectConfigurationLocatorTest {
    @Test
    void knowledgePacksCanCoexistAcrossCanonicalAndLegacyRoots(@TempDir Path project) throws Exception {
        Path canonicalKnowledge = project.resolve("src/main/resources/continuumlib/knowledge");
        Path legacyKnowledge = project.resolve("src/main/resources/data/continuumlib/knowledge");
        Files.createDirectories(canonicalKnowledge);
        Files.createDirectories(legacyKnowledge);
        Files.writeString(canonicalKnowledge.resolve("new.xml"), "<rules/>");
        Files.writeString(legacyKnowledge.resolve("old.xml"), "<rules/>");

        var discovered = new ProjectConfigurationLocator().locate(project);

        assertEquals(project.resolve("src/main/resources/continuumlib").toAbsolutePath().normalize(), discovered.root());
        assertFalse(discovered.isLegacy());
    }

    @Test
    void actualProjectConfigStillConflictsAcrossRoots(@TempDir Path project) throws Exception {
        Path canonical = project.resolve("src/main/resources/continuumlib");
        Path legacy = project.resolve("src/main/resources/data/continuumlib");
        Files.createDirectories(canonical);
        Files.createDirectories(legacy);
        Files.writeString(canonical.resolve("targets.properties"), "targets=a");
        Files.writeString(legacy.resolve("transform.properties"), "pack=test");

        assertThrows(ProjectConfigurationLocator.DualConfigurationException.class,
                () -> new ProjectConfigurationLocator().locate(project));
    }

    @Test
    void legacyConfigWinsOverCanonicalKnowledgeOnly(@TempDir Path project) throws Exception {
        Path canonicalKnowledge = project.resolve("src/main/resources/continuumlib/knowledge");
        Path legacy = project.resolve("src/main/resources/data/continuumlib");
        Files.createDirectories(canonicalKnowledge);
        Files.createDirectories(legacy);
        Files.writeString(canonicalKnowledge.resolve("rules.xml"), "<rules/>");
        Files.writeString(legacy.resolve("targets.properties"), "targets=a");

        var discovered = new ProjectConfigurationLocator().locate(project);

        assertEquals(legacy.toAbsolutePath().normalize(), discovered.root());
        assertTrue(discovered.isLegacy());
    }
}
