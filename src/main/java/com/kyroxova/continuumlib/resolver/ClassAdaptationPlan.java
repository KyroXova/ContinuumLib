package com.kyroxova.continuumlib.resolver;

import com.kyroxova.continuumlib.bytecode.*;
import com.kyroxova.continuumlib.knowledge.rule.RulePack;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Composes migration rules and binds them to artifact identities before use.
 * This plan performs transformations, not full JVM linkage or gameplay verification.
 */
public final class ClassAdaptationPlan {
    private final EnvironmentId source, target;
    private final List<String> packIds;
    private final Map<String, String> sourceArtifacts = new TreeMap<>(), targetArtifacts = new TreeMap<>();
    private final ClassAdapter renames;
    private final CallBridge bridges;
    private final ConstructorFactory constructors;
    private final Map<String, String> classNames;
    private final Map<MemberReference, MemberReference> memberNames;
    private record BridgeKey(MemberReference source, int opcode) {}

    public ClassAdaptationPlan(EnvironmentId source, EnvironmentId target, Collection<RulePack> packs) {
        this.source = Objects.requireNonNull(source);
        this.target = Objects.requireNonNull(target);
        if (packs.isEmpty()) throw new IllegalArgumentException("A plan requires explicit rule packs");
        Map<String, String> classes = new TreeMap<>();
        Map<MemberReference, MemberReference> members = new HashMap<>();
        Map<BridgeKey, CallBridge.Rule> calls = new HashMap<>();
        Map<MemberReference, ConstructorFactory.Rule> allocations = new HashMap<>();
        var ids = new TreeSet<String>();
        for (RulePack pack : packs) {
            if (!source.equals(pack.source()) || !target.equals(pack.target()))
                throw new IllegalArgumentException("Environment mismatch in " + pack.id());
            if (!ids.add(pack.id())) throw new IllegalArgumentException("Duplicate pack ID: " + pack.id());
            merge(sourceArtifacts, pack.sourceArtifacts(), "source artifacts");
            merge(targetArtifacts, pack.targetArtifacts(), "target artifacts");
            merge(classes, pack.classes(), "class rules");
            merge(members, pack.members(), "member rules");
            for (CallBridge.Rule rule : pack.bridges())
                merge(calls, Map.of(new BridgeKey(rule.source(), rule.opcode()), rule), "bridge rules");
            for (ConstructorFactory.Rule rule : pack.constructors())
                merge(allocations, Map.of(rule.source(), rule), "constructor factories");
        }
        for (MemberReference constructor : allocations.keySet())
            if (members.containsKey(constructor)) throw new IllegalArgumentException("A constructor cannot have both a rename and factory: " + constructor);
        for (BridgeKey call : calls.keySet()) {
            if (members.containsKey(call.source()))
                throw new IllegalArgumentException("A member cannot have both a rename and a call bridge: " + call.source());
        }
        if (new HashSet<>(classes.values()).size() != classes.size())
            throw new IllegalArgumentException("Multiple class rules have the same destination");
        packIds = List.copyOf(ids);
        classNames = Map.copyOf(classes);
        memberNames = Map.copyOf(members);
        renames = new ClassAdapter(classes, members);
        bridges = new CallBridge(calls.values());
        constructors = new ConstructorFactory(allocations.values());
    }
    private static <K, V> void merge(Map<K, V> into, Map<K, V> additions, String label) {
        additions.forEach((key, value) -> {
            V old = into.putIfAbsent(key, value);
            if (old != null && !old.equals(value)) throw new IllegalArgumentException("Conflicting " + label + ": " + key);
        });
    }
    public List<String> packIds() { return packIds; }

    /** Verification concerns the bytes at binding time. Later consumers must use the same artifacts. */
    public Bound bind(Map<String, Path> sourcePaths, Map<String, Path> targetPaths) throws IOException {
        verify(sourceArtifacts, sourcePaths, "source");
        verify(targetArtifacts, targetPaths, "target");
        return new Bound(renames);
    }
    private static void verify(Map<String, String> expected, Map<String, Path> paths, String side) throws IOException {
        if (!expected.keySet().equals(paths.keySet()))
            throw new IllegalArgumentException("Exact " + side + " artifact manifest required: " + expected.keySet());
        for (var entry : expected.entrySet()) {
            MessageDigest digest;
            try { digest = MessageDigest.getInstance("SHA-256"); }
            catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
            try (var input = Files.newInputStream(paths.get(entry.getKey()))) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!actual.equals(entry.getValue()))
                throw new IllegalArgumentException("SHA-256 mismatch for " + side + " artifact " + entry.getKey());
        }
    }
    public final class Bound {
        private final ClassAdapter activeRenames;
        private Bound(ClassAdapter activeRenames) { this.activeRenames = activeRenames; }
        public Bound withSourceHierarchy(Map<String, ClassInfo> sourceHierarchy) {
            return new Bound(new ClassAdapter(classNames, memberNames, sourceHierarchy));
        }
        public String mapClassName(String internalName) { return classNames.getOrDefault(internalName, internalName); }
        public byte[] adapt(byte[] original) {
            Objects.requireNonNull(original);
            if (original.length < 8 || original[0] != (byte) 0xca || original[1] != (byte) 0xfe
                    || original[2] != (byte) 0xba || original[3] != (byte) 0xbe)
                throw new IllegalArgumentException("Invalid class header");
            int major = ((original[6] & 255) << 8) | (original[7] & 255);
            int minor = ((original[4] & 255) << 8) | (original[5] & 255);
            if (minor == 65535) throw new IllegalArgumentException("Preview classes require explicit runtime support");
            if (major > source.javaVersion() + 44L || major > target.javaVersion() + 44L)
                throw new IllegalArgumentException("Class requires a newer Java runtime; bytecode downgrading is not implemented");
            return activeRenames.adapt(bridges.adapt(constructors.adapt(original)));
        }
    }
}
