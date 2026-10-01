package com.kyroxova.continuumlib.knowledge.promotion;

import com.kyroxova.continuumlib.bytecode.ConstructorFactory;
import com.kyroxova.continuumlib.bytecode.MemberReference;
import com.kyroxova.continuumlib.knowledge.discovery.CandidateType;
import com.kyroxova.continuumlib.knowledge.discovery.MigrationCandidate;
import com.kyroxova.continuumlib.knowledge.rule.RulePack;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;

import java.util.*;

public final class RulePromoter {
    private RulePromoter() {}

    public static RulePack promote(
            String id,
            String evidenceDescription,
            EnvironmentId source,
            EnvironmentId target,
            Map<String, String> sourceManifest,
            Map<String, String> targetManifest,
            Collection<MigrationCandidate> candidates
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(evidenceDescription, "evidenceDescription");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");

        Map<String, String> classes = new TreeMap<>();
        Map<MemberReference, MemberReference> members = new HashMap<>();
        List<ConstructorFactory.Rule> constructors = new ArrayList<>();

        for (var c : candidates) {
            if (!c.isVerified()) {
                throw new IllegalStateException("Cannot promote unverified candidate to executable rule: "
                        + c.source() + " -> " + c.target() + " (status: " + c.status() + ", confidence: " + c.confidence() + ")");
            }

            if (c.type() == CandidateType.POSSIBLE_RENAME) {
                members.put(c.source(), c.target());
            } else if (c.type() == CandidateType.POSSIBLE_CONSTRUCTOR_TO_FACTORY) {
                constructors.add(new ConstructorFactory.Rule(c.source(), c.target()));
            } else {
                throw new UnsupportedOperationException("Candidate type " + c.type() + " does not yet have an automated RulePack promotion strategy");
            }
        }

        return new RulePack(id, evidenceDescription, source, target, sourceManifest, targetManifest, classes, members, List.of(), constructors);
    }
}
