package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.Loader;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class TargetGenerationClassFilterTest {
    @Test
    void classExclusionRemovesParsedUnitBeforeCompilation(@TempDir Path project) throws Exception {
        Path sourceRoot = project.resolve("src/main/java");
        Path resources = project.resolve("src/main/resources");
        Path exclusions = resources.resolve("continuumlib/exclusions");
        Files.createDirectories(sourceRoot.resolve("example"));
        Files.createDirectories(exclusions);

        Path included = sourceRoot.resolve("example/Included.java");
        Path excluded = sourceRoot.resolve("example/Excluded.java");
        Files.writeString(included, "package example; public class Included {}");
        Files.writeString(excluded, "package example; public class Excluded {}");
        Files.writeString(exclusions.resolve("classes.json"),
                "{\"domain\":\"class\",\"class\":\"example.Excluded\"}");

        Path api = project.resolve("api.jar");
        createJavaLangApi(api);

        EnvironmentId env = new EnvironmentId("1.20.1", Loader.VANILLA, MappingNamespace.MOJMAP, 17);
        GeneratedWorkspace workspace = new GeneratedWorkspace(project.resolve("build"), "test");
        ResolvedTarget target = ResolvedTarget.builder()
                .targetId("test")
                .sourceEnvironment(env)
                .targetEnvironment(env)
                .sourceArtifacts(Map.of("java", api))
                .targetArtifacts(Map.of("java", api))
                .workspace(workspace)
                .build();

        TargetGenerationResult result = new TargetGenerationPipeline().execute(
                target,
                project,
                List.of(sourceRoot),
                List.of(resources),
                "test.jar"
        );

        assertEquals(List.of(included.toAbsolutePath().normalize()), result.includedSourceFiles());
        assertEquals(List.of(excluded.toAbsolutePath().normalize()), result.excludedSourceFiles());
        assertTrue(Files.exists(workspace.classesDir().resolve("example/Included.class")));
        assertFalse(Files.exists(workspace.classesDir().resolve("example/Excluded.class")));
        assertTrue(Files.exists(workspace.resourcesDir()));
    }

    private static void createJavaLangApi(Path jarPath) throws Exception {
        try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(jarPath.toFile()));
             var objectClass = Object.class.getResourceAsStream("/java/lang/Object.class")) {
            assertNotNull(objectClass);
            jar.putNextEntry(new JarEntry("java/lang/Object.class"));
            objectClass.transferTo(jar);
            jar.closeEntry();
        }
    }
}
