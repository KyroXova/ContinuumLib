package com.kyroxova.continuumlib.pipeline.report;

import com.kyroxova.continuumlib.filter.registry.RegistryEntry;
import com.kyroxova.continuumlib.knowledge.rule.RulePack;
import com.kyroxova.continuumlib.model.diagnostic.Diagnostic;
import com.kyroxova.continuumlib.pipeline.TargetGenerationResult;
import com.kyroxova.continuumlib.pipeline.migration.AppliedMigration;
import com.kyroxova.continuumlib.pipeline.migration.MigrationLayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class GenerationReportWriter {
    private static final Comparator<AppliedMigration> MIGRATION_ORDER =
            Comparator.comparing(AppliedMigration::file, Comparator.nullsFirst(String::compareTo))
                    .thenComparingInt(AppliedMigration::line)
                    .thenComparing(migration -> migration.type().name())
                    .thenComparing(AppliedMigration::sourceOwner, Comparator.nullsFirst(String::compareTo))
                    .thenComparing(AppliedMigration::sourceName, Comparator.nullsFirst(String::compareTo))
                    .thenComparing(AppliedMigration::sourceDescriptor, Comparator.nullsFirst(String::compareTo))
                    .thenComparing(AppliedMigration::targetOwner, Comparator.nullsFirst(String::compareTo))
                    .thenComparing(AppliedMigration::targetName, Comparator.nullsFirst(String::compareTo))
                    .thenComparing(AppliedMigration::targetDescriptor, Comparator.nullsFirst(String::compareTo))
                    .thenComparing(AppliedMigration::rulePackId, Comparator.nullsFirst(String::compareTo));

    private static final Comparator<RegistryEntry> REGISTRY_ORDER =
            Comparator.comparing((RegistryEntry entry) -> entry.registryType().name())
                    .thenComparing(RegistryEntry::fullId)
                    .thenComparing(RegistryEntry::ownerClass)
                    .thenComparing(RegistryEntry::fieldName)
                    .thenComparing(RegistryEntry::sourcePath)
                    .thenComparingInt(RegistryEntry::lineNumber);

    private static final Comparator<Diagnostic> DIAGNOSTIC_ORDER =
            Comparator.comparing((Diagnostic diagnostic) -> diagnostic.severity().name())
                    .thenComparing(diagnostic -> diagnostic.code().name())
                    .thenComparing(Diagnostic::stage, Comparator.nullsFirst(String::compareTo))
                    .thenComparing(diagnostic -> diagnostic.path() == null ? null : diagnostic.path().toString(),
                            Comparator.nullsFirst(String::compareTo))
                    .thenComparingInt(Diagnostic::line)
                    .thenComparingInt(Diagnostic::column)
                    .thenComparing(Diagnostic::ruleId, Comparator.nullsFirst(String::compareTo))
                    .thenComparing(Diagnostic::message);

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
        result.resolvedTarget().rulePacks().stream()
                .sorted(Comparator.comparing(RulePack::id))
                .forEach(pack -> sb.append("  * ").append(pack.id())
                        .append(" (evidence: ").append(pack.evidence()).append(")\n"));
        sb.append("\n");

        sb.append("--- Source File Selection ---\n");
        sb.append("Discovered: ").append(result.discoveredSourceFiles().size()).append("\n");
        sb.append("Included:   ").append(result.includedSourceFiles().size()).append("\n");
        sb.append("Excluded:   ").append(result.excludedSourceFiles().size()).append("\n");
        result.excludedSourceFiles().stream()
                .sorted(Comparator.comparing(Path::toString))
                .forEach(path -> sb.append("  [EXCLUDED] ").append(path).append("\n"));
        sb.append("\n");

        sb.append("--- Resource Selection ---\n");
        sb.append("Discovered: ").append(result.discoveredResources().size()).append("\n");
        sb.append("Included:   ").append(result.includedResources().size()).append("\n");
        sb.append("Excluded:   ").append(result.excludedResources().size()).append("\n");
        result.excludedResources().keySet().stream()
                .sorted()
                .forEach(resource -> sb.append("  [EXCLUDED] ").append(resource).append("\n"));
        sb.append("\n");

        sb.append("--- Registry Exclusions ---\n");
        sb.append("Total Excluded Declarations: ")
                .append(result.excludedRegistryEntries().size()).append("\n");
        result.excludedRegistryEntries().stream()
                .sorted(REGISTRY_ORDER)
                .forEach(entry -> sb.append("  [EXCLUDED REGISTRY] ").append(entry.registryType()).append(" ")
                        .append(entry.fullId()).append(" in ")
                        .append(entry.ownerClass()).append("#").append(entry.fieldName())
                        .append(" (").append(entry.sourcePath()).append(":")
                        .append(entry.lineNumber()).append(")\n"));
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
        result.auditFindings().stream()
                .sorted(Comparator.comparing((com.kyroxova.continuumlib.bytecode.TargetReferenceAudit.Finding finding)
                                -> finding.status().name())
                        .thenComparing(com.kyroxova.continuumlib.bytecode.TargetReferenceAudit.Finding::detail))
                .forEach(finding -> sb.append("  [").append(finding.status()).append("] ")
                        .append(finding.detail()).append("\n"));
        sb.append("\n");

        sb.append("--- Diagnostics ---\n");
        sb.append("Total Diagnostics: ").append(result.diagnostics().size()).append("\n");
        result.diagnostics().stream()
                .sorted(DIAGNOSTIC_ORDER)
                .forEach(diagnostic -> sb.append("  [").append(diagnostic.severity()).append("] ")
                        .append(diagnostic.code()).append(": ")
                        .append(diagnostic.message()).append("\n"));
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
                .sorted(MIGRATION_ORDER)
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
