package com.kyroxova.continuumlib.knowledge.discovery;

import com.kyroxova.continuumlib.bytecode.MemberReference;
import com.kyroxova.continuumlib.knowledge.evidence.Confidence;
import com.kyroxova.continuumlib.knowledge.evidence.Evidence;
import com.kyroxova.continuumlib.knowledge.evidence.VerificationStatus;

import java.util.*;

public record MigrationCandidate(
        MemberReference source,
        MemberReference target,
        CandidateType type,
        List<Evidence> evidence,
        Confidence confidence,
        VerificationStatus status,
        String note
) {
    public MigrationCandidate {
        Objects.requireNonNull(type, "type");
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(status, "status");
        note = note == null ? "" : note;
    }

    public static MigrationCandidate candidate(MemberReference source, MemberReference target, CandidateType type,
                                               List<Evidence> evidence, Confidence confidence, VerificationStatus status, String note) {
        return new MigrationCandidate(source, target, type, evidence, confidence, status, note);
    }

    public MigrationCandidate withVerification(VerificationStatus newStatus, Evidence additionalEvidence) {
        List<Evidence> newEvidence = new ArrayList<>(evidence);
        if (additionalEvidence != null) newEvidence.add(additionalEvidence);
        return new MigrationCandidate(source, target, type, newEvidence, confidence, newStatus, note);
    }

    public boolean isVerified() {
        return status.isVerified();
    }
}
