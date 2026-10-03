package com.kyroxova.continuumlib.gradle;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
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

    @Test void unifiedTargetGenerationBuildsConsumerJarWithoutChangingSource() throws Exception {
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'consumer'");
        Files.writeString(project.resolve("build.gradle"), """
                plugins { id 'com.kyroxova.continuumlib' }
                jar {
                    manifest {
                        attributes 'Implementation-Title': 'Continuum Consumer'
                    }
                }
                """);

        Path apiJar = project.resolve("api.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(apiJar));
             var objectClass = Object.class.getResourceAsStream("/java/lang/Object.class")) {
            assertNotNull(objectClass);
            jar.putNextEntry(new JarEntry("java/lang/Object.class"));
            objectClass.transferTo(jar);
            jar.closeEntry();
        }
        String digest = sha256(apiJar);

        Path config = project.resolve("src/main/resources/continuumlib");
        Path targets = config.resolve("targets");
        Path knowledge = config.resolve("knowledge");
        Files.createDirectories(targets);
        Files.createDirectories(knowledge);

        Files.writeString(config.resolve("targets.properties"), """
                targets=demo
                perVersion=true
                universal=false
                """);
        Files.writeString(targets.resolve("demo.properties"), """
                pack=fixture
                source.api=api.jar
                target.api=api.jar
                """);
        Files.writeString(knowledge.resolve("fixture.xml"),
                "<rules schema='1' id='fixture' evidence='Synthetic fixture'>"
                        + "<source minecraft='1.20.1' loader='VANILLA' namespace='MOJMAP' java='17'>"
                        + "<artifact name='api' sha256='" + digest + "'/></source>"
                        + "<target minecraft='1.20.1' loader='VANILLA' namespace='MOJMAP' java='17'>"
                        + "<artifact name='api' sha256='" + digest + "'/></target>"
                        + "</rules>");

        Path source = project.resolve("src/main/java/example/Example.java");
        Files.createDirectories(source.getParent());
        String original = "package example; public class Example { public int value() { return 7; } }";
        Files.writeString(source, original);

        Path resource = project.resolve("src/main/resources/assets/example/value.txt");
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, "resource-value");

        var result = GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments("continuumLibGenerate_demo", "--stacktrace").build();

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"), result.getOutput());
        assertEquals(original, Files.readString(source));

        Path output = project.resolve("build/continuumlib/demo.jar");
        assertTrue(Files.isRegularFile(output));
        try (JarFile jar = new JarFile(output.toFile())) {
            assertNotNull(jar.getJarEntry("example/Example.class"));
            assertNotNull(jar.getJarEntry("assets/example/value.txt"));
            assertNull(jar.getJarEntry("continuumlib/targets.properties"));
            assertEquals(
                    "Continuum Consumer",
                    jar.getManifest().getMainAttributes().getValue("Implementation-Title")
            );
        }
    }

    @Test
    void newProjectsDefaultTaskConfigPathsToCanonicalRoot() throws Exception {
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'consumer'");
        Files.writeString(project.resolve("build.gradle"), """
                plugins { id 'com.kyroxova.continuumlib' }

                tasks.register('printContinuumConfigPaths') {
                    doLast {
                        def transform = tasks.named('continuumLibTransformSource').get()
                        def targets = tasks.named('continuumLibValidateOutputModes').get()
                        println('TRANSFORM_CONFIG=' + project.relativePath(transform.configFile.get().asFile))
                        println('TARGETS_CONFIG=' + project.relativePath(targets.configFile.get().asFile))
                    }
                }
                """);

        var result = GradleRunner.create()
                .withProjectDir(project.toFile())
                .withPluginClasspath()
                .withArguments("printContinuumConfigPaths")
                .build();

        assertTrue(result.getOutput().contains(
                "TRANSFORM_CONFIG=src/main/resources/continuumlib/transform.properties"),
                result.getOutput());
        assertTrue(result.getOutput().contains(
                "TARGETS_CONFIG=src/main/resources/continuumlib/targets.properties"),
                result.getOutput());
        assertFalse(result.getOutput().contains(
                "src/main/resources/data/continuumlib/transform.properties"),
                result.getOutput());
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(Files.readAllBytes(file));
        return HexFormat.of().formatHex(digest.digest());
    }


}
