package com.kyroxova.continuumlib.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GeneratedWorkspaceTest {
    @Test
    void isolatesTargetsAndPreservesLastSuccessfulOutput(@TempDir Path buildDir) throws Exception {
        var first = GeneratedWorkspace.under(buildDir.resolve("continuum"), "forge-1.20.1", "mod.jar");
        var second = GeneratedWorkspace.under(buildDir.resolve("continuum"), "neoforge-1.21.1", "mod.jar");

        assertNotEquals(first.root(), second.root());
        assertTrue(first.sourceDirectory().startsWith(first.root()));
        assertTrue(second.classesDirectory().startsWith(second.root()));

        Files.createDirectories(first.sourceDirectory());
        Files.writeString(first.sourceDirectory().resolve("stale.java"), "stale");
        Files.createDirectories(first.outputDirectory());
        Files.writeString(first.outputJar(), "last-good");

        first.prepare();

        assertFalse(Files.exists(first.sourceDirectory().resolve("stale.java")));
        assertEquals("last-good", Files.readString(first.outputJar()));
        assertTrue(Files.isDirectory(first.resourcesDirectory()));
        assertTrue(Files.isDirectory(first.classesDirectory()));
    }

    @Test
    void rejectsUnsafeTargetAndOutputNames(@TempDir Path buildDir) {
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedWorkspace.under(buildDir, "../escape", "mod.jar"));
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedWorkspace.under(buildDir, "safe", "../mod.jar"));
    }
}
