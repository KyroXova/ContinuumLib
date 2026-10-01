package com.kyroxova.continuumlib.gradle;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ExternalIntegrationTest {
    @TempDir Path project;
    @Test void externalInitScriptResolvesPublishedPluginWithoutLibraryCheckout() throws Exception {
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name='independent-mod'\ninclude 'mod', 'other'");
        Files.writeString(project.resolve("build.gradle"), "tasks.register('verifyScope') { doLast { assert tasks.findByName('continuumLibInspect') == null; assert project(':other').tasks.findByName('continuumLibInspect') == null } }");
        Files.createDirectories(project.resolve("mod")); Files.createDirectories(project.resolve("other"));
        Files.writeString(project.resolve("mod/build.gradle"), "plugins { id 'java' }");
        Files.writeString(project.resolve("other/build.gradle"), "plugins { id 'java' }");
        Path source = project.resolve("mod/src/main/java/example/Mod.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package example; public class Mod { public int value() { return 42; } }");
        Path request = project.resolve("transform.properties");
        Files.writeString(request, "# Inspection does not require a migration request\n");
        Path init = project.resolve("external-mod.init.gradle");
        try (var resource = getClass().getResourceAsStream("/integration/external-mod.init.gradle")) {
            assertNotNull(resource); Files.copy(resource, init);
        }
        String repository = Path.of(System.getProperty("continuumlib.testRepository")).toUri().toString();
        GradleRunner.create().withProjectDir(project.toFile()).withArguments(
                "--init-script", init.toString(), "-Dcontinuumlib.repository=" + repository,
                "-Dcontinuumlib.version=" + System.getProperty("continuumlib.testVersion"),
                "-Dcontinuumlib.config=" + request, "-Dcontinuumlib.project=:mod", ":mod:continuumLibInspect", "verifyScope", "--stacktrace").build();
        assertTrue(Files.readString(project.resolve("mod/build/reports/continuumlib/api-inventory.tsv")).contains("example/Mod"));
        assertEquals("plugins { id 'java' }", Files.readString(project.resolve("mod/build.gradle")));
    }
}
