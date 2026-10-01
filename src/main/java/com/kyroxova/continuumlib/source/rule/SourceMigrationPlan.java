package com.kyroxova.continuumlib.source.rule;

import com.kyroxova.continuumlib.bytecode.CallBridge;
import com.kyroxova.continuumlib.bytecode.ConstructorFactory;
import com.kyroxova.continuumlib.bytecode.MemberReference;
import com.kyroxova.continuumlib.knowledge.rule.RulePack;

import java.util.*;

public final class SourceMigrationPlan {
    private record BridgeKey(MemberReference source, int opcode) {}

    private final List<SourceMigrationRule> rules;
    private final Map<String, String> classRenames = new HashMap<>();
    private final Map<String, Map<String, String>> methodRenames = new HashMap<>();
    private final Map<String, SourceMigrationRule.ConstructorToFactory> constructorToFactories = new HashMap<>();
    private final Map<String, Map<String, SourceMigrationRule.FactoryToConstructor>> factoryToConstructors = new HashMap<>();
    private final Map<String, Map<String, SourceMigrationRule.FieldToAccessor>> fieldToAccessors = new HashMap<>();

    // Canonical executable knowledge keeps the full JVM member identity from RulePack.
    private final Map<MemberReference, MemberReference> exactMemberRenames;
    private final Map<MemberReference, ConstructorFactory.Rule> exactConstructorFactories;
    private final Map<BridgeKey, CallBridge.Rule> exactCallBridges;

    public SourceMigrationPlan(Collection<SourceMigrationRule> rules) {
        this(rules, Map.of(), List.of(), List.of());
    }

    private SourceMigrationPlan(Collection<SourceMigrationRule> rules,
                                Map<MemberReference, MemberReference> exactMemberRenames,
                                Collection<ConstructorFactory.Rule> exactConstructorFactories,
                                Collection<CallBridge.Rule> exactCallBridges) {
        this.rules = List.copyOf(rules);
        for (var rule : this.rules) {
            if (rule instanceof SourceMigrationRule.ClassRename cr) {
                putUnique(classRenames, normalize(cr.sourceClass()), normalize(cr.targetClass()), "class rename");
            } else if (rule instanceof SourceMigrationRule.MethodRename mr) {
                putUnique(methodRenames.computeIfAbsent(normalize(mr.ownerClass()), k -> new HashMap<>()),
                        mr.sourceMethod(), mr.targetMethod(), "method rename");
            } else if (rule instanceof SourceMigrationRule.ConstructorToFactory cf) {
                putUnique(constructorToFactories, normalize(cf.constructorOwner()), cf, "constructor factory");
            } else if (rule instanceof SourceMigrationRule.FactoryToConstructor fc) {
                putUnique(factoryToConstructors.computeIfAbsent(normalize(fc.factoryOwner()), k -> new HashMap<>()),
                        fc.factoryMethod(), fc, "factory constructor");
            } else if (rule instanceof SourceMigrationRule.FieldToAccessor fa) {
                putUnique(fieldToAccessors.computeIfAbsent(normalize(fa.ownerClass()), k -> new HashMap<>()),
                        fa.fieldName(), fa, "field accessor");
            }
        }

        Map<MemberReference, MemberReference> members = new HashMap<>();
        exactMemberRenames.forEach((source, target) ->
                putUnique(members, canonical(source), canonical(target), "exact member migration"));
        this.exactMemberRenames = Map.copyOf(members);

        Map<MemberReference, ConstructorFactory.Rule> constructors = new HashMap<>();
        for (ConstructorFactory.Rule rule : exactConstructorFactories) {
            ConstructorFactory.Rule normalized = new ConstructorFactory.Rule(canonical(rule.source()), canonical(rule.factory()));
            putUnique(constructors, normalized.source(), normalized, "exact constructor factory");
        }
        this.exactConstructorFactories = Map.copyOf(constructors);

        Map<BridgeKey, CallBridge.Rule> bridges = new HashMap<>();
        for (CallBridge.Rule rule : exactCallBridges) {
            CallBridge.Rule normalized = new CallBridge.Rule(canonical(rule.source()), rule.opcode(), canonical(rule.hook()));
            putUnique(bridges, new BridgeKey(normalized.source(), normalized.opcode()), normalized, "exact call bridge");
        }
        this.exactCallBridges = Map.copyOf(bridges);
    }

    public static SourceMigrationPlan fromRulePack(RulePack pack) {
        List<SourceMigrationRule> rules = new ArrayList<>();
        for (var entry : pack.classes().entrySet()) {
            rules.add(new SourceMigrationRule.ClassRename(entry.getKey(), entry.getValue()));
        }
        return new SourceMigrationPlan(rules, pack.members(), pack.constructors(), pack.bridges());
    }

    public List<SourceMigrationRule> rules() { return rules; }

    public Optional<String> findClassRename(String className) {
        return Optional.ofNullable(classRenames.get(normalize(className)));
    }

    /** Descriptor-less rules are retained for explicit/manual source rules and tests. */
    public Optional<String> findMethodRename(String owner, String methodName) {
        var map = methodRenames.get(normalize(owner));
        return map != null ? Optional.ofNullable(map.get(methodName)) : Optional.empty();
    }

    public Optional<MemberReference> findExactMemberMigration(MemberReference source) {
        return Optional.ofNullable(exactMemberRenames.get(canonical(source)));
    }

    public Optional<MemberReference> findExactMemberMigration(String owner, String name, String descriptor) {
        return findExactMemberMigration(new MemberReference(internal(owner), name, descriptor));
    }

    public Optional<ConstructorFactory.Rule> findExactConstructorFactory(MemberReference constructor) {
        return Optional.ofNullable(exactConstructorFactories.get(canonical(constructor)));
    }

    public Optional<CallBridge.Rule> findExactCallBridge(MemberReference source, int opcode) {
        return Optional.ofNullable(exactCallBridges.get(new BridgeKey(canonical(source), opcode)));
    }

    public Optional<SourceMigrationRule.ConstructorToFactory> findConstructorToFactory(String owner) {
        return Optional.ofNullable(constructorToFactories.get(normalize(owner)));
    }

    public Optional<SourceMigrationRule.FactoryToConstructor> findFactoryToConstructor(String owner, String methodName) {
        var map = factoryToConstructors.get(normalize(owner));
        return map != null ? Optional.ofNullable(map.get(methodName)) : Optional.empty();
    }

    public Optional<SourceMigrationRule.FieldToAccessor> findFieldToAccessor(String owner, String fieldName) {
        var map = fieldToAccessors.get(normalize(owner));
        return map != null ? Optional.ofNullable(map.get(fieldName)) : Optional.empty();
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final List<SourceMigrationRule> rules = new ArrayList<>();

        public Builder addClassRename(String from, String to) {
            rules.add(new SourceMigrationRule.ClassRename(from, to));
            return this;
        }

        public Builder addMethodRename(String owner, String from, String to) {
            rules.add(new SourceMigrationRule.MethodRename(owner, from, to));
            return this;
        }

        public Builder addConstructorToFactory(String constructorOwner, String factoryOwner, String factoryMethod) {
            rules.add(new SourceMigrationRule.ConstructorToFactory(constructorOwner, factoryOwner, factoryMethod));
            return this;
        }

        public Builder addFactoryToConstructor(String factoryOwner, String factoryMethod, String constructorOwner) {
            rules.add(new SourceMigrationRule.FactoryToConstructor(factoryOwner, factoryMethod, constructorOwner));
            return this;
        }

        public Builder addFieldToAccessor(String owner, String fieldName, String getterMethod) {
            rules.add(new SourceMigrationRule.FieldToAccessor(owner, fieldName, getterMethod));
            return this;
        }

        public SourceMigrationPlan build() {
            return new SourceMigrationPlan(rules);
        }
    }

    private static MemberReference canonical(MemberReference reference) {
        return new MemberReference(internal(reference.owner()), reference.name(), reference.descriptor());
    }

    private static String internal(String name) {
        return name.replace('.', '/');
    }

    private static String normalize(String name) {
        if (name == null) return "";
        return name.replace('/', '.');
    }

    private static <K, V> void putUnique(Map<K, V> map, K key, V value, String label) {
        V previous = map.putIfAbsent(key, value);
        if (previous != null && !previous.equals(value)) {
            throw new IllegalArgumentException("Conflicting " + label + ": " + key);
        }
    }
}
