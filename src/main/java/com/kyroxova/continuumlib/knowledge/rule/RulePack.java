package com.kyroxova.continuumlib.knowledge.rule;

import com.kyroxova.continuumlib.bytecode.*;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import java.util.*;

/** Explicit migration knowledge for one exact environment pair. Evidence is attribution,
 * not a compatibility certificate; manifests pin the API artifacts used to author rules.
 */
public record RulePack(String id, String evidence, EnvironmentId source, EnvironmentId target,
                       Map<String, String> sourceArtifacts, Map<String, String> targetArtifacts,
                       Map<String, String> classes, Map<MemberReference, MemberReference> members,
                       List<CallBridge.Rule> bridges, List<ConstructorFactory.Rule> constructors) {
    public RulePack(String id, String evidence, EnvironmentId source, EnvironmentId target,
                    Map<String, String> sourceArtifacts, Map<String, String> targetArtifacts,
                    Map<String, String> classes, Map<MemberReference, MemberReference> members, List<CallBridge.Rule> bridges) {
        this(id, evidence, source, target, sourceArtifacts, targetArtifacts, classes, members, bridges, List.of());
    }
    public RulePack {
        if (id == null || id.isBlank() || evidence == null || evidence.isBlank())
            throw new IllegalArgumentException("Rule pack ID and evidence are required");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        sourceArtifacts = manifest(sourceArtifacts);
        targetArtifacts = manifest(targetArtifacts);
        classes = Map.copyOf(classes);
        members = Map.copyOf(members);
        bridges = List.copyOf(bridges);
        constructors = List.copyOf(constructors);
    }
    private static Map<String, String> manifest(Map<String, String> values) {
        if (values.isEmpty()) throw new IllegalArgumentException("An API artifact manifest is required");
        var copy = new TreeMap<String, String>();
        values.forEach((name, digest) -> {
            if (name == null || name.isBlank() || digest == null || !digest.matches("[0-9a-fA-F]{64}"))
                throw new IllegalArgumentException("Artifact entries require a name and SHA-256 digest");
            copy.put(name, digest.toLowerCase(Locale.ROOT));
        });
        return Map.copyOf(copy);
    }
}
