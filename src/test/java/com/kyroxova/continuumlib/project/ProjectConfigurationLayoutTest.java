package com.kyroxova.continuumlib.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectConfigurationLayoutTest {
    @Test
    void prefersCanonicalConfigurationAndAllowsSplitDomains(@TempDir Path project) throws Exception {
        var layout = ProjectConfigurationLayout.discover(project);
        Files.createDirectories(layout.canonicalRoot().resolve("inclusions/source"));
        Files.createDirectories(layout.legacyRoot().resolve("knowledge"));
        Files.writeString(layout.legacyRoot().resolve("knowledge/example.xml"), "<rules/>");

        assertEquals(layout.canonicalRoot(), layout.filterConfigurationRoot());
        assertEquals(layout.legacyRoot().resolve("knowledge"), layout.resolveDirectory("knowledge"));
        assertEquals(layout.canonicalRoot().resolve("transform.properties"), layout.resolveFile("transform.properties"));
    }

    @Test
    void fallsBackToLegacyFilters(@TempDir Path project) throws Exception {
        var layout = ProjectConfigurationLayout.discover(project);
        Files.createDirectories(layout.legacyRoot().resolve("exclusions/source"));

        assertEquals(layout.legacyRoot(), layout.filterConfigurationRoot());
    }

    @Test
    void rejectsAmbiguousDefinitions(@TempDir Path project) throws Exception {
        var layout = ProjectConfigurationLayout.discover(project);
        Files.createDirectories(layout.canonicalRoot().resolve("inclusions"));
        Files.createDirectories(layout.legacyRoot().resolve("exclusions"));
        assertThrows(IllegalStateException.class, layout::filterConfigurationRoot);

        Files.createDirectories(layout.canonicalRoot().resolve("knowledge"));
        Files.createDirectories(layout.legacyRoot().resolve("knowledge"));
        assertThrows(IllegalStateException.class, () -> layout.resolveDirectory("knowledge"));
    }
}
