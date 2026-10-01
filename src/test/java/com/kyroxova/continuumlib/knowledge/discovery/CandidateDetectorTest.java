package com.kyroxova.continuumlib.knowledge.discovery;

import com.kyroxova.continuumlib.bytecode.ClassInfo;
import com.kyroxova.continuumlib.bytecode.MemberReference;
import com.kyroxova.continuumlib.knowledge.evidence.Confidence;
import com.kyroxova.continuumlib.knowledge.evidence.EvidenceType;
import com.kyroxova.continuumlib.knowledge.evidence.VerificationStatus;
import com.kyroxova.continuumlib.knowledge.promotion.RulePromoter;
import com.kyroxova.continuumlib.knowledge.snapshot.ApiSnapshot;
import com.kyroxova.continuumlib.knowledge.snapshot.SnapshotBuilder;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.Loader;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CandidateDetectorTest {
    private final EnvironmentId envA = new EnvironmentId("1.20.1", Loader.FORGE, MappingNamespace.OFFICIAL, 17);
    private final EnvironmentId envB = new EnvironmentId("1.21.1", Loader.FORGE, MappingNamespace.OFFICIAL, 21);
    private final Map<String, String> manifest = Map.of("api", "a".repeat(64));

    @Test
    void identifiesExactUnchangedMembers() {
        var cls = new ClassInfo("api/Block", "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC,
                List.of(new ClassInfo.Member("tickRate", "I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, null)),
                List.of(new ClassInfo.Member("tick", "()V", Opcodes.ACC_PUBLIC, null)));
        var snapA = SnapshotBuilder.fromClassIndex(envA, manifest, Map.of(cls.name(), cls));
        var snapB = SnapshotBuilder.fromClassIndex(envB, manifest, Map.of(cls.name(), cls));

        var report = new CandidateDetector().compare(snapA, snapB);
        assertEquals(2, report.unchanged().size());
        assertEquals(0, report.added().size());
        assertEquals(0, report.removed().size());
        assertEquals(0, report.candidates().size());
        assertTrue(report.unchanged().contains(new MemberReference("api/Block", "tick", "()V")));
        assertTrue(report.unchanged().contains(new MemberReference("api/Block", "tickRate", "I")));
    }

    @Test
    void detectsMethodRenameWithEvidence() {
        var clsA = new ClassInfo("api/Block", "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC,
                List.of(), List.of(new ClassInfo.Member("oldTick", "()V", Opcodes.ACC_PUBLIC, null)));
        var clsB = new ClassInfo("api/Block", "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC,
                List.of(), List.of(new ClassInfo.Member("newTick", "()V", Opcodes.ACC_PUBLIC, null)));
        var snapA = SnapshotBuilder.fromClassIndex(envA, manifest, Map.of(clsA.name(), clsA));
        var snapB = SnapshotBuilder.fromClassIndex(envB, manifest, Map.of(clsB.name(), clsB));

        var report = new CandidateDetector().compare(snapA, snapB);
        assertEquals(1, report.removed().size());
        assertEquals(1, report.added().size());
        assertEquals(1, report.candidates().size());

        var candidate = report.candidates().get(0);
        assertEquals(CandidateType.POSSIBLE_RENAME, candidate.type());
        assertEquals(new MemberReference("api/Block", "oldTick", "()V"), candidate.source());
        assertEquals(new MemberReference("api/Block", "newTick", "()V"), candidate.target());
        assertEquals(VerificationStatus.REVIEW_REQUIRED, candidate.status());
        assertEquals(Confidence.MEDIUM, candidate.confidence());
        assertTrue(candidate.evidence().stream().anyMatch(e -> e.type() == EvidenceType.SAME_OWNER));
        assertTrue(candidate.evidence().stream().anyMatch(e -> e.type() == EvidenceType.EXACT_DESCRIPTOR_MATCH));
    }

    @Test
    void multipleSameDescriptorMethodsRemainAmbiguousAndAreNotEquated() {
        // Class has two removed methods with same descriptor ()V, and two added methods with ()V
        var clsA = new ClassInfo("api/Block", "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC, List.of(),
                List.of(new ClassInfo.Member("actionA", "()V", Opcodes.ACC_PUBLIC, null),
                        new ClassInfo.Member("actionB", "()V", Opcodes.ACC_PUBLIC, null)));
        var clsB = new ClassInfo("api/Block", "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC, List.of(),
                List.of(new ClassInfo.Member("process1", "()V", Opcodes.ACC_PUBLIC, null),
                        new ClassInfo.Member("process2", "()V", Opcodes.ACC_PUBLIC, null)));
        var snapA = SnapshotBuilder.fromClassIndex(envA, manifest, Map.of(clsA.name(), clsA));
        var snapB = SnapshotBuilder.fromClassIndex(envB, manifest, Map.of(clsB.name(), clsB));

        var report = new CandidateDetector().compare(snapA, snapB);
        assertEquals(0, report.candidates().size(), "Ambiguous matches must not be recorded as single candidates");
        assertEquals(4, report.ambiguous().size(), "All permutations must be flagged as ambiguous");
        for (var a : report.ambiguous()) {
            assertEquals(CandidateType.AMBIGUOUS, a.type());
            assertEquals(Confidence.LOW, a.confidence());
            assertEquals(VerificationStatus.REVIEW_REQUIRED, a.status());
        }
    }

    @Test
    void overloadedMethodsAreTrackedSeparatelyWithoutCollapsing() {
        var clsA = new ClassInfo("api/Block", "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC, List.of(),
                List.of(new ClassInfo.Member("render", "(I)V", Opcodes.ACC_PUBLIC, null),
                        new ClassInfo.Member("render", "(II)V", Opcodes.ACC_PUBLIC, null)));
        var clsB = new ClassInfo("api/Block", "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC, List.of(),
                List.of(new ClassInfo.Member("draw", "(I)V", Opcodes.ACC_PUBLIC, null),
                        new ClassInfo.Member("render", "(II)V", Opcodes.ACC_PUBLIC, null)));
        var snapA = SnapshotBuilder.fromClassIndex(envA, manifest, Map.of(clsA.name(), clsA));
        var snapB = SnapshotBuilder.fromClassIndex(envB, manifest, Map.of(clsB.name(), clsB));

        var report = new CandidateDetector().compare(snapA, snapB);
        assertEquals(1, report.unchanged().size());
        assertTrue(report.unchanged().contains(new MemberReference("api/Block", "render", "(II)V")));

        assertEquals(1, report.candidates().size());
        var candidate = report.candidates().get(0);
        assertEquals(new MemberReference("api/Block", "render", "(I)V"), candidate.source());
        assertEquals(new MemberReference("api/Block", "draw", "(I)V"), candidate.target());
    }

    @Test
    void detectsConstructorToFactoryCandidate() {
        var clsA = new ClassInfo("api/ResourceLocation", "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC, List.of(),
                List.of(new ClassInfo.Member("<init>", "(Ljava/lang/String;Ljava/lang/String;)V", Opcodes.ACC_PUBLIC, null)));
        var clsB = new ClassInfo("api/ResourceLocation", "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC, List.of(),
                List.of(new ClassInfo.Member("fromNamespaceAndPath", "(Ljava/lang/String;Ljava/lang/String;)Lapi/ResourceLocation;",
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, null)));
        var snapA = SnapshotBuilder.fromClassIndex(envA, manifest, Map.of(clsA.name(), clsA));
        var snapB = SnapshotBuilder.fromClassIndex(envB, manifest, Map.of(clsB.name(), clsB));

        var report = new CandidateDetector().compare(snapA, snapB);
        assertEquals(1, report.candidates().size());
        var candidate = report.candidates().get(0);
        assertEquals(CandidateType.POSSIBLE_CONSTRUCTOR_TO_FACTORY, candidate.type());
        assertEquals(new MemberReference("api/ResourceLocation", "<init>", "(Ljava/lang/String;Ljava/lang/String;)V"), candidate.source());
        assertEquals(new MemberReference("api/ResourceLocation", "fromNamespaceAndPath", "(Ljava/lang/String;Ljava/lang/String;)Lapi/ResourceLocation;"), candidate.target());
        assertTrue(candidate.evidence().stream().anyMatch(e -> e.type() == EvidenceType.SAME_RETURN_TYPE));
        assertTrue(candidate.evidence().stream().anyMatch(e -> e.type() == EvidenceType.PARAMETER_SIMILARITY));
    }

    @Test
    void detectsFieldToAccessorCandidate() {
        var clsA = new ClassInfo("api/Block", "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC,
                List.of(new ClassInfo.Member("hardness", "F", Opcodes.ACC_PUBLIC, null)), List.of());
        var clsB = new ClassInfo("api/Block", "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC,
                List.of(), List.of(new ClassInfo.Member("getHardness", "()F", Opcodes.ACC_PUBLIC, null)));
        var snapA = SnapshotBuilder.fromClassIndex(envA, manifest, Map.of(clsA.name(), clsA));
        var snapB = SnapshotBuilder.fromClassIndex(envB, manifest, Map.of(clsB.name(), clsB));

        var report = new CandidateDetector().compare(snapA, snapB);
        assertEquals(1, report.candidates().size());
        var candidate = report.candidates().get(0);
        assertEquals(CandidateType.POSSIBLE_FIELD_TO_ACCESSOR, candidate.type());
        assertEquals(new MemberReference("api/Block", "hardness", "F"), candidate.source());
        assertEquals(new MemberReference("api/Block", "getHardness", "()F"), candidate.target());
    }

    @Test
    void unverifiedCandidateCannotBePromotedToExecutableRulePack() {
        var unverifiedCandidate = MigrationCandidate.candidate(
                new MemberReference("api/Block", "oldMethod", "()V"),
                new MemberReference("api/Block", "newMethod", "()V"),
                CandidateType.POSSIBLE_RENAME,
                List.of(),
                Confidence.MEDIUM,
                VerificationStatus.REVIEW_REQUIRED,
                "Unreviewed structural match"
        );

        var ex = assertThrows(IllegalStateException.class, () ->
                RulePromoter.promote("test-pack", "evidence", envA, envB, manifest, manifest, List.of(unverifiedCandidate)));
        assertTrue(ex.getMessage().contains("Cannot promote unverified candidate"));
    }

    @Test
    void verifiedCandidatePromotesSuccessfullyToRulePack() {
        var unverifiedCandidate = MigrationCandidate.candidate(
                new MemberReference("api/Block", "oldMethod", "()V"),
                new MemberReference("api/Block", "newMethod", "()V"),
                CandidateType.POSSIBLE_RENAME,
                List.of(),
                Confidence.MEDIUM,
                VerificationStatus.REVIEW_REQUIRED,
                "Structural match"
        );

        var verifiedCandidate = unverifiedCandidate.withVerification(
                VerificationStatus.VERIFIED_TRANSFORMATION,
                com.kyroxova.continuumlib.knowledge.evidence.Evidence.of(EvidenceType.TRANSFORMATION_TEST, "Passes bytecode fixture execution")
        );

        var pack = RulePromoter.promote("test-pack", "Verified by test", envA, envB, manifest, manifest, List.of(verifiedCandidate));
        assertEquals("test-pack", pack.id());
        assertEquals(new MemberReference("api/Block", "newMethod", "()V"), pack.members().get(new MemberReference("api/Block", "oldMethod", "()V")));
    }

    @Test
    void deterministicReportsCanBeExported() {
        var clsA = new ClassInfo("api/Block", "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC,
                List.of(), List.of(new ClassInfo.Member("foo", "()V", Opcodes.ACC_PUBLIC, null)));
        var clsB = new ClassInfo("api/Block", "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC,
                List.of(), List.of(new ClassInfo.Member("bar", "()V", Opcodes.ACC_PUBLIC, null)));
        var snapA = SnapshotBuilder.fromClassIndex(envA, manifest, Map.of(clsA.name(), clsA));
        var snapB = SnapshotBuilder.fromClassIndex(envB, manifest, Map.of(clsB.name(), clsB));

        var report = new CandidateDetector().compare(snapA, snapB);
        String human = report.toHumanReadable();
        assertTrue(human.contains("Unchanged symbols: 0"));
        assertTrue(human.contains("Removed symbols:   1"));
        assertTrue(human.contains("Added symbols:     1"));
        assertTrue(human.contains("POSSIBLE_RENAME"));

        String machine = report.toMachineReadable();
        assertTrue(machine.contains("# CONTINUUM_KNOWLEDGE_REPORT_V1"));
        assertTrue(machine.contains("count.candidates=1"));
    }
}
