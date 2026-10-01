package com.kyroxova.continuumlib.filter.rule;

import com.kyroxova.continuumlib.filter.condition.TargetContext;
import com.kyroxova.continuumlib.filter.domain.RegistryType;

import java.util.*;

/**
 * Normalized collection of project filter rules.
 */
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
        List<RegistryFilterRule> activeRegistry = registryRules.stream()
                .filter(r -> r.condition().matches(context))
                .toList();
        List<SourceFilterRule> activeSource = sourceRules.stream()
                .filter(r -> r.condition().matches(context))
                .toList();
        List<ClassFilterRule> activeClass = classRules.stream()
                .filter(r -> r.condition().matches(context))
                .toList();
        List<ResourceFilterRule> activeResource = resourceRules.stream()
                .filter(r -> r.condition().matches(context))
                .toList();

        return new RuleSet(activeRegistry, activeSource, activeClass, activeResource);
    }

    public boolean matchesRegistry(RegistryType type, String id) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
        String normId = id.trim();
        for (var rule : registryRules) {
            if (rule.registryType().equals(type)) {
                if (rule.id().equals(normId)) return true;
                if (!rule.id().contains(":") && normId.contains(":")) {
                    String pathPart = normId.substring(normId.indexOf(':') + 1);
                    if (rule.id().equals(pathPart)) return true;
                }
            }
        }
        return false;
    }

    public boolean matchesSource(String path) {
        Objects.requireNonNull(path, "path");
        String norm = path.replace('\\', '/').trim();
        while (norm.startsWith("/")) norm = norm.substring(1);
        for (var rule : sourceRules) {
            if (rule.path().equals(norm)) return true;
        }
        return false;
    }

    public boolean matchesClass(String className) {
        Objects.requireNonNull(className, "className");
        String norm = className.replace('/', '.').trim();
        for (var rule : classRules) {
            if (rule.className().equals(norm)) return true;
        }
        return false;
    }

    public boolean matchesResource(String path) {
        Objects.requireNonNull(path, "path");
        String norm = path.replace('\\', '/').trim();
        while (norm.startsWith("/")) norm = norm.substring(1);
        for (var rule : resourceRules) {
            if (rule.path().equals(norm)) return true;
        }
        return false;
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
