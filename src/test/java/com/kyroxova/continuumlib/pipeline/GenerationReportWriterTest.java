package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.Loader;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;
import com.kyroxova.continuumlib.pipeline.migration.AppliedMigration;
import com.kyroxova.continuumlib.pipeline.migration.MigrationConfidence;
import com.kyroxova.continuumlib.pipeline.migration.MigrationLayer;
import com.kyroxova.continuumlib.pipeline.migration.MigrationType;
import com.kyroxova.continuumlib.pipeline.report.GenerationReportWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GenerationReportWriterTest {
    @Test
    void reportsSourceAndBytecodeMigrationsSeparately(@TempDir Path root) throws Exception {
        GeneratedWorkspace workspace = new GeneratedWorkspace(root.resolve("build"), "test");
        workspace.init();

        EnvironmentId environment = new EnvironmentId(
                "1.21.1",
                Loader.VANILLA,
                MappingNamespace.OFFICIAL,
                21
        );
        ResolvedTarget target = ResolvedTarget.builder()
                .targetId("test")
                .sourceEnvironment(environment)
                .targetEnvironment(environment)
                .workspace(workspace)
                .build();

        Path output = workspace.finalJar("test.jar");
        Files.write(output, new byte[]{1});

        AppliedMigration source = new AppliedMigration(
                "source-pack",
                MigrationType.MEMBER_RENAME,
                "old/Owner",
                "oldMethod",
                "()V",
                "new/Owner",
                "newMethod",
                "()V",
                MigrationLayer.SOURCE_AST,
                MigrationConfidence.SEMANTICALLY_RESOLVED,
                "example/Example.java",
                12
        );
        AppliedMigration bytecode = new AppliedMigration(
                "bridge-pack",
                MigrationType.CALL_BRIDGE,
                "old/Owner",
                "call",
                "()V",
                "compat/Hooks",
                "call",
                "()V",
                MigrationLayer.BYTECODE,
                MigrationConfidence.SEMANTICALLY_RESOLVED,
                "example/Example.class",
                14
        );

        TargetGenerationResult result = TargetGenerationResult.builder()
                .resolvedTarget(target)
                .workspace(workspace)
                .appliedMigrations(List.of(source, bytecode))
                .compilationSuccess(true)
                .compiledClassesCount(1)
                .bytecodeAdaptedCount(1)
                .outputJar(output)
                .build();

        Path report = new GenerationReportWriter().write(result);
        String text = Files.readString(report);

        assertTrue(text.contains("--- Applied Source Migrations ---"));
        assertTrue(text.contains("source-pack"));
        assertTrue(text.contains("--- Applied Bytecode Migrations ---"));
        assertTrue(text.contains("bridge-pack"));
        assertTrue(text.contains("Output Namespace: OFFICIAL"));
        assertTrue(text.contains("FINAL STATUS: PASSED"));
    }
}
