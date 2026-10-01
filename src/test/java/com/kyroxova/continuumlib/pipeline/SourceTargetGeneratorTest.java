package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.continuumlib.api.config.TransformRequest;
import com.kyroxova.continuumlib.knowledge.rule.RulePack;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.Loader;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SourceTargetGeneratorTest {
    private static final String HASH = "0".repeat(64);

    @Test
    void generatesThroughWorkspaceWithoutTouchingOriginalSource(@TempDir Path project) throws Exception {
        Path sourceRoot = project.resolve("src/main/java");
        Path source = sourceRoot.resolve("example/Mod.java");
        Files.createDirectories(source.getParent());
        String original = "package example; public class Mod { public int value() { return 7; } }";
        Files.writeString(source, original);
        Files.createDirectories(project.resolve("src/main/resources"));

        EnvironmentId env = new EnvironmentId("1.20.1", Loader.FORGE, MappingNamespace.MOJMAP, 17);
        RulePack pack = new RulePack(
                "same",
                "test fixture",
                env,
                env,
                Map.of("api", HASH),
                Map.of("api", HASH),
                Map.of(),
                Map.of(),
                java.util.List.of(),
                java.util.List.of()
        );
        Path emptyApi = project.resolve("empty-api.jar");
        try (var jar = new java.util.jar.JarOutputStream(Files.newOutputStream(emptyApi))) {
            // Empty target API is sufficient for source using only java.lang.
        }
        TransformRequest request = new TransformRequest(
                "same",
                Map.of("api", emptyApi),
                Map.of("api", emptyApi)
        );
        ResolvedTarget target = new TargetResolver().resolve("test", request, java.util.List.of(pack));
        GeneratedWorkspace workspace = GeneratedWorkspace.under(
                project.resolve("build/continuum"),
                "test",
                "mod.jar"
        );

        SourceTargetGenerationResult result =
                new SourceTargetGenerator().generate(project, sourceRoot, target, workspace);

        assertEquals(original, Files.readString(source));
        assertEquals(1, result.generatedSources().size());
        assertTrue(Files.isRegularFile(workspace.classesDirectory().resolve("example/Mod.class")));
        assertTrue(Files.isRegularFile(workspace.outputJar()));
    }
}
