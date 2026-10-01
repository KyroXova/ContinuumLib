package com.kyroxova.continuumlib.gradle;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.jar.JarOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class MappingInputSafetyTest {
    @TempDir Path project;
    @ParameterizedTest @ValueSource(strings = {"target.tiny", "dependency.jar"})
    void auditCannotTruncateVerifiedMappingOrDependencyInput(String outputName) throws Exception {
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name='safety'");
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'com.kyroxova.continuumlib' }\n"
                + "tasks.named('continuumLibTransformJar') { inputJar.set(layout.projectDirectory.file('empty.jar')) }\n"
                + "tasks.named('continuumLibAuditTarget') { reportFile.set(layout.projectDirectory.file('" + outputName + "')) }\n");
        try (var jar = new JarOutputStream(Files.newOutputStream(project.resolve("empty.jar")))) { }
        Files.copy(project.resolve("empty.jar"), project.resolve("dependency.jar"));
        byte[] dependency = Files.readAllBytes(project.resolve("dependency.jar"));
        String mapping = "tiny\t2\t0\tobf\tnamed\nc\ta\tgame/Unused\n";
        Path map = Files.writeString(project.resolve("target.tiny"), mapping);
        String artifactHash = sha(project.resolve("empty.jar"));
        Path config = project.resolve("src/main/resources/data/continuumlib");
        Files.createDirectories(config.resolve("knowledge"));
        Files.writeString(config.resolve("knowledge/safety.xml"), "<rules schema='1' id='safety' evidence='Synthetic input safety test'>"
                + "<source minecraft='1.20.1' loader='FORGE' namespace='MOJMAP' java='17'><artifact name='api' sha256='" + artifactHash + "'/></source>"
                + "<target minecraft='1.21.1' loader='FORGE' namespace='MOJMAP' java='21'><artifact name='api' sha256='" + artifactHash + "'/></target></rules>");
        Files.writeString(config.resolve("transform.properties"), "pack=safety\nsource.api=empty.jar\ntarget.api=empty.jar\n"
                + "mapping.target.file=target.tiny\nmapping.target.sha256=" + sha(map) + "\nmapping.target.from=OBFUSCATED\n"
                + "mapping.target.namespaces=obf:OBFUSCATED,named:MOJMAP\nmapping.target.license=Synthetic\n"
                + "classpath.target.library.file=dependency.jar\nclasspath.target.library.sha256=" + sha(project.resolve("dependency.jar")) + "\n");
        var result = GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath().withArguments("continuumLibAuditTarget").buildAndFail();
        assertTrue(result.getOutput().contains("must not overwrite"));
        assertEquals(mapping, Files.readString(map));
        assertArrayEquals(dependency, Files.readAllBytes(project.resolve("dependency.jar")));
    }
    private String sha(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
