package com.kyroxova.continuumlib.knowledge.discovery;

import com.kyroxova.continuumlib.bytecode.MemberReference;
import com.kyroxova.continuumlib.knowledge.snapshot.ApiSnapshot;

import java.util.*;

public record KnowledgeReport(
        ApiSnapshot source,
        ApiSnapshot target,
        List<MemberReference> unchanged,
        List<MemberReference> added,
        List<MemberReference> removed,
        List<MigrationCandidate> candidates,
        List<MigrationCandidate> ambiguous
) {
    public KnowledgeReport {
        unchanged = List.copyOf(unchanged);
        added = List.copyOf(added);
        removed = List.copyOf(removed);
        candidates = List.copyOf(candidates);
        ambiguous = List.copyOf(ambiguous);
    }

    public String toHumanReadable() {
        var sb = new StringBuilder();
        sb.append("=== ContinuumLib Knowledge Comparison Report ===\n");
        sb.append("Source: ").append(source.environment()).append("\n");
        sb.append("Target: ").append(target.environment()).append("\n");
        sb.append("------------------------------------------------\n");
        sb.append("Summary:\n");
        sb.append("  Unchanged symbols: ").append(unchanged.size()).append("\n");
        sb.append("  Added symbols:     ").append(added.size()).append("\n");
        sb.append("  Removed symbols:   ").append(removed.size()).append("\n");
        sb.append("  Candidates:        ").append(candidates.size()).append("\n");
        sb.append("  Ambiguous matches: ").append(ambiguous.size()).append("\n");
        sb.append("------------------------------------------------\n");

        if (!candidates.isEmpty()) {
            sb.append("Migration Candidates:\n");
            for (var c : candidates) {
                sb.append("  [").append(c.type()).append("] (").append(c.status()).append(", confidence=").append(c.confidence()).append(")\n");
                sb.append("    Source: ").append(c.source()).append("\n");
                sb.append("    Target: ").append(c.target()).append("\n");
                if (!c.evidence().isEmpty()) {
                    sb.append("    Evidence:\n");
                    for (var e : c.evidence()) {
                        sb.append("      - ").append(e.type()).append(": ").append(e.description()).append("\n");
                    }
                }
                if (!c.note().isEmpty()) {
                    sb.append("    Note: ").append(c.note()).append("\n");
                }
            }
            sb.append("------------------------------------------------\n");
        }

        if (!ambiguous.isEmpty()) {
            sb.append("Ambiguous Matches (Review Required):\n");
            for (var a : ambiguous) {
                sb.append("  [AMBIGUOUS] ").append(a.source()).append(" -> ").append(a.target()).append("\n");
                sb.append("    Note: ").append(a.note()).append("\n");
            }
            sb.append("------------------------------------------------\n");
        }

        return sb.toString();
    }

    public String toMachineReadable() {
        var sb = new StringBuilder();
        sb.append("# CONTINUUM_KNOWLEDGE_REPORT_V1\n");
        sb.append("source.minecraft=").append(source.environment().minecraftVersion()).append("\n");
        sb.append("source.loader=").append(source.environment().loader()).append("\n");
        sb.append("target.minecraft=").append(target.environment().minecraftVersion()).append("\n");
        sb.append("target.loader=").append(target.environment().loader()).append("\n");
        sb.append("count.unchanged=").append(unchanged.size()).append("\n");
        sb.append("count.added=").append(added.size()).append("\n");
        sb.append("count.removed=").append(removed.size()).append("\n");
        sb.append("count.candidates=").append(candidates.size()).append("\n");
        sb.append("count.ambiguous=").append(ambiguous.size()).append("\n");

        for (var c : candidates) {
            sb.append("candidate\t").append(c.type()).append("\t")
                    .append(c.status()).append("\t")
                    .append(c.confidence()).append("\t")
                    .append(c.source().owner()).append("\t").append(c.source().name()).append("\t").append(c.source().descriptor()).append("\t")
                    .append(c.target().owner()).append("\t").append(c.target().name()).append("\t").append(c.target().descriptor()).append("\n");
        }
        for (var a : ambiguous) {
            sb.append("ambiguous\t").append(a.source().owner()).append("\t").append(a.source().name()).append("\t").append(a.source().descriptor()).append("\t")
                    .append(a.target().owner()).append("\t").append(a.target().name()).append("\t").append(a.target().descriptor()).append("\t")
                    .append(a.note()).append("\n");
        }
        return sb.toString();
    }
}
