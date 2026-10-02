package com.kyroxova.continuumlib.filter.rule;

import com.kyroxova.continuumlib.filter.condition.TargetContext;
import com.kyroxova.continuumlib.filter.domain.RegistryType;

import java.util.*;

public record RuleSet(
        List<RegistryFilterRule> registryRules,
        List<SourceFilterRule> sourceRules,
        List<ClassFilterRule> classRules,
        List<ResourceFilterRule> resourceRules
) {
    public static final RuleSet EMPTY = new RuleSet(List.of(), List.of(), List.of(), List.of());

    public RuleSet {
        registryRules = List.copyOf(registryRules);
        sourceRules = List.copyOf(sourceRules);
        classRules = List.copyOf(classRules);
        resourceRules = List.copyOf(resourceRules);
    }

    public boolean isEmpty() {
        return registryRules.isEmpty() && sourceRules.isEmpty() && classRules.isEmpty() && resourceRules.isEmpty();
    }

    public boolean hasRegistryRules() { return !registryRules.isEmpty(); }
    public boolean hasSourceRules() { return !sourceRules.isEmpty(); }
    public boolean hasClassRules() { return !classRules.isEmpty(); }
    public boolean hasResourceRules() { return !resourceRules.isEmpty(); }

    public List<FilterRule> allRules() {
        List<FilterRule> all = new ArrayList<>();
        all.addAll(registryRules);
        all.addAll(sourceRules);
        all.addAll(classRules);
        all.addAll(resourceRules);
        return Collections.unmodifiableList(all);
    }

    public RuleSet filterFor(TargetContext context) {
        Objects.requireNonNull(context, "context");
        return new RuleSet(
                registryRules.stream().filter(r -> r.condition().matches(context)).toList(),
                sourceRules.stream().filter(r -> r.condition().matches(context)).toList(),
                classRules.stream().filter(r -> r.condition().matches(context)).toList(),
                resourceRules.stream().filter(r -> r.condition().matches(context)).toList()
        );
    }

    public boolean matchesRegistry(RegistryType type, String id) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
        String normId = id.trim();
        for (var rule : registryRules) {
            if (!rule.registryType().equals(type)) continue;
            String ruleId = rule.id();
            if (ruleId.equals(normId)) return true;

            int ruleColon = ruleId.indexOf(':');
            int idColon = normId.indexOf(':');

            // A namespaced rule is exact. Do not apply it to an entry whose namespace is unknown.
            if (ruleColon >= 0 && idColon < 0) {
                continue;
            }
            // An unqualified rule intentionally targets that path in any namespace.
            if (ruleColon < 0 && idColon >= 0 && ruleId.equals(normId.substring(idColon + 1))) {
                return true;
            }
        }
        return false;
    }

    public boolean matchesSource(String path) {
        Objects.requireNonNull(path, "path");
        String norm = normalizePath(path);
        return sourceRules.stream().anyMatch(rule -> rule.path().equals(norm));
    }

    public boolean matchesClass(String className) {
        Objects.requireNonNull(className, "className");
        String norm = className.replace('/', '.').trim();
        return classRules.stream().anyMatch(rule -> rule.className().equals(norm));
    }

    public boolean matchesResource(String path) {
        Objects.requireNonNull(path, "path");
        String norm = normalizePath(path);
        return resourceRules.stream().anyMatch(rule -> rule.path().equals(norm));
    }

    private static String normalizePath(String path) {
        String norm = path.replace('\\', '/').trim();
        while (norm.startsWith("/")) norm = norm.substring(1);
        return norm;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<RegistryFilterRule> registryRules = new ArrayList<>();
        private final List<SourceFilterRule> sourceRules = new ArrayList<>();
        private final List<ClassFilterRule> classRules = new ArrayList<>();
        private final List<ResourceFilterRule> resourceRules = new ArrayList<>();

        public Builder addRegistry(RegistryFilterRule rule) {
            registryRules.add(Objects.requireNonNull(rule, "rule"));
            return this;
        }

        public Builder addSource(SourceFilterRule rule) {
            sourceRules.add(Objects.requireNonNull(rule, "rule"));
            return this;
        }

        public Builder addClass(ClassFilterRule rule) {
            classRules.add(Objects.requireNonNull(rule, "rule"));
            return this;
        }

        public Builder addResource(ResourceFilterRule rule) {
            resourceRules.add(Objects.requireNonNull(rule, "rule"));
            return this;
        }

        public Builder addAll(RuleSet other) {
            if (other != null) {
                registryRules.addAll(other.registryRules());
                sourceRules.addAll(other.sourceRules());
                classRules.addAll(other.classRules());
                resourceRules.addAll(other.resourceRules());
            }
            return this;
        }

        public RuleSet build() {
            return new RuleSet(registryRules, sourceRules, classRules, resourceRules);
        }
    }
}
