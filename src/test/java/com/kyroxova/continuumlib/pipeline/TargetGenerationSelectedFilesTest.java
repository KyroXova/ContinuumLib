package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.Loader;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class TargetGenerationSelectedFilesTest {
    @Test
    void respectsPreselectedGradleSourceAndResourceFiles(@TempDir Path project) throws Exception {
        Path sourceRoot = project.resolve("src/main/java");
        Path resourceRoot = project.resolve("src/main/resources");
        Files.createDirectories(sourceRoot.resolve("example"));
        Files.createDirectories(resourceRoot.resolve("assets/example"));

        Path selectedSource = sourceRoot.resolve("example/Selected.java");
        Path excludedSource = sourceRoot.resolve("example/Excluded.java");
        Files.writeString(
                selectedSource,
                "package example; public class Selected { public int value() { return 7; } }"
        );
        Files.writeString(
                excludedSource,
                "package example; public class Excluded { this is intentionally invalid"
        );

        Path selectedResource = resourceRoot.resolve("assets/example/selected.json");
        Path excludedResource = resourceRoot.resolve("assets/example/excluded.json");
        Files.writeString(selectedResource, "{\"selected\":true}");
        Files.writeString(excludedResource, "{\"selected\":false}");

        Path api = project.resolve("empty-api.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(api))) {
        }

        GeneratedWorkspace workspace = new GeneratedWorkspace(project.resolve("build"), "selected-files");
        ResolvedTarget target = target(api, workspace);

        TargetGenerationResult result = new TargetGenerationPipeline().execute(
                target,
                project,
                List.of(sourceRoot),
                List.of(resourceRoot),
                "selected-files.jar",
                List.of(selectedSource),
                List.of(selectedResource)
        );

        assertTrue(result.isSuccess());
        assertEquals(
                List.of(selectedSource.toAbsolutePath().normalize()),
                result.discoveredSourceFiles()
        );
        assertEquals(
                Map.of("assets/example/selected.json", selectedResource.toAbsolutePath().normalize()),
                result.discoveredResources()
        );
        assertTrue(Files.isRegularFile(workspace.classesDir().resolve("example/Selected.class")));

        try (JarFile jar = new JarFile(result.outputJar().toFile())) {
            assertNotNull(jar.getEntry("assets/example/selected.json"));
            assertNull(jar.getEntry("assets/example/excluded.json"));
        }
    }

    @Test
    void rejectsSelectedFilesOutsideConfiguredRoots(@TempDir Path project) throws Exception {
        Path sourceRoot = project.resolve("src/main/java");
        Path resourceRoot = project.resolve("src/main/resources");
        Files.createDirectories(sourceRoot);
        Files.createDirectories(resourceRoot);

        Path outsideSource = project.resolve("outside/Outside.java");
        Files.createDirectories(outsideSource.getParent());
        Files.writeString(outsideSource, "public class Outside {}");

        Path api = project.resolve("empty-api.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(api))) {
        }

        GeneratedWorkspace workspace = new GeneratedWorkspace(project.resolve("build"), "outside-source");
        ResolvedTarget target = target(api, workspace);

        IOException failure = assertThrows(IOException.class, () ->
                new TargetGenerationPipeline().execute(
                        target,
                        project,
                        List.of(sourceRoot),
                        List.of(resourceRoot),
                        "outside-source.jar",
                        List.of(outsideSource),
                        List.of()
                )
        );
        assertTrue(failure.getMessage().contains("outside configured source roots"), failure.getMessage());
    }

    private static ResolvedTarget target(Path api, GeneratedWorkspace workspace) {
        EnvironmentId environment = new EnvironmentId(
                "1.21.1",
                Loader.VANILLA,
                MappingNamespace.OFFICIAL,
                17
        );
        return ResolvedTarget.builder()
                .targetId(workspace.targetId())
                .sourceEnvironment(environment)
                .targetEnvironment(environment)
                .sourceArtifacts(Map.of("game", api))
                .targetArtifacts(Map.of("game", api))
                .workspace(workspace)
                .build();
    }
}
