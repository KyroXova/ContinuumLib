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
import java.util.Map;

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
    @Test
    void reportOrderingIsDeterministic(@TempDir Path root) throws Exception {
        GeneratedWorkspace workspace = new GeneratedWorkspace(root.resolve("build"), "ordered");
        workspace.init();

        EnvironmentId environment = new EnvironmentId(
                "1.21.1",
                Loader.VANILLA,
                MappingNamespace.OFFICIAL,
                21
        );
        ResolvedTarget target = ResolvedTarget.builder()
                .targetId("ordered")
                .sourceEnvironment(environment)
                .targetEnvironment(environment)
                .workspace(workspace)
                .build();

        Path output = workspace.finalJar("ordered.jar");
        Files.write(output, new byte[]{1});

        AppliedMigration alphaMigration = new AppliedMigration(
                "alpha-pack",
                MigrationType.MEMBER_RENAME,
                "old/A",
                "oldMethod",
                "()V",
                "new/A",
                "newMethod",
                "()V",
                MigrationLayer.SOURCE_AST,
                MigrationConfidence.SEMANTICALLY_RESOLVED,
                "a/Example.java",
                4
        );
        AppliedMigration zetaMigration = new AppliedMigration(
                "zeta-pack",
                MigrationType.MEMBER_RENAME,
                "old/Z",
                "oldMethod",
                "()V",
                "new/Z",
                "newMethod",
                "()V",
                MigrationLayer.SOURCE_AST,
                MigrationConfidence.SEMANTICALLY_RESOLVED,
                "z/Example.java",
                9
        );

        var alphaRegistry = new com.kyroxova.continuumlib.filter.registry.RegistryEntry(
                com.kyroxova.continuumlib.filter.domain.RegistryType.BLOCK,
                "example",
                "alpha",
                "example.Blocks",
                "ALPHA",
                "a/Blocks.java",
                3
        );
        var zetaRegistry = new com.kyroxova.continuumlib.filter.registry.RegistryEntry(
                com.kyroxova.continuumlib.filter.domain.RegistryType.BLOCK,
                "example",
                "zeta",
                "example.Blocks",
                "ZETA",
                "z/Blocks.java",
                8
        );

        var alphaFinding = new com.kyroxova.continuumlib.bytecode.TargetReferenceAudit.Finding(
                com.kyroxova.continuumlib.bytecode.TargetReferenceAudit.Status.MEMBER_MISSING,
                "a/Owner"
        );
        var zetaFinding = new com.kyroxova.continuumlib.bytecode.TargetReferenceAudit.Finding(
                com.kyroxova.continuumlib.bytecode.TargetReferenceAudit.Status.MEMBER_MISSING,
                "z/Owner"
        );

        var alphaDiagnostic = new com.kyroxova.continuumlib.model.diagnostic.Diagnostic(
                com.kyroxova.continuumlib.model.diagnostic.DiagnosticCode.MIGRATION_UNRESOLVED,
                com.kyroxova.continuumlib.model.diagnostic.Severity.WARNING,
                "alpha diagnostic"
        );
        var zetaDiagnostic = new com.kyroxova.continuumlib.model.diagnostic.Diagnostic(
                com.kyroxova.continuumlib.model.diagnostic.DiagnosticCode.MIGRATION_UNRESOLVED,
                com.kyroxova.continuumlib.model.diagnostic.Severity.WARNING,
                "zeta diagnostic"
        );

        Path alphaSource = root.resolve("a/Source.java");
        Path zetaSource = root.resolve("z/Source.java");
        Path alphaResource = root.resolve("a.json");
        Path zetaResource = root.resolve("z.json");

        TargetGenerationResult first = TargetGenerationResult.builder()
                .resolvedTarget(target)
                .workspace(workspace)
                .excludedSourceFiles(List.of(zetaSource, alphaSource))
                .excludedResources(Map.of("z/resource.json", zetaResource, "a/resource.json", alphaResource))
                .excludedRegistryEntries(List.of(zetaRegistry, alphaRegistry))
                .appliedMigrations(List.of(zetaMigration, alphaMigration))
                .auditFindings(List.of(zetaFinding, alphaFinding))
                .diagnostics(List.of(zetaDiagnostic, alphaDiagnostic))
                .compilationSuccess(true)
                .outputJar(output)
                .build();

        GenerationReportWriter writer = new GenerationReportWriter();
        String firstText = Files.readString(writer.write(first));

        TargetGenerationResult second = TargetGenerationResult.builder()
                .resolvedTarget(target)
                .workspace(workspace)
                .excludedSourceFiles(List.of(alphaSource, zetaSource))
                .excludedResources(Map.of("a/resource.json", alphaResource, "z/resource.json", zetaResource))
                .excludedRegistryEntries(List.of(alphaRegistry, zetaRegistry))
                .appliedMigrations(List.of(alphaMigration, zetaMigration))
                .auditFindings(List.of(alphaFinding, zetaFinding))
                .diagnostics(List.of(alphaDiagnostic, zetaDiagnostic))
                .compilationSuccess(true)
                .outputJar(output)
                .build();

        String secondText = Files.readString(writer.write(second));

        assertEquals(firstText, secondText);
        assertTrue(firstText.indexOf(alphaSource.toString()) < firstText.indexOf(zetaSource.toString()));
        assertTrue(firstText.indexOf("example:alpha") < firstText.indexOf("example:zeta"));
        assertTrue(firstText.indexOf("a/Example.java") < firstText.indexOf("z/Example.java"));
        assertTrue(firstText.indexOf("a/Owner") < firstText.indexOf("z/Owner"));
        assertTrue(firstText.indexOf("alpha diagnostic") < firstText.indexOf("zeta diagnostic"));
    }

}
