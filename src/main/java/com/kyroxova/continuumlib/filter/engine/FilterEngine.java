package com.kyroxova.continuumlib.filter.engine;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.kyroxova.continuumlib.filter.condition.TargetContext;
import com.kyroxova.continuumlib.filter.config.FilterConfigurationException;
import com.kyroxova.continuumlib.filter.registry.RegistryDeclarationScanner;
import com.kyroxova.continuumlib.filter.registry.RegistryEntry;
import com.kyroxova.continuumlib.filter.registry.RegistryIndex;
import com.kyroxova.continuumlib.filter.rule.ExclusionRuleSet;
import com.kyroxova.continuumlib.filter.rule.FilterRule;
import com.kyroxova.continuumlib.filter.rule.InclusionRuleSet;
import com.kyroxova.continuumlib.filter.rule.RuleSet;
import com.kyroxova.continuumlib.filter.validation.ExclusionConflictDetector;
import com.kyroxova.continuumlib.source.ast.SourceUnit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public final class FilterEngine {
    private final RegistryDeclarationScanner scanner = new RegistryDeclarationScanner();
    private final ExclusionConflictDetector conflictDetector = new ExclusionConflictDetector();

    public FilterResult process(
            TargetContext context,
            InclusionRuleSet inclusions,
            ExclusionRuleSet exclusions,
            List<SourceUnit> sourceUnits,
            Map<String, Path> resources
    ) {
        Objects.requireNonNull(context, "context");
        RuleSet include = (inclusions != null ? inclusions : InclusionRuleSet.EMPTY).filterFor(context).rules();
        RuleSet exclude = (exclusions != null ? exclusions : ExclusionRuleSet.EMPTY).filterFor(context).rules();
        validateActiveContradictions(include, exclude);

        List<SourceUnit> activeSourceUnits = new ArrayList<>();
        List<SourceUnit> excludedSourceUnits = new ArrayList<>();

        for (SourceUnit unit : sourceUnits) {
            String relPath = unit.relativePath().replace('\\', '/');
            boolean sourceAllowed = !include.hasSourceRules() || include.matchesSource(relPath);
            boolean sourceExcluded = exclude.matchesSource(relPath);

            if (!sourceAllowed || sourceExcluded) {
                excludedSourceUnits.add(unit);
                continue;
            }

            if (applyTopLevelClassFilters(unit.ast(), include, exclude)) {
                activeSourceUnits.add(unit);
            } else {
                excludedSourceUnits.add(unit);
            }
        }

        RegistryIndex registryIndex = scanner.scan(activeSourceUnits);
        List<RegistryEntry> excludedRegistryEntries = new ArrayList<>();

        for (RegistryEntry entry : registryIndex.entries()) {
            boolean included = !include.hasRegistryRules()
                    || include.matchesRegistry(entry.registryType(), entry.fullId());
            boolean excluded = exclude.matchesRegistry(entry.registryType(), entry.fullId());

            if (!included || excluded) {
                excludedRegistryEntries.add(effectiveEntry(entry, excluded ? exclude : include));
                removeDeclarationFromAst(entry, activeSourceUnits);
            }
        }

        conflictDetector.validate(context, excludedRegistryEntries, activeSourceUnits);

        Map<String, Path> activeResources = new TreeMap<>();
        Map<String, Path> excludedResources = new TreeMap<>();

        if (resources != null) {
            for (var entry : resources.entrySet()) {
                String path = entry.getKey().replace('\\', '/');
                boolean included = !include.hasResourceRules() || include.matchesResource(path);
                boolean excluded = exclude.matchesResource(path);

                if (included && !excluded) {
                    activeResources.put(path, entry.getValue());
                } else {
                    excludedResources.put(path, entry.getValue());
                }
            }
        }

        return new FilterResult(
                List.copyOf(activeSourceUnits),
                List.copyOf(excludedSourceUnits),
                List.copyOf(excludedRegistryEntries),
                Map.copyOf(activeResources),
                Map.copyOf(excludedResources)
        );
    }

    public static Map<String, Path> discoverResources(Path resourcesDir) throws IOException {
        if (resourcesDir == null || !Files.isDirectory(resourcesDir)) {
            return Map.of();
        }
        Map<String, Path> map = new TreeMap<>();
        try (Stream<Path> stream = Files.walk(resourcesDir)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String relative = resourcesDir.relativize(path).toString().replace('\\', '/');
                map.put(relative, path);
            });
        }
        return Collections.unmodifiableMap(map);
    }

    private static void validateActiveContradictions(RuleSet inclusions, RuleSet exclusions) {
        if (inclusions.isEmpty() || exclusions.isEmpty()) return;

        for (FilterRule inclusion : inclusions.allRules()) {
            for (FilterRule exclusion : exclusions.allRules()) {
                if (inclusion.domain() == exclusion.domain()
                        && inclusion.targetIdentifier().equals(exclusion.targetIdentifier())) {
                    throw new FilterConfigurationException(
                            "Contradictory active rules for " + inclusion.domain() + " "
                                    + inclusion.targetIdentifier() + ": included by "
                                    + inclusion.sourceFile() + " and excluded by " + exclusion.sourceFile()
                    );
                }
            }
        }
    }

    private static RegistryEntry effectiveEntry(RegistryEntry entry, RuleSet rules) {
        if (entry.namespace() != null) return entry;

        return rules.registryRules().stream()
                .filter(rule -> rule.registryType() == entry.registryType())
                .filter(rule -> {
                    String id = rule.id();
                    int colon = id.indexOf(':');
                    return colon >= 0 && id.substring(colon + 1).equals(entry.id());
                })
                .findFirst()
                .map(rule -> {
                    String id = rule.id();
                    return new RegistryEntry(
                            entry.registryType(),
                            id.substring(0, id.indexOf(':')),
                            entry.id(),
                            entry.ownerClass(),
                            entry.fieldName(),
                            entry.sourcePath(),
                            entry.lineNumber()
                    );
                })
                .orElse(entry);
    }

    private static boolean applyTopLevelClassFilters(
            CompilationUnit ast,
            RuleSet inclusions,
            RuleSet exclusions
    ) {
        if (!inclusions.hasClassRules() && !exclusions.hasClassRules()) {
            return true;
        }

        if (ast.getTypes().isEmpty()) {
            // package-info.java and other source units without top-level types are outside CLASS filtering.
            return true;
        }

        String pkg = ast.getPackageDeclaration().map(p -> p.getNameAsString() + ".").orElse("");
        for (TypeDeclaration<?> type : new ArrayList<>(ast.getTypes())) {
            String qualifiedName = pkg + type.getNameAsString();
            boolean included = !inclusions.hasClassRules() || inclusions.matchesClass(qualifiedName);
            boolean excluded = exclusions.matchesClass(qualifiedName);
            if (!included || excluded) {
                type.remove();
            }
        }
        return !ast.getTypes().isEmpty();
    }

    private static void removeDeclarationFromAst(RegistryEntry entry, List<SourceUnit> units) {
        for (SourceUnit unit : units) {
            if (!unit.relativePath().replace('\\', '/').equals(entry.sourcePath().replace('\\', '/'))) {
                continue;
            }
            for (var type : unit.ast().findAll(ClassOrInterfaceDeclaration.class)) {
                for (FieldDeclaration field : new ArrayList<>(type.getFields())) {
                    field.getVariables().removeIf(variable -> variable.getNameAsString().equals(entry.fieldName()));
                    if (field.getVariables().isEmpty()) field.remove();
                }
            }
        }
    }

    public record FilterResult(
            List<SourceUnit> activeSources,
            List<SourceUnit> excludedSources,
            List<RegistryEntry> excludedRegistryEntries,
            Map<String, Path> activeResources,
            Map<String, Path> excludedResources
    ) {}
}
