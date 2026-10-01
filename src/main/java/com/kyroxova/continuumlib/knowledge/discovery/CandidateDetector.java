package com.kyroxova.continuumlib.knowledge.discovery;

import com.kyroxova.continuumlib.bytecode.MemberReference;
import com.kyroxova.continuumlib.knowledge.evidence.Confidence;
import com.kyroxova.continuumlib.knowledge.evidence.Evidence;
import com.kyroxova.continuumlib.knowledge.evidence.EvidenceType;
import com.kyroxova.continuumlib.knowledge.evidence.VerificationStatus;
import com.kyroxova.continuumlib.knowledge.snapshot.ApiSnapshot;
import com.kyroxova.continuumlib.knowledge.snapshot.ClassSnapshot;
import com.kyroxova.continuumlib.knowledge.snapshot.MemberSnapshot;

import java.util.*;

public final class CandidateDetector {

    public KnowledgeReport compare(ApiSnapshot source, ApiSnapshot target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");

        List<MemberReference> unchanged = new ArrayList<>();
        List<MemberReference> removed = new ArrayList<>();
        List<MemberReference> added = new ArrayList<>();
        List<MigrationCandidate> candidates = new ArrayList<>();
        List<MigrationCandidate> ambiguous = new ArrayList<>();

        Map<MemberReference, MemberSnapshot> sourceMembers = extractMembers(source);
        Map<MemberReference, MemberSnapshot> targetMembers = extractMembers(target);

        for (var entry : sourceMembers.entrySet()) {
            var ref = entry.getKey();
            var srcMem = entry.getValue();
            var tgtMem = targetMembers.get(ref);
            if (tgtMem != null && srcMem.isStatic() == tgtMem.isStatic()) {
                unchanged.add(ref);
            } else {
                removed.add(ref);
            }
        }

        for (var entry : targetMembers.entrySet()) {
            var ref = entry.getKey();
            if (!sourceMembers.containsKey(ref)) {
                added.add(ref);
            }
        }

        // Group removed and added by owner
        Map<String, List<MemberSnapshot>> removedByOwner = new TreeMap<>();
        for (var ref : removed) {
            removedByOwner.computeIfAbsent(ref.owner(), k -> new ArrayList<>()).add(sourceMembers.get(ref));
        }
        Map<String, List<MemberSnapshot>> addedByOwner = new TreeMap<>();
        for (var ref : added) {
            addedByOwner.computeIfAbsent(ref.owner(), k -> new ArrayList<>()).add(targetMembers.get(ref));
        }

        // Detect within same owner
        Set<String> allOwners = new TreeSet<>(removedByOwner.keySet());
        for (String owner : allOwners) {
            List<MemberSnapshot> removedList = removedByOwner.getOrDefault(owner, List.of());
            List<MemberSnapshot> addedList = addedByOwner.getOrDefault(owner, List.of());

            // 1. Method renames (same descriptor, same owner)
            detectMethodRenames(owner, removedList, addedList, candidates, ambiguous);

            // 2. Constructor to Static Factory
            detectConstructorFactories(owner, removedList, addedList, candidates, ambiguous);

            // 3. Field to Accessor
            detectFieldAccessors(owner, removedList, addedList, candidates, ambiguous);
        }

        // Sort all results deterministically
        unchanged.sort(Comparator.comparing(MemberReference::owner).thenComparing(MemberReference::name).thenComparing(MemberReference::descriptor));
        removed.sort(Comparator.comparing(MemberReference::owner).thenComparing(MemberReference::name).thenComparing(MemberReference::descriptor));
        added.sort(Comparator.comparing(MemberReference::owner).thenComparing(MemberReference::name).thenComparing(MemberReference::descriptor));
        candidates.sort(Comparator.comparing((MigrationCandidate c) -> c.source().owner())
                .thenComparing(c -> c.source().name())
                .thenComparing(c -> c.source().descriptor()));
        ambiguous.sort(Comparator.comparing((MigrationCandidate c) -> c.source().owner())
                .thenComparing(c -> c.source().name())
                .thenComparing(c -> c.source().descriptor()));

        return new KnowledgeReport(source, target, unchanged, added, removed, candidates, ambiguous);
    }

    private void detectMethodRenames(String owner, List<MemberSnapshot> removed, List<MemberSnapshot> added,
                                     List<MigrationCandidate> candidates, List<MigrationCandidate> ambiguous) {
        Map<String, List<MemberSnapshot>> removedByDesc = new TreeMap<>();
        for (var m : removed) {
            if (m.kind() == MemberSnapshot.Kind.METHOD) {
                removedByDesc.computeIfAbsent(m.descriptor(), k -> new ArrayList<>()).add(m);
            }
        }
        Map<String, List<MemberSnapshot>> addedByDesc = new TreeMap<>();
        for (var m : added) {
            if (m.kind() == MemberSnapshot.Kind.METHOD) {
                addedByDesc.computeIfAbsent(m.descriptor(), k -> new ArrayList<>()).add(m);
            }
        }

        for (var entry : removedByDesc.entrySet()) {
            String desc = entry.getKey();
            List<MemberSnapshot> remList = entry.getValue();
            List<MemberSnapshot> addList = addedByDesc.getOrDefault(desc, List.of());
            if (addList.isEmpty()) continue;

            if (remList.size() == 1 && addList.size() == 1) {
                var src = remList.get(0);
                var tgt = addList.get(0);
                if (src.isStatic() == tgt.isStatic()) {
                    List<Evidence> evidence = List.of(
                            Evidence.of(EvidenceType.SAME_OWNER, "Both methods belong to class " + owner),
                            Evidence.of(EvidenceType.EXACT_DESCRIPTOR_MATCH, "Identical descriptor: " + desc),
                            Evidence.of(EvidenceType.SAME_RETURN_TYPE, "Return type matches: " + returnType(desc))
                    );
                    candidates.add(MigrationCandidate.candidate(
                            src.toReference(), tgt.toReference(), CandidateType.POSSIBLE_RENAME,
                            evidence, Confidence.MEDIUM, VerificationStatus.REVIEW_REQUIRED,
                            "Single matching method with identical descriptor on same owner"
                    ));
                }
            } else {
                for (var src : remList) {
                    for (var tgt : addList) {
                        List<Evidence> evidence = List.of(
                                Evidence.of(EvidenceType.SAME_OWNER, "Both methods belong to class " + owner),
                                Evidence.of(EvidenceType.EXACT_DESCRIPTOR_MATCH, "Descriptor matches: " + desc)
                        );
                        ambiguous.add(MigrationCandidate.candidate(
                                src.toReference(), tgt.toReference(), CandidateType.AMBIGUOUS,
                                evidence, Confidence.LOW, VerificationStatus.REVIEW_REQUIRED,
                                "Multiple candidate matches for descriptor on owner (" + remList.size() + " removed, " + addList.size() + " added)"
                        ));
                    }
                }
            }
        }
    }

    private void detectConstructorFactories(String owner, List<MemberSnapshot> removed, List<MemberSnapshot> added,
                                            List<MigrationCandidate> candidates, List<MigrationCandidate> ambiguous) {
        String ownerType = "L" + owner + ";";
        for (var m : removed) {
            if (m.kind() != MemberSnapshot.Kind.CONSTRUCTOR) continue;
            String args = paramsOnly(m.descriptor());
            String expectedFactoryDesc = args + ownerType;

            List<MemberSnapshot> matchingFactories = new ArrayList<>();
            for (var targetMethod : added) {
                if (targetMethod.kind() == MemberSnapshot.Kind.METHOD && targetMethod.isStatic()
                        && targetMethod.descriptor().equals(expectedFactoryDesc)) {
                    matchingFactories.add(targetMethod);
                }
            }

            if (matchingFactories.size() == 1) {
                var factory = matchingFactories.get(0);
                List<Evidence> evidence = List.of(
                        Evidence.of(EvidenceType.SAME_OWNER, "Constructor and static factory on owner " + owner),
                        Evidence.of(EvidenceType.SAME_RETURN_TYPE, "Factory return type is owner " + ownerType),
                        Evidence.of(EvidenceType.PARAMETER_SIMILARITY, "Factory parameters exactly match constructor arguments: " + args)
                );
                candidates.add(MigrationCandidate.candidate(
                        m.toReference(), factory.toReference(), CandidateType.POSSIBLE_CONSTRUCTOR_TO_FACTORY,
                        evidence, Confidence.MEDIUM, VerificationStatus.REVIEW_REQUIRED,
                        "Static factory method on same class replaces constructor"
                ));
            } else if (matchingFactories.size() > 1) {
                for (var factory : matchingFactories) {
                    ambiguous.add(MigrationCandidate.candidate(
                            m.toReference(), factory.toReference(), CandidateType.AMBIGUOUS,
                            List.of(Evidence.of(EvidenceType.SAME_OWNER, "Multiple static factories with matching signature on " + owner)),
                            Confidence.LOW, VerificationStatus.REVIEW_REQUIRED,
                            "Multiple candidate factory methods (" + matchingFactories.size() + ") found for constructor"
                    ));
                }
            }
        }
    }

    private void detectFieldAccessors(String owner, List<MemberSnapshot> removed, List<MemberSnapshot> added,
                                      List<MigrationCandidate> candidates, List<MigrationCandidate> ambiguous) {
        for (var f : removed) {
            if (f.kind() != MemberSnapshot.Kind.FIELD) continue;
            String getterDesc = "()" + f.descriptor();
            String nameLower = f.name().toLowerCase(Locale.ROOT);

            List<MemberSnapshot> matchingAccessors = new ArrayList<>();
            for (var targetMethod : added) {
                if (targetMethod.kind() == MemberSnapshot.Kind.METHOD && targetMethod.descriptor().equals(getterDesc)) {
                    String targetNameLower = targetMethod.name().toLowerCase(Locale.ROOT);
                    if (targetNameLower.equals(nameLower)
                            || targetNameLower.equals("get" + nameLower)
                            || targetNameLower.equals("is" + nameLower)) {
                        matchingAccessors.add(targetMethod);
                    }
                }
            }

            if (matchingAccessors.size() == 1) {
                var acc = matchingAccessors.get(0);
                List<Evidence> evidence = List.of(
                        Evidence.of(EvidenceType.SAME_OWNER, "Field and accessor on owner " + owner),
                        Evidence.of(EvidenceType.SAME_RETURN_TYPE, "Accessor return type matches field type: " + f.descriptor())
                );
                candidates.add(MigrationCandidate.candidate(
                        f.toReference(), acc.toReference(), CandidateType.POSSIBLE_FIELD_TO_ACCESSOR,
                        evidence, Confidence.MEDIUM, VerificationStatus.REVIEW_REQUIRED,
                        "Accessor method replaces removed field"
                ));
            }
        }
    }

    private static Map<MemberReference, MemberSnapshot> extractMembers(ApiSnapshot snapshot) {
        Map<MemberReference, MemberSnapshot> map = new TreeMap<>(
                Comparator.comparing(MemberReference::owner).thenComparing(MemberReference::name).thenComparing(MemberReference::descriptor));
        for (ClassSnapshot cls : snapshot.classes().values()) {
            for (MemberSnapshot m : cls.methods().values()) map.put(m.toReference(), m);
            for (MemberSnapshot c : cls.constructors().values()) map.put(c.toReference(), c);
            for (MemberSnapshot f : cls.fields().values()) map.put(f.toReference(), f);
        }
        return map;
    }

    private static String returnType(String descriptor) {
        int idx = descriptor.indexOf(')');
        return idx >= 0 ? descriptor.substring(idx + 1) : descriptor;
    }

    private static String paramsOnly(String descriptor) {
        int idx = descriptor.indexOf(')');
        return idx >= 0 ? descriptor.substring(0, idx + 1) : descriptor;
    }
}
