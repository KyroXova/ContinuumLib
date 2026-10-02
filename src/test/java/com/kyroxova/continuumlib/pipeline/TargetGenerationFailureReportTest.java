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

import static org.junit.jupiter.api.Assertions.*;

class TargetGenerationFailureReportTest {
    @Test
    void artifactFailureIsRecordedInGenerationReport(@TempDir Path project) throws Exception {
        EnvironmentId environment = new EnvironmentId(
                "1.21.1",
                Loader.VANILLA,
                MappingNamespace.OFFICIAL,
                17
        );
        GeneratedWorkspace workspace = new GeneratedWorkspace(project.resolve("build"), "test");
        Path missing = project.resolve("missing-source-api.jar");

        ResolvedTarget target = ResolvedTarget.builder()
                .targetId("test")
                .sourceEnvironment(environment)
                .targetEnvironment(environment)
                .sourceArtifacts(Map.of("api", missing))
                .workspace(workspace)
                .build();

        IOException failure = assertThrows(IOException.class, () ->
                new TargetGenerationPipeline().execute(
                        target,
                        project,
                        List.of(project.resolve("src/main/java")),
                        List.of(project.resolve("src/main/resources")),
                        "test.jar"
                )
        );

        assertTrue(failure.getMessage().contains("Missing source artifact"));

        Path report = workspace.reportsDir().resolve("generation.txt");
        assertTrue(Files.isRegularFile(report));
        String text = Files.readString(report);
        assertTrue(text.contains("PIPELINE_EXECUTION_FAILED"), text);
        assertTrue(text.contains("Missing source artifact"), text);
        assertTrue(text.contains("FINAL STATUS: FAILED"), text);
    }
}
