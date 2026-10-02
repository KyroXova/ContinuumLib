package com.kyroxova.continuumlib.pipeline.migration;

import com.kyroxova.continuumlib.bytecode.CallBridge;
import com.kyroxova.continuumlib.bytecode.ConstructorFactory;
import com.kyroxova.continuumlib.bytecode.MemberReference;
import com.kyroxova.continuumlib.knowledge.rule.RulePack;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;

import java.util.*;

public final class CanonicalMigrationPlan {
    private final List<CanonicalMigrationRule> rules;
    private final Map<String, CanonicalMigrationRule> classRenames = new HashMap<>();
    private final Map<String, List<CanonicalMigrationRule>> methodRules = new HashMap<>();
    private final Map<String, List<CanonicalMigrationRule>> fieldRenames = new HashMap<>();
    private final Map<String, List<CanonicalMigrationRule>> constructorToFactories = new HashMap<>();
    private final Map<String, List<CanonicalMigrationRule>> factoryToConstructors = new HashMap<>();
    private final Map<String, List<CanonicalMigrationRule>> fieldToAccessors = new HashMap<>();
    private final Map<String, List<CanonicalMigrationRule>> callBridges = new HashMap<>();

    public CanonicalMigrationPlan(Collection<CanonicalMigrationRule> rules) {
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        validateConflicts(this.rules);
        for (var rule : this.rules) {
            switch (rule.type()) {
                case CLASS_RENAME -> classRenames.put(normalize(rule.sourceOwner()), rule);
                case MEMBER_RENAME -> {
                    String key = normalize(rule.sourceOwner()) + "#" + rule.sourceName();
                    methodRules.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
                }
                case FIELD_RENAME -> {
                    String key = normalize(rule.sourceOwner()) + "#" + rule.sourceName();
                    fieldRenames.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
                }
                case CONSTRUCTOR_TO_FACTORY -> {
                    String key = normalize(rule.sourceOwner());
                    constructorToFactories.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
                }
                case FACTORY_TO_CONSTRUCTOR -> {
                    String key = normalize(rule.sourceOwner()) + "#" + rule.sourceName();
                    factoryToConstructors.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
                }
                case FIELD_TO_ACCESSOR -> {
                    String key = normalize(rule.sourceOwner()) + "#" + rule.sourceName();
                    fieldToAccessors.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
                }
                case CALL_BRIDGE -> {
                    String key = normalize(rule.sourceOwner()) + "#" + rule.sourceName();
                    callBridges.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
                }
            }
        }
    }

    public static CanonicalMigrationPlan fromRulePacks(Collection<RulePack> packs) {
        List<CanonicalMigrationRule> rules = new ArrayList<>();
        for (RulePack pack : packs) {
            String packId = pack.id();
            EnvironmentId srcEnv = pack.source();
            EnvironmentId tgtEnv = pack.target();
            String evidence = pack.evidence();

            for (var entry : pack.classes().entrySet()) {
                rules.add(new CanonicalMigrationRule(
                        packId,
                        MigrationType.CLASS_RENAME,
                        entry.getKey(),
                        null,
                        null,
                        entry.getValue(),
                        null,
                        null,
                        0,
                        srcEnv,
                        tgtEnv,
                        MigrationLayer.SOURCE_AST,
                        evidence
                ));
            }

            for (var entry : pack.members().entrySet()) {
                MemberReference src = entry.getKey();
                MemberReference tgt = entry.getValue();
                boolean isMethod = src.descriptor().startsWith("(");
                rules.add(new CanonicalMigrationRule(
                        packId,
                        isMethod ? MigrationType.MEMBER_RENAME : MigrationType.FIELD_RENAME,
                        src.owner(),
                        src.name(),
                        src.descriptor(),
                        tgt.owner(),
                        tgt.name(),
                        tgt.descriptor(),
                        0,
                        srcEnv,
                        tgtEnv,
                        MigrationLayer.SOURCE_AST,
                        evidence
                ));
            }

            for (ConstructorFactory.Rule c : pack.constructors()) {
                rules.add(new CanonicalMigrationRule(
                        packId,
                        MigrationType.CONSTRUCTOR_TO_FACTORY,
                        c.source().owner(),
                        c.source().name(),
                        c.source().descriptor(),
                        c.factory().owner(),
                        c.factory().name(),
                        c.factory().descriptor(),
                        0,
                        srcEnv,
                        tgtEnv,
                        MigrationLayer.SOURCE_AST,
                        evidence
                ));
            }

            for (CallBridge.Rule b : pack.bridges()) {
                rules.add(new CanonicalMigrationRule(
                        packId,
                        MigrationType.CALL_BRIDGE,
                        b.source().owner(),
                        b.source().name(),
                        b.source().descriptor(),
                        b.hook().owner(),
                        b.hook().name(),
                        b.hook().descriptor(),
                        b.opcode(),
                        srcEnv,
                        tgtEnv,
                        MigrationLayer.BYTECODE,
                        evidence
                ));
            }
        }
        return new CanonicalMigrationPlan(rules);
    }

    public List<CanonicalMigrationRule> rules() { return rules; }

    public Optional<CanonicalMigrationRule> findClassRename(String className) {
        CanonicalMigrationRule exact = classRenames.get(normalize(className));
        if (exact != null) return Optional.of(exact);

        String sourceName = sourceName(className);
        List<CanonicalMigrationRule> aliases = classRenames.values().stream()
                .filter(rule -> sourceName(rule.sourceOwner()).equals(sourceName))
                .toList();
        return aliases.size() == 1 ? Optional.of(aliases.get(0)) : Optional.empty();
    }

    public List<CanonicalMigrationRule> findMethodRules(String owner, String methodName) {
        return rules(methodRules, owner, methodName);
    }

    public List<CanonicalMigrationRule> findFieldRenames(String owner, String fieldName) {
        return rules(fieldRenames, owner, fieldName);
    }

    public Optional<CanonicalMigrationRule> findExactMemberRename(com.kyroxova.continuumlib.bytecode.MemberReference source) {
        return findExact(methodRules, source);
    }

    public Optional<CanonicalMigrationRule> findExactFieldRename(com.kyroxova.continuumlib.bytecode.MemberReference source) {
        return findExact(fieldRenames, source);
    }

    public Optional<CanonicalMigrationRule> findExactConstructorFactory(com.kyroxova.continuumlib.bytecode.MemberReference source) {
        return exactOwnerRules(constructorToFactories, source.owner()).stream()
                .filter(rule -> Objects.equals(rule.sourceDescriptor(), source.descriptor()))
                .findFirst();
    }

    public Optional<CanonicalMigrationRule> findExactCallBridge(
            com.kyroxova.continuumlib.bytecode.MemberReference source,
            int opcode
    ) {
        return exactRules(callBridges, source.owner(), source.name()).stream()
                .filter(rule -> Objects.equals(rule.sourceDescriptor(), source.descriptor()) && rule.opcode() == opcode)
                .findFirst();
    }

    public List<CanonicalMigrationRule> findCallBridges(String owner, String name) {
        return rules(callBridges, owner, name);
    }


    public List<CanonicalMigrationRule> findConstructorToFactories(String owner) {
        return ownerRules(constructorToFactories, owner);
    }

    public List<CanonicalMigrationRule> findFactoryToConstructors(String owner, String methodName) {
        return rules(factoryToConstructors, owner, methodName);
    }

    public List<CanonicalMigrationRule> findFieldToAccessors(String owner, String fieldName) {
        return rules(fieldToAccessors, owner, fieldName);
    }

    private static List<CanonicalMigrationRule> rules(
            Map<String, List<CanonicalMigrationRule>> index,
            String owner,
            String name
    ) {
        List<CanonicalMigrationRule> exact = exactRules(index, owner, name);
        if (!exact.isEmpty()) return exact;

        String requestedOwner = sourceName(owner);
        List<CanonicalMigrationRule> aliases = new ArrayList<>();
        for (var entry : index.entrySet()) {
            int separator = entry.getKey().lastIndexOf('#');
            if (separator < 0 || !entry.getKey().substring(separator + 1).equals(name)) continue;
            String indexedOwner = entry.getKey().substring(0, separator);
            if (sourceName(indexedOwner).equals(requestedOwner)) {
                aliases.addAll(entry.getValue());
            }
        }
        return List.copyOf(aliases);
    }

    private static List<CanonicalMigrationRule> exactRules(
            Map<String, List<CanonicalMigrationRule>> index,
            String owner,
            String name
    ) {
        String key = normalize(owner) + "#" + name;
        var list = index.get(key);
        return list != null ? Collections.unmodifiableList(list) : List.of();
    }

    private static List<CanonicalMigrationRule> ownerRules(
            Map<String, List<CanonicalMigrationRule>> index,
            String owner
    ) {
        List<CanonicalMigrationRule> exact = exactOwnerRules(index, owner);
        if (!exact.isEmpty()) return exact;

        String requestedOwner = sourceName(owner);
        List<CanonicalMigrationRule> aliases = new ArrayList<>();
        for (var entry : index.entrySet()) {
            if (sourceName(entry.getKey()).equals(requestedOwner)) {
                aliases.addAll(entry.getValue());
            }
        }
        return List.copyOf(aliases);
    }

    private static List<CanonicalMigrationRule> exactOwnerRules(
            Map<String, List<CanonicalMigrationRule>> index,
            String owner
    ) {
        var list = index.get(normalize(owner));
        return list != null ? Collections.unmodifiableList(list) : List.of();
    }

    private static Optional<CanonicalMigrationRule> findExact(
            Map<String, List<CanonicalMigrationRule>> index,
            com.kyroxova.continuumlib.bytecode.MemberReference source
    ) {
        return exactRules(index, source.owner(), source.name()).stream()
                .filter(rule -> Objects.equals(rule.sourceDescriptor(), source.descriptor()))
                .findFirst();
    }

    public List<CanonicalMigrationRule> rulesForLayer(MigrationLayer layer) {
        return rules.stream().filter(r -> r.layer() == layer).toList();
    }

    public List<CanonicalMigrationRule> unappliedBytecodeRules(Set<CanonicalMigrationRule> appliedRules) {
        return rules.stream()
                .filter(r -> r.layer() == MigrationLayer.BYTECODE && !appliedRules.contains(r))
                .toList();
    }

    private static void validateConflicts(List<CanonicalMigrationRule> rules) {
        for (int i = 0; i < rules.size(); i++) {
            CanonicalMigrationRule left = rules.get(i);
            for (int j = i + 1; j < rules.size(); j++) {
                CanonicalMigrationRule right = rules.get(j);
                if (!overlaps(left, right)) continue;
                if (sameTransformation(left, right)) continue;

                throw new IllegalArgumentException(
                        "Conflicting canonical migration rules for "
                                + sourceIdentity(left) + ": "
                                + targetIdentity(left) + " vs " + targetIdentity(right)
                );
            }
        }
    }

    private static boolean overlaps(CanonicalMigrationRule left, CanonicalMigrationRule right) {
        if (left.type() != right.type()) return false;
        if (!sourceName(left.sourceOwner()).equals(sourceName(right.sourceOwner()))) return false;

        return switch (left.type()) {
            case CLASS_RENAME -> true;
            case CONSTRUCTOR_TO_FACTORY ->
                    descriptorsOverlap(left.sourceDescriptor(), right.sourceDescriptor());
            case CALL_BRIDGE ->
                    Objects.equals(left.sourceName(), right.sourceName())
                            && descriptorsOverlap(left.sourceDescriptor(), right.sourceDescriptor())
                            && left.opcode() == right.opcode();
            case MEMBER_RENAME, FIELD_RENAME, FACTORY_TO_CONSTRUCTOR, FIELD_TO_ACCESSOR ->
                    Objects.equals(left.sourceName(), right.sourceName())
                            && descriptorsOverlap(left.sourceDescriptor(), right.sourceDescriptor());
        };
    }

    private static boolean descriptorsOverlap(String left, String right) {
        return left == null || right == null || left.equals(right);
    }

    private static boolean sameTransformation(CanonicalMigrationRule left, CanonicalMigrationRule right) {
        return sourceName(left.targetOwner()).equals(sourceName(right.targetOwner()))
                && Objects.equals(left.targetName(), right.targetName())
                && Objects.equals(left.targetDescriptor(), right.targetDescriptor())
                && left.layer() == right.layer();
    }

    private static String sourceIdentity(CanonicalMigrationRule rule) {
        StringBuilder value = new StringBuilder(sourceName(rule.sourceOwner()));
        if (rule.sourceName() != null) value.append('#').append(rule.sourceName());
        if (rule.sourceDescriptor() != null) value.append(rule.sourceDescriptor());
        if (rule.type() == MigrationType.CALL_BRIDGE) value.append(" opcode=").append(rule.opcode());
        return value.toString();
    }

    private static String targetIdentity(CanonicalMigrationRule rule) {
        StringBuilder value = new StringBuilder(sourceName(rule.targetOwner()));
        if (rule.targetName() != null) value.append('#').append(rule.targetName());
        if (rule.targetDescriptor() != null) value.append(rule.targetDescriptor());
        value.append(" [").append(rule.layer()).append(']');
        return value.toString();
    }

    private static String normalize(String name) {
        if (name == null) return "";
        return name.replace('/', '.');
    }

    private static String sourceName(String name) {
        return normalize(name).replace((char) 36, '.');
    }
}
