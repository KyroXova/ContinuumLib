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
    @Test void targetGenerationTracksConsumerResourcesAsInputs() throws Exception {
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'consumer'");
        Files.writeString(project.resolve("build.gradle"), """
                plugins { id 'com.kyroxova.continuumlib' }
                sourceSets {
                    main {
                        java {
                            srcDir 'extra-java'
                        }
                        resources {
                            srcDir 'extra-resources'
                        }
                    }
                }
                tasks.register('printContinuumResourceInputs') {
                    doLast {
                        ['continuumLibGenerate_demo', 'continuumLibTransformSource'].each { taskName ->
                            def task = tasks.named(taskName).get()
                            task.sourceRoots.files.each {
                                println(taskName + ':SOURCE_ROOT=' + project.relativePath(it))
                            }
                            task.resourceFiles.files.each {
                                println(taskName + ':RESOURCE_INPUT=' + project.relativePath(it))
                            }
                            task.resourceRoots.files.each {
                                println(taskName + ':RESOURCE_ROOT=' + project.relativePath(it))
                            }
                        }
                    }
                }
                """);

        Path config = project.resolve("src/main/resources/continuumlib");
        Files.createDirectories(config.resolve("inclusions"));
        Files.writeString(config.resolve("targets.properties"),
                "targets=demo\nperVersion=true\nuniversal=false\n");
        Files.writeString(config.resolve("inclusions/source.json"), "{\"rules\":[]}");
        Path extraSource = project.resolve("extra-java/example/Extra.java");
        Files.createDirectories(extraSource.getParent());
        Files.writeString(extraSource, "package example; public class Extra {}");

        Path asset = project.resolve("src/main/resources/assets/example/model.json");
        Files.createDirectories(asset.getParent());
        Files.writeString(asset, "{}");
        Path extraAsset = project.resolve("extra-resources/assets/example/extra.json");
        Files.createDirectories(extraAsset.getParent());
        Files.writeString(extraAsset, "{}");

        var result = GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments("printContinuumResourceInputs").build();

        assertTrue(result.getOutput().contains(
                "continuumLibGenerate_demo:SOURCE_ROOT=src/main/java"));
        assertTrue(result.getOutput().contains(
                "continuumLibGenerate_demo:SOURCE_ROOT=extra-java"));
        assertTrue(result.getOutput().contains(
                "continuumLibTransformSource:SOURCE_ROOT=extra-java"));
        assertTrue(result.getOutput().contains(
                "continuumLibGenerate_demo:RESOURCE_INPUT=src/main/resources/assets/example/model.json"));
        assertTrue(result.getOutput().contains(
                "continuumLibGenerate_demo:RESOURCE_INPUT=src/main/resources/continuumlib/inclusions/source.json"));
        assertTrue(result.getOutput().contains(
                "continuumLibTransformSource:RESOURCE_INPUT=src/main/resources/assets/example/model.json"));
        assertTrue(result.getOutput().contains(
                "continuumLibGenerate_demo:RESOURCE_INPUT=extra-resources/assets/example/extra.json"));
        assertTrue(result.getOutput().contains(
                "continuumLibTransformSource:RESOURCE_INPUT=extra-resources/assets/example/extra.json"));
        assertTrue(result.getOutput().contains(
                "continuumLibGenerate_demo:RESOURCE_ROOT=src/main/resources"));
        assertTrue(result.getOutput().contains(
                "continuumLibGenerate_demo:RESOURCE_ROOT=extra-resources"));
        assertTrue(result.getOutput().contains(
                "continuumLibTransformSource:RESOURCE_ROOT=extra-resources"));
    }

}
