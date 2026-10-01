package com.kyroxova.continuumlib.source.rule;

import com.kyroxova.continuumlib.bytecode.ConstructorFactory;
import com.kyroxova.continuumlib.bytecode.MemberReference;
import com.kyroxova.continuumlib.knowledge.rule.RulePack;

import java.util.*;

public final class SourceMigrationPlan {
    private final List<SourceMigrationRule> rules;
    private final Map<String, String> classRenames = new HashMap<>();
    private final Map<String, Map<String, String>> methodRenames = new HashMap<>();
    private final Map<String, SourceMigrationRule.ConstructorToFactory> constructorToFactories = new HashMap<>();
    private final Map<String, Map<String, SourceMigrationRule.FactoryToConstructor>> factoryToConstructors = new HashMap<>();
    private final Map<String, Map<String, SourceMigrationRule.FieldToAccessor>> fieldToAccessors = new HashMap<>();

    public SourceMigrationPlan(Collection<SourceMigrationRule> rules) {
        this.rules = List.copyOf(rules);
        for (var rule : this.rules) {
            if (rule instanceof SourceMigrationRule.ClassRename cr) {
                classRenames.put(normalize(cr.sourceClass()), normalize(cr.targetClass()));
            } else if (rule instanceof SourceMigrationRule.MethodRename mr) {
                methodRenames.computeIfAbsent(normalize(mr.ownerClass()), k -> new HashMap<>())
                        .put(mr.sourceMethod(), mr.targetMethod());
            } else if (rule instanceof SourceMigrationRule.ConstructorToFactory cf) {
                constructorToFactories.put(normalize(cf.constructorOwner()), cf);
            } else if (rule instanceof SourceMigrationRule.FactoryToConstructor fc) {
                factoryToConstructors.computeIfAbsent(normalize(fc.factoryOwner()), k -> new HashMap<>())
                        .put(fc.factoryMethod(), fc);
            } else if (rule instanceof SourceMigrationRule.FieldToAccessor fa) {
                fieldToAccessors.computeIfAbsent(normalize(fa.ownerClass()), k -> new HashMap<>())
                        .put(fa.fieldName(), fa);
            }
        }
    }

    public static SourceMigrationPlan fromRulePack(RulePack pack) {
        List<SourceMigrationRule> rules = new ArrayList<>();
        for (var entry : pack.classes().entrySet()) {
            rules.add(new SourceMigrationRule.ClassRename(entry.getKey(), entry.getValue()));
        }
        for (var entry : pack.members().entrySet()) {
            MemberReference src = entry.getKey();
            MemberReference tgt = entry.getValue();
            if (!src.name().equals(tgt.name())) {
                rules.add(new SourceMigrationRule.MethodRename(src.owner(), src.name(), tgt.name()));
            }
        }
        for (ConstructorFactory.Rule c : pack.constructors()) {
            rules.add(new SourceMigrationRule.ConstructorToFactory(c.source().owner(), c.factory().owner(), c.factory().name()));
        }
        return new SourceMigrationPlan(rules);
    }

    public List<SourceMigrationRule> rules() { return rules; }

    public Optional<String> findClassRename(String className) {
        return Optional.ofNullable(classRenames.get(normalize(className)));
    }

    public Optional<String> findMethodRename(String owner, String methodName) {
        var map = methodRenames.get(normalize(owner));
        return map != null ? Optional.ofNullable(map.get(methodName)) : Optional.empty();
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

    private static String normalize(String name) {
        if (name == null) return "";
        return name.replace('/', '.');
    }
}
