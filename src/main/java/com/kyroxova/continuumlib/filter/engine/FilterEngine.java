package com.kyroxova.continuumlib.filter.engine;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.kyroxova.continuumlib.filter.condition.TargetContext;
import com.kyroxova.continuumlib.filter.registry.RegistryDeclarationScanner;
import com.kyroxova.continuumlib.filter.registry.RegistryEntry;
import com.kyroxova.continuumlib.filter.registry.RegistryIndex;
import com.kyroxova.continuumlib.filter.rule.ExclusionRuleSet;
import com.kyroxova.continuumlib.filter.rule.InclusionRuleSet;
import com.kyroxova.continuumlib.filter.rule.RegistryFilterRule;
import com.kyroxova.continuumlib.filter.validation.ExclusionConflictDetector;
import com.kyroxova.continuumlib.source.ast.SourceUnit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Executes inclusion and exclusion filtering across source files, registry declarations, and resources.
 * Operates strictly on generated targets; original consumer files are NEVER modified.
 */
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
        InclusionRuleSet activeInclusions = inclusions != null ? inclusions.filterFor(context) : InclusionRuleSet.EMPTY;
        ExclusionRuleSet activeExclusions = exclusions != null ? exclusions.filterFor(context) : ExclusionRuleSet.EMPTY;

        // 1. Filter source files
        List<SourceUnit> activeSourceUnits = new ArrayList<>();
        List<SourceUnit> excludedSourceUnits = new ArrayList<>();

        for (SourceUnit unit : sourceUnits) {
            String relPath = unit.relativePath().replace('\\', '/');
            String primaryClass = extractPrimaryClassName(unit.ast());

            boolean isExcluded = activeExclusions.rules().matchesSource(relPath)
                    || (primaryClass != null && activeExclusions.rules().matchesClass(primaryClass));

            if (isExcluded) {
                excludedSourceUnits.add(unit);
            } else {
                activeSourceUnits.add(unit);
            }
        }

        // 2. Scan and filter registry declarations on active source units
        RegistryIndex registryIndex = scanner.scan(activeSourceUnits);
        List<RegistryEntry> excludedRegistryEntries = new ArrayList<>();

        for (RegistryFilterRule rule : activeExclusions.rules().registryRules()) {
            List<RegistryEntry> matched = registryIndex.findByTypeAndId(rule.registryType(), rule.id());
            for (RegistryEntry entry : matched) {
                RegistryEntry effective = (entry.namespace() == null && rule.id().contains(":"))
                        ? new RegistryEntry(entry.registryType(), rule.id().substring(0, rule.id().indexOf(':')), entry.id(), entry.ownerClass(), entry.fieldName(), entry.sourcePath(), entry.lineNumber())
                        : entry;
                excludedRegistryEntries.add(effective);
                removeDeclarationFromAst(entry, activeSourceUnits);
            }
        }

        // 3. Validate no dangling references to excluded declarations
        conflictDetector.validate(context, excludedRegistryEntries, activeSourceUnits);

        // 4. Filter resources
        Map<String, Path> activeResources = new TreeMap<>();
        Map<String, Path> excludedResources = new TreeMap<>();

        if (resources != null) {
            for (var entry : resources.entrySet()) {
                String resPath = entry.getKey().replace('\\', '/');
                if (activeExclusions.rules().matchesResource(resPath)) {
                    excludedResources.put(resPath, entry.getValue());
                } else {
                    activeResources.put(resPath, entry.getValue());
                }
            }
        }

        return new FilterResult(
                activeSourceUnits,
                excludedSourceUnits,
                excludedRegistryEntries,
                activeResources,
                excludedResources
        );
    }

    public static Map<String, Path> discoverResources(Path resourcesDir) throws IOException {
        if (resourcesDir == null || !Files.exists(resourcesDir) || !Files.isDirectory(resourcesDir)) {
            return Map.of();
        }
        Map<String, Path> map = new TreeMap<>();
        try (Stream<Path> stream = Files.walk(resourcesDir)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                String rel = resourcesDir.relativize(p).toString().replace('\\', '/');
                map.put(rel, p);
            });
        }
        return Collections.unmodifiableMap(map);
    }

    private static String extractPrimaryClassName(CompilationUnit ast) {
        String pkg = ast.getPackageDeclaration().map(p -> p.getNameAsString() + ".").orElse("");
        return ast.findFirst(ClassOrInterfaceDeclaration.class)
                .map(c -> pkg + c.getNameAsString())
                .orElse(null);
    }

    private static void removeDeclarationFromAst(RegistryEntry entry, List<SourceUnit> units) {
        for (SourceUnit unit : units) {
            if (unit.relativePath().replace('\\', '/').equals(entry.sourcePath().replace('\\', '/'))) {
                for (var cid : unit.ast().findAll(ClassOrInterfaceDeclaration.class)) {
                    for (FieldDeclaration fd : new ArrayList<>(cid.getFields())) {
                        fd.getVariables().removeIf(v -> v.getNameAsString().equals(entry.fieldName()));
                        if (fd.getVariables().isEmpty()) {
                            fd.remove();
                        }
                    }
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
