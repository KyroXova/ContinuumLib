package com.kyroxova.continuumlib.gradle;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

class PluginPackagingTest {
    @Test
    void publishedPluginRelocatesParserDependencies() throws Exception {
        String configured = System.getProperty("continuumlib.shadowJar");
        assertNotNull(configured, "Shadow JAR path must be supplied by the Gradle test task");

        Path jarPath = Path.of(configured);
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            assertNotNull(jar.getEntry(
                    "com/kyroxova/continuumlib/internal/javaparser/JavaParser.class"));
            assertNotNull(jar.getEntry(
                    "com/kyroxova/continuumlib/internal/javaparser/symbolsolver/JavaSymbolSolver.class"));
            assertNull(jar.getEntry("com/github/javaparser/JavaParser.class"));
            assertNull(jar.getEntry(
                    "com/github/javaparser/symbolsolver/JavaSymbolSolver.class"));
        }
    }
}
