package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.Loader;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class TargetGenerationInclusionTest {
    @Test
    void sourceInclusionSkipsUnselectedFilesBeforeParsing(@TempDir Path project) throws Exception {
        Path sourceRoot = project.resolve("src/main/java");
        Path resources = project.resolve("src/main/resources");
        Path inclusions = resources.resolve("continuumlib/inclusions");
        Files.createDirectories(sourceRoot.resolve("example"));
        Files.createDirectories(inclusions);

        Path valid = sourceRoot.resolve("example/Valid.java");
        Path broken = sourceRoot.resolve("example/Broken.java");
        Files.writeString(valid, "package example; public class Valid { public int value() { return 7; } }");
        Files.writeString(broken, "package example; public class Broken { this is not valid java");

        Files.writeString(inclusions.resolve("source.json"), """
                {
                  "domain": "source",
                  "path": "example/Valid.java"
                }
                """);

        Path api = project.resolve("empty-api.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(api))) {
        }

        EnvironmentId environment = new EnvironmentId(
                "1.21.1",
                Loader.VANILLA,
                MappingNamespace.OFFICIAL,
                17
        );
        GeneratedWorkspace workspace = new GeneratedWorkspace(project.resolve("build"), "test");
        ResolvedTarget target = ResolvedTarget.builder()
                .targetId("test")
                .sourceEnvironment(environment)
                .targetEnvironment(environment)
                .sourceArtifacts(Map.of("game", api))
                .targetArtifacts(Map.of("game", api))
                .workspace(workspace)
                .build();

        TargetGenerationResult result = new TargetGenerationPipeline().execute(
                target,
                project,
                List.of(sourceRoot),
                List.of(resources),
                "test.jar"
        );

        assertTrue(result.isSuccess());
        assertEquals(List.of(valid.toAbsolutePath().normalize()), result.includedSourceFiles());
        assertEquals(List.of(broken.toAbsolutePath().normalize()), result.excludedSourceFiles());
        assertEquals("package example; public class Broken { this is not valid java", Files.readString(broken));
        assertTrue(Files.isRegularFile(workspace.classesDir().resolve("example/Valid.class")));
        assertTrue(Files.isRegularFile(result.outputJar()));
    }
}
