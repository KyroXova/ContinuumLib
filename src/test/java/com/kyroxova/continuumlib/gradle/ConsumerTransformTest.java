package com.kyroxova.continuumlib.gradle;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Collections;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;

class ConsumerTransformTest {
    @TempDir Path project;
    private Path api(String file, String methodName, int multiplier) throws Exception {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "fixture/Api", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, methodName, "(I)I", null, null);
        method.visitCode(); method.visitVarInsn(Opcodes.ILOAD, 0); method.visitLdcInsn(multiplier);
        method.visitInsn(Opcodes.IMUL); method.visitInsn(Opcodes.IRETURN); method.visitMaxs(2, 1); method.visitEnd();
        writer.visitField(methodName.equals("oldCall") ? Opcodes.ACC_PUBLIC : Opcodes.ACC_PRIVATE, "value", "I", null, null).visitEnd();
        var constructor = writer.visitMethod(methodName.equals("oldCall") ? Opcodes.ACC_PUBLIC : Opcodes.ACC_PRIVATE, "<init>", "(I)V", null, null);
        constructor.visitCode(); constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitVarInsn(Opcodes.ALOAD, 0); constructor.visitVarInsn(Opcodes.ILOAD, 1); constructor.visitLdcInsn(multiplier);
        constructor.visitInsn(Opcodes.IMUL); constructor.visitFieldInsn(Opcodes.PUTFIELD, "fixture/Api", "value", "I");
        constructor.visitInsn(Opcodes.RETURN); constructor.visitMaxs(3, 2); constructor.visitEnd();
        if (!methodName.equals("oldCall")) {
            var accessor = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "readValue", "(Lfixture/Api;)I", null, null);
            accessor.visitCode(); accessor.visitVarInsn(Opcodes.ALOAD, 0);
            accessor.visitFieldInsn(Opcodes.GETFIELD, "fixture/Api", "value", "I");
            accessor.visitInsn(Opcodes.IRETURN); accessor.visitMaxs(1, 1); accessor.visitEnd();
            var factory = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create", "(I)Lfixture/Api;", null, null);
            factory.visitCode(); factory.visitTypeInsn(Opcodes.NEW, "fixture/Api"); factory.visitInsn(Opcodes.DUP);
            factory.visitVarInsn(Opcodes.ILOAD, 0); factory.visitMethodInsn(Opcodes.INVOKESPECIAL, "fixture/Api", "<init>", "(I)V", false);
            factory.visitInsn(Opcodes.ARETURN); factory.visitMaxs(3, 1); factory.visitEnd();
        }
        writer.visitEnd();
        Path path = project.resolve(file);
        try (var jar = new JarOutputStream(Files.newOutputStream(path))) {
            jar.putNextEntry(new JarEntry("fixture/Api.class")); jar.write(writer.toByteArray()); jar.closeEntry();
        }
        return path;
    }
    private String digest(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
    private GradleRunner runner(boolean published) {
        var runner = GradleRunner.create().withProjectDir(project.toFile());
        return published ? runner : runner.withPluginClasspath();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void buildsExecutableTargetJarFromConsumerConfigurationAndInvalidatesChangedArtifacts(boolean published) throws Exception {
        Path oldApi = api("old.jar", "oldCall", 2), newApi = api("new.jar", "newCall", 3);
        String repository = Path.of(System.getProperty("continuumlib.testRepository", "build/test-repository")).toAbsolutePath().toUri().toString();
        Files.writeString(project.resolve("settings.gradle"), (published
                ? "pluginManagement { repositories { maven { url = uri('" + repository + "') }; mavenCentral() } }\n" : "")
                + "rootProject.name = 'consumer'");
        String version = System.getProperty("continuumlib.testVersion", "1.0.0");
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'com.kyroxova.continuumlib'"
                + (published ? " version '" + version + "'" : "") + " }\ndependencies { implementation files('old.jar') }\n"
                + "tasks.register('checkRuntimeIsolation') { doLast { assert configurations.runtimeClasspath.files*.name == ['old.jar'] } }\n");
        Path java = project.resolve("src/main/java/example/CustomBlock.java");
        Files.createDirectories(java.getParent());
        String source = "package example; public class CustomBlock { public int shape() { return fixture.Api.oldCall(4) + 7; } public int factory() { return new fixture.Api(4).value + 7; } }";
        Files.writeString(java, source);
        Path config = project.resolve("src/main/resources/data/continuumlib");
        Files.createDirectories(config.resolve("knowledge"));
        Files.writeString(config.resolve("transform.properties"), "pack=fixture\nsource.api=old.jar\ntarget.api=new.jar\n");
        Files.writeString(config.resolve("knowledge/fixture.xml"), "<rules schema='1' id='fixture' evidence='Executable synthetic fixture'>"
                + "<source minecraft='1.18.2' loader='FORGE' namespace='MOJMAP' java='17'><artifact name='api' sha256='" + digest(oldApi) + "'/></source>"
                + "<target minecraft='1.20.1' loader='FORGE' namespace='MOJMAP' java='17'><artifact name='api' sha256='" + digest(newApi) + "'/></target>"
                + "<member from-owner='fixture/Api' from-name='oldCall' from-descriptor='(I)I' to-owner='fixture/Api' to-name='newCall' to-descriptor='(I)I'/>"
                + "<bridge opcode='GETFIELD' from-owner='fixture/Api' from-name='value' from-descriptor='I' to-owner='fixture/Api' to-name='readValue' to-descriptor='(Lfixture/Api;)I'/>"
                + "<constructor-factory from-owner='fixture/Api' from-name='&lt;init&gt;' from-descriptor='(I)V' to-owner='fixture/Api' to-name='create' to-descriptor='(I)Lfixture/Api;'/></rules>");
        var result = runner(published)
                .withArguments("continuumLibCompareApis", "continuumLibAuditTarget", "checkRuntimeIsolation", "--stacktrace").build();
        assertTrue(result.getOutput().contains("NOT_CERTIFIED"));
        String audit = Files.readString(project.resolve("build/reports/continuumlib/target-audit.tsv"));
        assertTrue(audit.contains("DECLARATION_FOUND\texample/CustomBlock\tshape\t()I"));
        assertTrue(audit.contains("fixture/Api\tnewCall\t(I)I"));
        assertTrue(audit.contains("OWNER_MISSING\texample/CustomBlock\t<init>"));
        String types = Files.readString(project.resolve("build/reports/continuumlib/target-types.tsv"));
        assertTrue(types.contains("TYPE_PRESENT\texample/CustomBlock\tfixture/Api"));
        assertTrue(types.contains("TYPE_MISSING\texample/CustomBlock\tjava/lang/Object"));
        String delta = Files.readString(project.resolve("build/reports/continuumlib/api-delta.tsv"));
        assertTrue(delta.contains("METHOD_REMOVED\tfixture/Api\toldCall\t(I)I"));
        assertTrue(delta.contains("METHOD_ADDED\tfixture/Api\tnewCall\t(I)I"));
        Path output = project.resolve("build/continuumlib/consumer-transformed.jar");
        try (var jar = new JarFile(output.toFile())) {
            assertTrue(jar.stream().noneMatch(entry -> entry.getName().startsWith("com/kyroxova/continuumlib/") || entry.getName().endsWith(".jar")));
        }
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{output.toUri().toURL(), newApi.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass("com.kyroxova.continuumlib.gradle.ContinuumLibPlugin"));
            var type = loader.loadClass("example.CustomBlock");
            assertEquals(19, type.getMethod("shape").invoke(type.getConstructor().newInstance()));
            assertEquals(19, type.getMethod("factory").invoke(type.getConstructor().newInstance()));
        }
        assertEquals(source, Files.readString(java));
        String buildScript = Files.readString(project.resolve("build.gradle"));
        Files.writeString(project.resolve("build.gradle"), buildScript
                + "\ntasks.named('continuumLibAuditTarget') { failOnUnresolved.set(true) }\n");
        var unresolved = runner(published).withArguments("continuumLibAuditTarget").buildAndFail();
        assertTrue(unresolved.getOutput().contains("ContinuumLib strict declaration audit failed"));
        assertTrue(Files.readString(project.resolve("build/reports/continuumlib/target-audit.tsv")).contains("OWNER_MISSING"));
        // This fixture runs on the same Java version as the test. Real consumers must supply
        // declarations from their target runtime, not assume Gradle's JDK is the target JDK.
        Path completeApi = project.resolve("complete-api.jar");
        try (var complete = new JarOutputStream(Files.newOutputStream(completeApi));
             var object = Object.class.getResourceAsStream("/java/lang/Object.class")) {
            complete.putNextEntry(new JarEntry("java/lang/Object.class")); object.transferTo(complete); complete.closeEntry();
        }
        String ruleText = Files.readString(config.resolve("knowledge/fixture.xml"));
        String requestText = Files.readString(config.resolve("transform.properties"));
        Files.writeString(config.resolve("transform.properties"), requestText
                + "classpath.target.runtime.file=complete-api.jar\nclasspath.target.runtime.sha256=" + digest(completeApi) + "\n");
        runner(published).withArguments("continuumLibAuditTarget").build();
        assertFalse(Files.readString(project.resolve("build/reports/continuumlib/target-types.tsv")).contains("TYPE_MISSING"));
        byte[] verifiedDependency = Files.readAllBytes(completeApi);
        Files.write(completeApi, new byte[]{0});
        var changedDependency = runner(published).withArguments("continuumLibAuditTarget").buildAndFail();
        assertTrue(changedDependency.getOutput().contains("Classpath SHA-256 mismatch"));
        Files.write(completeApi, verifiedDependency);
        try (var empty = new JarOutputStream(Files.newOutputStream(project.resolve("empty.jar")))) { }
        Files.writeString(project.resolve("build.gradle"), buildScript
                + "\ntasks.named('continuumLibAuditTarget') { failOnUnresolved.set(true); inputJar.set(layout.projectDirectory.file('empty.jar')) }\n");
        var emptyAudit = runner(published).withArguments("continuumLibAuditTarget").buildAndFail();
        assertTrue(emptyAudit.getOutput().contains("0 inspected classes"));
        Files.writeString(config.resolve("knowledge/fixture.xml"), ruleText);
        Files.writeString(config.resolve("transform.properties"), requestText);
        Files.writeString(project.resolve("build.gradle"), buildScript);
        Files.createDirectories(config.resolve("targets"));
        String request = Files.readString(config.resolve("transform.properties"));
        Files.writeString(config.resolve("targets/first.properties"), request);
        Files.writeString(config.resolve("targets/second.properties"), request);
        Files.writeString(config.resolve("targets.properties"), "targets=first,second\nperVersion=true\nuniversal=false\n");
        runner(published)
                .withArguments("continuumLibBuildTargets", "continuumLibAuditTargets", "--configuration-cache").build();
        assertTrue(Files.isRegularFile(project.resolve("build/continuumlib/first.jar")));
        assertTrue(Files.isRegularFile(project.resolve("build/continuumlib/second.jar")));
        assertTrue(Files.readString(project.resolve("build/reports/continuumlib/first-audit.tsv")).contains("fixture/Api\tnewCall\t(I)I"));
        assertTrue(Files.readString(project.resolve("build/reports/continuumlib/second-audit.tsv")).contains("DECLARATION_FOUND"));
        Files.writeString(config.resolve("targets.properties"), "targets=first,second\nperVersion=true\nuniversal=true\n");
        var unsupported = runner(published)
                .withArguments("continuumLibBuildTargets").buildAndFail();
        assertTrue(unsupported.getOutput().contains("Universal output is not supported yet"));
        assertFalse(Files.exists(project.resolve("build/continuumlib/universal.jar")));
        byte[] goodOutput = Files.readAllBytes(output);
        api("new.jar", "newCall", 4);
        var failure = runner(published)
                .withArguments("continuumLibTransformJar").buildAndFail();
        assertTrue(failure.getOutput().contains("SHA-256 mismatch"));
        assertArrayEquals(goodOutput, Files.readAllBytes(output));
    }
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sourceTransformTaskUsesUnifiedDescriptorAwareBridgePipeline(boolean published) throws Exception {
        Path oldApi = api("old-source.jar", "oldCall", 2);
        Path newApi = api("new-source.jar", "newCall", 3);

        String repository = Path.of(System.getProperty("continuumlib.testRepository", "build/test-repository"))
                .toAbsolutePath().toUri().toString();
        Files.writeString(project.resolve("settings.gradle"), (published
                ? "pluginManagement { repositories { maven { url = uri('" + repository + "') }; mavenCentral() } }\n"
                : "") + "rootProject.name = 'consumer'");
        String version = System.getProperty("continuumlib.testVersion", "1.0.0");
        Files.writeString(project.resolve("build.gradle"),
                "plugins { id 'com.kyroxova.continuumlib'"
                        + (published ? " version '" + version + "'" : "") + " }\n"
                        + "dependencies { implementation files('old-source.jar') }\n");

        Path java = project.resolve("src/main/java/example/SourceAdapted.java");
        Files.createDirectories(java.getParent());
        String source = "package example; public class SourceAdapted { "
                + "record Holder(int value) {} "
                + "public int value() { return new fixture.Api(4).value + new Holder(7).value(); } }";
        Files.writeString(java, source);

        Path config = project.resolve("src/main/resources/continuumlib");
        Files.createDirectories(config.resolve("knowledge"));
        Files.writeString(config.resolve("transform.properties"),
                "pack=source-fixture\nsource.api=old-source.jar\ntarget.api=new-source.jar\n");
        Files.writeString(config.resolve("knowledge/source-fixture.xml"),
                "<rules schema='1' id='source-fixture' evidence='Unified source fixture'>"
                        + "<source minecraft='1.18.2' loader='FORGE' namespace='MOJMAP' java='17'>"
                        + "<artifact name='api' sha256='" + digest(oldApi) + "'/></source>"
                        + "<target minecraft='1.20.1' loader='FORGE' namespace='MOJMAP' java='17'>"
                        + "<artifact name='api' sha256='" + digest(newApi) + "'/></target>"
                        + "<bridge opcode='GETFIELD' from-owner='fixture/Api' from-name='value' from-descriptor='I' "
                        + "to-owner='fixture/Api' to-name='readValue' to-descriptor='(Lfixture/Api;)I'/>"
                        + "<constructor-factory from-owner='fixture/Api' from-name='&lt;init&gt;' from-descriptor='(I)V' "
                        + "to-owner='fixture/Api' to-name='create' to-descriptor='(I)Lfixture/Api;'/></rules>");

        var result = runner(published)
                .withArguments("continuumLibTransformSource", "--stacktrace")
                .build();

        assertTrue(result.getOutput().contains("unified target pipeline"));
        assertEquals(source, Files.readString(java));

        Path generated = project.resolve("build/continuum/generated-src/example/SourceAdapted.java");
        String generatedText = Files.readString(generated);
        assertTrue(generatedText.contains("Api.create(4)"), generatedText);
        assertTrue(generatedText.contains("Api.readValue("), generatedText);
        assertTrue(generatedText.contains("record Holder"), generatedText);

        Path output = project.resolve("build/continuumlib/consumer-source-adapted.jar");
        assertTrue(Files.isRegularFile(output));
        try (var loader = new java.net.URLClassLoader(
                new java.net.URL[]{output.toUri().toURL(), newApi.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            var type = loader.loadClass("example.SourceAdapted");
            assertEquals(19, type.getMethod("value").invoke(type.getConstructor().newInstance()));
        }
    }

}
