package com.kyroxova.continuumlib.gradle;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ConsumerPluginTest {
    @TempDir Path project;
    @Test void discoversRulePacksInConsumerResourcesAndRejectsInvalidPacks() throws Exception {
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'consumer'");
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'com.kyroxova.continuumlib' }");
        Path rule = project.resolve("src/main/resources/data/continuumlib/knowledge/1.18.2/test.xml");
        Files.createDirectories(rule.getParent());
        String digest = "0".repeat(64);
        String xml = "<rules schema='1' id='fixture' evidence='Synthetic fixture'>"
                + "<source minecraft='1.18.2' loader='FORGE' namespace='MOJMAP' java='17'><artifact name='game' sha256='" + digest + "'/></source>"
                + "<target minecraft='1.20.1' loader='FORGE' namespace='MOJMAP' java='17'><artifact name='game' sha256='" + digest + "'/></target>"
                + "<class from='old/Block' to='new/Block'/></rules>";
        Files.writeString(rule, xml);
        GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments("continuumLibValidateRules").build();
        String report = Files.readString(project.resolve("build/reports/continuumlib/rule-packs.txt"));
        assertTrue(report.contains("ARTIFACTS_UNVERIFIED"));
        assertTrue(report.contains("fixture"));
        Files.writeString(rule, xml.replace("schema='1'", "schema='9'"));
        var failure = GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments("continuumLibValidateRules").buildAndFail();
        assertTrue(failure.getOutput().contains("Unsupported rule schema"));
    }
    @Test void consumerCanInspectItsOwnCompiledModWithoutChangingSources() throws Exception {
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'consumer'");
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java'; id 'com.kyroxova.continuumlib' }");
        Path source = project.resolve("src/main/java/example/CustomBlock.java");
        Files.createDirectories(source.getParent());
        String body = "package example; public class CustomBlock { public int shape(int n) { return Math.abs(n) & 15; } }";
        Files.writeString(source, body);
        var result = GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments("continuumLibInspect", "--stacktrace").build();
        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));
        String report = Files.readString(project.resolve("build/reports/continuumlib/api-inventory.tsv"));
        assertTrue(report.contains("java/lang/Math\tabs\t(I)I"));
        assertTrue(report.contains("example/CustomBlock\tshape\t(I)I"));
        assertTrue(report.contains("INVENTORY_ONLY"));
        assertEquals(body, Files.readString(source));
    }
}
