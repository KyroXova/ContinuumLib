package com.kyroxova.continuumlib.pipeline.report;

import com.kyroxova.continuumlib.knowledge.rule.RulePack;
import com.kyroxova.continuumlib.pipeline.TargetGenerationResult;
import com.kyroxova.continuumlib.pipeline.migration.AppliedMigration;
import com.kyroxova.continuumlib.pipeline.migration.MigrationLayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class GenerationReportWriter {
    public Path write(TargetGenerationResult result) throws IOException {
        Path reportFile = result.workspace().reportsDir().resolve("generation.txt");
        Files.createDirectories(reportFile.getParent());

        StringBuilder sb = new StringBuilder();
        sb.append("================================================================================\n");
        sb.append("CONTINUUMLIB TARGET GENERATION REPORT\n");
        sb.append("================================================================================\n\n");

        sb.append("Target ID: ").append(result.resolvedTarget().targetId()).append("\n");
        sb.append("Source Environment: ").append(result.resolvedTarget().sourceEnvironment()).append("\n");
        sb.append("Target Environment: ").append(result.resolvedTarget().targetEnvironment()).append("\n");
        sb.append("Output Namespace: ").append(result.resolvedTarget().mappingNamespace()).append("\n");
        sb.append("Loader Version: ")
                .append(result.resolvedTarget().loaderVersion() != null
                        ? result.resolvedTarget().loaderVersion()
                        : "unspecified")
                .append("\n");
        sb.append("Output Mode: ").append(result.resolvedTarget().outputMode()).append("\n\n");

        sb.append("--- Selected Rule Packs ---\n");
        for (RulePack pack : result.resolvedTarget().rulePacks()) {
            sb.append("  * ").append(pack.id())
                    .append(" (evidence: ").append(pack.evidence()).append(")\n");
        }
        sb.append("\n");

        sb.append("--- Source File Selection ---\n");
        sb.append("Discovered: ").append(result.discoveredSourceFiles().size()).append("\n");
        sb.append("Included:   ").append(result.includedSourceFiles().size()).append("\n");
        sb.append("Excluded:   ").append(result.excludedSourceFiles().size()).append("\n");
        for (Path path : result.excludedSourceFiles()) {
            sb.append("  [EXCLUDED] ").append(path).append("\n");
        }
        sb.append("\n");

        sb.append("--- Resource Selection ---\n");
        sb.append("Discovered: ").append(result.discoveredResources().size()).append("\n");
        sb.append("Included:   ").append(result.includedResources().size()).append("\n");
        sb.append("Excluded:   ").append(result.excludedResources().size()).append("\n");
        for (String resource : result.excludedResources().keySet()) {
            sb.append("  [EXCLUDED] ").append(resource).append("\n");
        }
        sb.append("\n");

        sb.append("--- Registry Exclusions ---\n");
        sb.append("Total Excluded Declarations: ")
                .append(result.excludedRegistryEntries().size()).append("\n");
        for (var entry : result.excludedRegistryEntries()) {
            sb.append("  [EXCLUDED REGISTRY] ").append(entry.registryType()).append(" ")
                    .append(entry.id()).append(" in ")
                    .append(entry.ownerClass()).append("#").append(entry.fieldName())
                    .append(" (").append(entry.sourcePath()).append(":")
                    .append(entry.lineNumber()).append(")\n");
        }
        sb.append("\n");

        appendMigrationSection(sb, "Applied Source Migrations", result.appliedMigrations(), MigrationLayer.SOURCE_AST);
        appendMigrationSection(sb, "Applied Bytecode Migrations", result.appliedMigrations(), MigrationLayer.BYTECODE);

        sb.append("--- Bytecode Adaptations ---\n");
        sb.append("Adapted Bytecode Classes: ").append(result.bytecodeAdaptedCount()).append("\n\n");

        sb.append("--- Compilation Result ---\n");
        sb.append("Status: ").append(result.compilationSuccess() ? "SUCCESS" : "FAILED").append("\n");
        sb.append("Compiled Classes Count: ").append(result.compiledClassesCount()).append("\n\n");

        sb.append("--- Audit Verification ---\n");
        sb.append("Total Audit Findings: ").append(result.auditFindings().size()).append("\n");
        for (var finding : result.auditFindings()) {
            sb.append("  [").append(finding.status()).append("] ")
                    .append(finding.detail()).append("\n");
        }
        sb.append("\n");

        sb.append("--- Diagnostics ---\n");
        sb.append("Total Diagnostics: ").append(result.diagnostics().size()).append("\n");
        for (var diagnostic : result.diagnostics()) {
            sb.append("  [").append(diagnostic.severity()).append("] ")
                    .append(diagnostic.code()).append(": ")
                    .append(diagnostic.message()).append("\n");
        }
        sb.append("\n");

        sb.append("================================================================================\n");
        sb.append("FINAL STATUS: ").append(result.isSuccess() ? "PASSED" : "FAILED").append("\n");
        if (result.outputJar() != null && Files.exists(result.outputJar())) {
            sb.append("OUTPUT JAR: ").append(result.outputJar()).append("\n");
        }
        sb.append("================================================================================\n");

        Files.writeString(reportFile, sb.toString(), StandardCharsets.UTF_8);
        return reportFile;
    }

    private static void appendMigrationSection(
            StringBuilder sb,
            String title,
            List<AppliedMigration> migrations,
            MigrationLayer layer
    ) {
        List<AppliedMigration> selected = migrations.stream()
                .filter(migration -> migration.layer() == layer)
                .toList();

        sb.append("--- ").append(title).append(" ---\n");
        sb.append("Total: ").append(selected.size()).append("\n");
        for (AppliedMigration migration : selected) {
            sb.append("  [").append(migration.confidence()).append("] ")
                    .append(migration.type()).append(" in ")
                    .append(migration.file()).append(":").append(migration.line()).append("\n")
                    .append("    from: ").append(migration.sourceOwner())
                    .append(migration.sourceName() != null ? "#" + migration.sourceName() : "")
                    .append(migration.sourceDescriptor() != null ? migration.sourceDescriptor() : "").append("\n")
                    .append("    to:   ").append(migration.targetOwner())
                    .append(migration.targetName() != null ? "#" + migration.targetName() : "")
                    .append(migration.targetDescriptor() != null ? migration.targetDescriptor() : "").append("\n")
                    .append("    rule pack: ")
                    .append(migration.rulePackId() != null ? migration.rulePackId() : "none")
                    .append("\n");
        }
        sb.append("\n");
    }
}
