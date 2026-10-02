package com.kyroxova.continuumlib.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GeneratedWorkspaceTest {
    @Test
    void prepareCleansTransientStateAndPreservesLastGoodOutput(@TempDir Path root) throws Exception {
        GeneratedWorkspace workspace = new GeneratedWorkspace(root.resolve("build"), "forge-1.20.1");
        workspace.init();

        Files.writeString(workspace.sourceDir().resolve("stale.java"), "stale");
        Files.writeString(workspace.resourcesDir().resolve("stale.json"), "stale");
        Files.writeString(workspace.classesDir().resolve("stale.class"), "stale");
        Files.writeString(workspace.reportsDir().resolve("old.txt"), "stale");
        Files.writeString(workspace.metadataDir().resolve("old.txt"), "stale");
        Files.writeString(workspace.stagingDir().resolve("partial.jar"), "partial");
        Files.writeString(workspace.finalJar("mod.jar"), "last-good");

        workspace.prepare();

        assertFalse(Files.exists(workspace.sourceDir().resolve("stale.java")));
        assertFalse(Files.exists(workspace.resourcesDir().resolve("stale.json")));
        assertFalse(Files.exists(workspace.classesDir().resolve("stale.class")));
        assertFalse(Files.exists(workspace.reportsDir().resolve("old.txt")));
        assertFalse(Files.exists(workspace.metadataDir().resolve("old.txt")));
        assertFalse(Files.exists(workspace.stagingDir().resolve("partial.jar")));
        assertEquals("last-good", Files.readString(workspace.finalJar("mod.jar")));
    }

    @Test
    void normalizesTargetIdBeforeBuildingWorkspacePath(@TempDir Path root) {
        GeneratedWorkspace workspace = new GeneratedWorkspace(root.resolve("build"), "  forge-1.20.1  ");

        assertEquals("forge-1.20.1", workspace.targetId());
        assertEquals(
                root.resolve("build/continuum/targets/forge-1.20.1").toAbsolutePath().normalize(),
                workspace.rootDir()
        );
    }

    @Test
    void cleanStagingRemovesPartialArtifacts(@TempDir Path root) throws Exception {
        GeneratedWorkspace workspace = new GeneratedWorkspace(root.resolve("build"), "target");
        workspace.init();
        Path partial = workspace.stagingDir().resolve("partial.jar");
        Files.writeString(partial, "partial");

        workspace.cleanStaging();

        assertFalse(Files.exists(partial));
        assertTrue(Files.isDirectory(workspace.stagingDir()));
    }

    @Test
    void rejectsUnsafeArtifactNames(@TempDir Path root) {
        GeneratedWorkspace workspace = new GeneratedWorkspace(root.resolve("build"), "target");

        assertThrows(IllegalArgumentException.class, () -> workspace.stagingJar("../escape.jar"));
        assertThrows(IllegalArgumentException.class, () -> workspace.finalJar("nested/mod.jar"));
        assertThrows(IllegalArgumentException.class, () -> workspace.finalJar("CON.jar"));
    }
}
