package com.kyroxova.continuumlib.gradle;

import com.kyroxova.continuumlib.bytecode.*;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real artifact-backed Gradle pipeline. No game launch/loader compatibility claim. */
class MinecraftConsumerTest {
    @TempDir Path project;
    static final String ID = "net/minecraft/resources/ResourceLocation";
    @Test void writesJarWithActual1211FactoryCallsUsingVerifiedObfuscatedApiMappings() throws Exception {
        String root = System.getProperty("continuumlib.minecraftArtifacts");
        Assumptions.assumeTrue(root != null, "Supply -PminecraftArtifacts=ref/artifacts");
        Path artifacts = Path.of(root).toAbsolutePath();
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name='minecraft-consumer'");
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'com.kyroxova.continuumlib' }\ntasks.named('continuumLibTransformJar') { inputJar.set(layout.projectDirectory.file('input.jar')) }\n");
        try (var jar = new JarOutputStream(Files.newOutputStream(project.resolve("input.jar")))) {
            jar.putNextEntry(new JarEntry("example/CustomCode.class")); jar.write(caller()); jar.closeEntry();
        }
        Path config = project.resolve("src/main/resources/data/continuumlib/transform.properties");
        Files.createDirectories(config.getParent());
        String properties = "pack=vanilla-identifiers-1.20.1-to-1.21.1\nsource.minecraft=" + path(artifacts.resolve("minecraft-1.20.1-server-extracted.jar"))
                + "\ntarget.minecraft=" + path(artifacts.resolve("minecraft-1.21.1-server-extracted.jar")) + "\n"
                + mapping("source", artifacts, "1.20.1", "dca153d20defb32cfac3f069c3bf77b3e13c30ae63847637479d3137e099bb72")
                + mapping("target", artifacts, "1.21.1", "9d0b04bead421c8229aff14b534432bbc927bea642e7c8593d1276b8df8ba53f");
        Files.writeString(config, properties);
        GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments("continuumLibAuditTarget", "--stacktrace").build();
        try (var jar = new JarFile(project.resolve("build/continuumlib/minecraft-consumer-transformed.jar").toFile())) {
            byte[] bytes;
            try (var input = jar.getInputStream(jar.getJarEntry("example/CustomCode.class"))) { bytes = input.readAllBytes(); }
            var references = new ReferenceScanner().scan(bytes);
            assertEquals("fromNamespaceAndPath", references.get(0).target().name());
            assertEquals("getPath", references.get(1).target().name());
            assertTrue(references.stream().noneMatch(r -> r.target().name().equals("<init>")));
        }
        String report = Files.readString(project.resolve("build/reports/continuumlib/target-audit.tsv"));
        assertTrue(report.contains("fromNamespaceAndPath"));
        assertFalse(report.contains("OWNER_MISSING"));
        assertFalse(report.contains("MEMBER_MISSING"));
        Path named1211 = project.resolve("input-1.21.1.jar");
        Files.copy(project.resolve("build/continuumlib/minecraft-consumer-transformed.jar"), named1211);
        // Opt-in output namespace must rewrite the same JAR to actual runtime symbol names.
        Files.writeString(config, properties + "output.namespace=OBFUSCATED\n");
        GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments("continuumLibAuditTarget", "--stacktrace").build();
        try (var jar = new JarFile(project.resolve("build/continuumlib/minecraft-consumer-transformed.jar").toFile());
             var stream = jar.getInputStream(jar.getJarEntry("example/CustomCode.class"))) {
            var references = new ReferenceScanner().scan(stream.readAllBytes());
            assertEquals(new MemberReference("akr", "a", "(Ljava/lang/String;Ljava/lang/String;)Lakr;"), references.get(0).target());
            assertEquals(new MemberReference("akr", "a", "()Ljava/lang/String;"), references.get(1).target());
        }
        assertFalse(Files.readString(project.resolve("build/reports/continuumlib/target-audit.tsv")).contains("OWNER_MISSING"));
        String targetJava = System.getProperty("continuumlib.minecraftJava21");
        if (targetJava != null) {
            var classpath = new ArrayList<String>();
            classpath.add(project.resolve("build/continuumlib/minecraft-consumer-transformed.jar").toString());
            classpath.add(artifacts.resolve("minecraft-1.21.1-server-extracted.jar").toString());
            classpath.add(Path.of(RuntimeJarProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
            Path libraries = project.resolve("target-libraries");
            try (var bundle = new JarFile(artifacts.resolve("minecraft-1.21.1-server.jar").toFile())) {
                for (var entry : Collections.list(bundle.entries())) {
                    if (!entry.getName().startsWith("META-INF/libraries/") || !entry.getName().endsWith(".jar")) continue;
                    Path destination = libraries.resolve(entry.getName().substring("META-INF/libraries/".length())).normalize();
                    if (!destination.startsWith(libraries)) throw new IllegalArgumentException("Unsafe bundle library path");
                    Files.createDirectories(destination.getParent());
                    try (var stream = bundle.getInputStream(entry)) { Files.copy(stream, destination); }
                    classpath.add(destination.toString());
                }
            }
            Path log = project.resolve("target-execution.log");
            var process = new ProcessBuilder(targetJava, "-cp", String.join(java.io.File.pathSeparator, classpath), RuntimeJarProbe.class.getName(),
                    "example.CustomCode", "path", "custom_path").redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if (!process.waitFor(45, java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Target Java process timed out"); }
            assertEquals(0, process.exitValue(), Files.readString(log));
            assertTrue(Files.readString(log).contains("TARGET_EXECUTION_OK:custom_path"));
        }
        String modern = System.getProperty("continuumlib.testArtifact");
        if (modern != null) {
            Files.writeString(project.resolve("build.gradle"), Files.readString(project.resolve("build.gradle"))
                    + "\ntasks.named('continuumLibTransformJar') { inputJar.set(layout.projectDirectory.file('input-1.21.1.jar')) }\n");
            Files.writeString(config, "pack=vanilla-identifiers-1.21.1-to-26.3\nsource.minecraft="
                    + path(artifacts.resolve("minecraft-1.21.1-server-extracted.jar")) + "\ntarget.minecraft=" + path(Path.of(modern).toAbsolutePath()) + "\n"
                    + mapping("source", artifacts, "1.21.1", "9d0b04bead421c8229aff14b534432bbc927bea642e7c8593d1276b8df8ba53f"));
            GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath().withArguments("continuumLibAuditTarget", "--stacktrace").build();
            try (var jar = new JarFile(project.resolve("build/continuumlib/minecraft-consumer-transformed.jar").toFile());
                 var stream = jar.getInputStream(jar.getJarEntry("example/CustomCode.class"))) {
                var references = new ReferenceScanner().scan(stream.readAllBytes());
                assertEquals(new MemberReference("net/minecraft/resources/Identifier", "fromNamespaceAndPath",
                        "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/Identifier;"), references.get(0).target());
                assertEquals(new MemberReference("net/minecraft/resources/Identifier", "getPath", "()Ljava/lang/String;"), references.get(1).target());
            }
            assertFalse(Files.readString(project.resolve("build/reports/continuumlib/target-audit.tsv")).contains("OWNER_MISSING"));
        }
    }
    @Test void rejectsFinalNamespaceCollisionWithoutReplacingOutput() throws Exception {
        String root = System.getProperty("continuumlib.minecraftArtifacts");
        Assumptions.assumeTrue(root != null, "Supply -PminecraftArtifacts=ref/artifacts");
        Path artifacts = Path.of(root).toAbsolutePath();
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name='collision-consumer'");
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'com.kyroxova.continuumlib' }\ntasks.named('continuumLibTransformJar') { inputJar.set(layout.projectDirectory.file('input.jar')) }\n");
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "example/Safe", null, "java/lang/Object", null); writer.visitEnd();
        try (var jar = new JarOutputStream(Files.newOutputStream(project.resolve("input.jar")))) {
            jar.putNextEntry(new JarEntry("example/Safe.class")); jar.write(writer.toByteArray()); jar.closeEntry();
        }
        Path config = project.resolve("src/main/resources/data/continuumlib/transform.properties");
        Files.createDirectories(config.getParent());
        Files.writeString(config, "pack=vanilla-identifiers-1.20.1-to-1.21.1\nsource.minecraft=" + path(artifacts.resolve("minecraft-1.20.1-server-extracted.jar"))
                + "\ntarget.minecraft=" + path(artifacts.resolve("minecraft-1.21.1-server-extracted.jar")) + "\noutput.namespace=OBFUSCATED\n"
                + mapping("source", artifacts, "1.20.1", "dca153d20defb32cfac3f069c3bf77b3e13c30ae63847637479d3137e099bb72")
                + mapping("target", artifacts, "1.21.1", "9d0b04bead421c8229aff14b534432bbc927bea642e7c8593d1276b8df8ba53f"));
        Path output = project.resolve("build/continuumlib/collision-consumer-transformed.jar");
        GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments("continuumLibTransformJar", "--stacktrace").build();
        byte[] previous = Files.readAllBytes(output);
        var collision = new ClassWriter(0);
        collision.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "akr", null, "java/lang/Object", null); collision.visitEnd();
        try (var jar = new JarOutputStream(Files.newOutputStream(project.resolve("input.jar")))) {
            jar.putNextEntry(new JarEntry("akr.class")); jar.write(collision.toByteArray()); jar.closeEntry();
        }
        var result = GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments("continuumLibTransformJar", "--stacktrace").buildAndFail();
        assertTrue(result.getOutput().contains("duplicates target API class in output namespace: akr"));
        assertArrayEquals(previous, Files.readAllBytes(output));
    }
    static String path(Path path) { return path.toString().replace('\\', '/'); }
    static String mapping(String side, Path artifacts, String version, String sha) {
        String prefix = "mapping." + side + ".";
        return prefix + "file=" + path(artifacts.resolve("minecraft-" + version + "-server_mappings.txt")) + "\n"
                + prefix + "sha256=" + sha + "\n" + prefix + "from=OBFUSCATED\n"
                + prefix + "namespaces=source:MOJMAP,target:OBFUSCATED\n" + prefix + "license=Mojang mappings license; local verification only\n";
    }
    static byte[] caller() {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "example/CustomCode", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "path", "()Ljava/lang/String;", null, null);
        method.visitCode(); method.visitTypeInsn(Opcodes.NEW, ID); method.visitInsn(Opcodes.DUP);
        method.visitLdcInsn("example"); method.visitLdcInsn("custom_path");
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, ID, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V", false);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ID, "getPath", "()Ljava/lang/String;", false);
        method.visitInsn(Opcodes.ARETURN); method.visitMaxs(4, 0); method.visitEnd(); writer.visitEnd();
        return writer.toByteArray();
    }
}
