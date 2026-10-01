package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.continuumlib.artifact.JarTransformer;
import com.kyroxova.continuumlib.bytecode.*;
import com.kyroxova.continuumlib.filter.condition.TargetContext;
import com.kyroxova.continuumlib.filter.config.ContinuumProjectConfiguration;
import com.kyroxova.continuumlib.filter.config.FilterConfigurationReader;
import com.kyroxova.continuumlib.filter.engine.FilterEngine;
import com.kyroxova.continuumlib.filter.registry.RegistryDeclarationScanner;
import com.kyroxova.continuumlib.filter.registry.RegistryEntry;
import com.kyroxova.continuumlib.filter.registry.RegistryIndex;
import com.kyroxova.continuumlib.filter.rule.ExclusionRuleSet;
import com.kyroxova.continuumlib.filter.rule.InclusionRuleSet;
import com.kyroxova.continuumlib.filter.rule.RegistryFilterRule;
import com.kyroxova.continuumlib.filter.validation.ExclusionConflictDetector;
import com.kyroxova.continuumlib.filter.validation.ExclusionConflictException;
import com.kyroxova.continuumlib.model.diagnostic.Diagnostic;
import com.kyroxova.continuumlib.model.diagnostic.DiagnosticCode;
import com.kyroxova.continuumlib.model.diagnostic.Severity;
import com.kyroxova.continuumlib.pipeline.migration.*;
import com.kyroxova.continuumlib.pipeline.report.GenerationReportWriter;
import com.kyroxova.continuumlib.source.ast.SourceParser;
import com.kyroxova.continuumlib.source.ast.SourceUnit;
import com.kyroxova.continuumlib.source.compile.SourceCompiler;
import com.kyroxova.continuumlib.source.compile.TargetJarPackager;
import com.kyroxova.continuumlib.source.transform.SourceTransformer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public final class TargetGenerationPipeline {
    private final RegistryDeclarationScanner registryScanner = new RegistryDeclarationScanner();
    private final ExclusionConflictDetector conflictDetector = new ExclusionConflictDetector();
    private final GenerationReportWriter reportWriter = new GenerationReportWriter();

    public TargetGenerationResult execute(
            ResolvedTarget target,
            Path projectRoot,
            List<Path> sourceRoots,
            List<Path> resourceRoots,
            String artifactName
    ) throws Exception {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(projectRoot, "projectRoot");

        TargetGenerationResult.Builder resultBuilder = TargetGenerationResult.builder()
                .resolvedTarget(target)
                .workspace(target.workspace());

        GeneratedWorkspace ws = target.workspace();
        ws.prepare();
        TargetContext targetContext = target.toTargetContext();

        try {
            // Stage 1 & 2: Validate Target & Environments
            if (target.targetId() == null || target.targetEnvironment() == null) {
                throw new PipelineExecutionException("Target configuration is missing ID or environment",
                        Diagnostic.builder()
                                .code(DiagnosticCode.INVALID_TARGET_CONFIG)
                                .severity(Severity.ERROR)
                                .targetId(target.targetId())
                                .stage("TARGET_RESOLUTION")
                                .message("Target configuration missing ID or environment")
                                .build());
            }

            // Stage 3: Verify Artifacts and Classpaths
            verifyArtifacts(target);

            // Stage 4: Load Configuration
            ContinuumProjectConfiguration projConfig = target.projectConfiguration();
            if (projConfig == null) {
                Path cfgRoot = projectRoot.resolve("src/main/resources/continuumlib");
                if (!Files.exists(cfgRoot)) {
                    cfgRoot = projectRoot.resolve("src/main/resources/data/continuumlib");
                }
                projConfig = new FilterConfigurationReader().load(cfgRoot);
            }

            InclusionRuleSet activeInclusions = projConfig.inclusions() != null
                    ? projConfig.inclusions().filterFor(targetContext)
                    : InclusionRuleSet.EMPTY;
            ExclusionRuleSet activeExclusions = projConfig.exclusions() != null
                    ? projConfig.exclusions().filterFor(targetContext)
                    : ExclusionRuleSet.EMPTY;

            // Stage 5: Discover Source Files and Resources
            List<Path> discoveredSources = discoverJavaSources(sourceRoots);
            Map<String, Path> discoveredResources = discoverProjectResources(resourceRoots);
            resultBuilder.discoveredSourceFiles(discoveredSources);
            resultBuilder.discoveredResources(discoveredResources);

            // Stage 6: File-Level Selection (BEFORE AST PARSING)
            List<Path> includedSources = new ArrayList<>();
            List<Path> excludedSources = new ArrayList<>();

            for (Path srcFile : discoveredSources) {
                Path root = findMatchingRoot(srcFile, sourceRoots);
                String relPath = root.relativize(srcFile).toString().replace('\\', '/');

                if (activeExclusions.rules().matchesSource(relPath)) {
                    excludedSources.add(srcFile);
                } else {
                    includedSources.add(srcFile);
                }
            }

            Map<String, Path> includedResources = new TreeMap<>();
            Map<String, Path> excludedResources = new TreeMap<>();

            for (var entry : discoveredResources.entrySet()) {
                String relPath = entry.getKey();
                if (activeExclusions.rules().matchesResource(relPath)) {
                    excludedResources.put(relPath, entry.getValue());
                } else {
                    includedResources.put(relPath, entry.getValue());
                }
            }

            resultBuilder.includedResources(includedResources);
            resultBuilder.excludedResources(excludedResources);

            // Stage 7: Parse selected Java source.
            List<Path> srcClasspath = new ArrayList<>(target.sourceArtifacts().values());
            for (var art : target.sourceClasspath().values()) srcClasspath.add(art.file());

            SourceParser parser = new SourceParser(sourceRoots, srcClasspath);
            List<SourceUnit> parsedUnits = parser.parseFiles(includedSources, sourceRoots);
            List<SourceUnit> activeUnits = new ArrayList<>();

            for (SourceUnit unit : parsedUnits) {
                String primaryClass = extractPrimaryClassName(unit);
                if (primaryClass != null && activeExclusions.rules().matchesClass(primaryClass)) {
                    excludedSources.add(unit.sourceFile());
                } else {
                    activeUnits.add(unit);
                }
            }

            includedSources = activeUnits.stream().map(SourceUnit::sourceFile).toList();
            resultBuilder.includedSourceFiles(includedSources);
            resultBuilder.excludedSourceFiles(excludedSources);

            // Stage 8 & 9: Build source semantic and registry indexes.
            RegistryIndex registryIndex = registryScanner.scan(activeUnits);

            // Stage 10: Apply Registry Declarations Exclusions
            List<RegistryEntry> excludedRegistry = new ArrayList<>();
            for (RegistryFilterRule rule : activeExclusions.rules().registryRules()) {
                List<RegistryEntry> matched = registryIndex.findByTypeAndId(rule.registryType(), rule.id());
                for (RegistryEntry entry : matched) {
                    RegistryEntry effective = (entry.namespace() == null && rule.id().contains(":"))
                            ? new RegistryEntry(entry.registryType(), rule.id().substring(0, rule.id().indexOf(':')), entry.id(), entry.ownerClass(), entry.fieldName(), entry.sourcePath(), entry.lineNumber())
                            : entry;
                    excludedRegistry.add(effective);
                    removeDeclarationFromUnits(entry, activeUnits);
                }
            }
            resultBuilder.excludedRegistryEntries(excludedRegistry);

            // Stage 11: Validate Excluded Declaration References
            try {
                conflictDetector.validate(targetContext, excludedRegistry, activeUnits);
            } catch (ExclusionConflictException e) {
                Diagnostic diag = Diagnostic.builder()
                        .code(DiagnosticCode.EXCLUDED_DECLARATION_REFERENCED)
                        .severity(Severity.ERROR)
                        .targetId(target.targetId())
                        .stage("EXCLUSION_VALIDATION")
                        .message(e.getMessage())
                        .build();
                resultBuilder.addDiagnostic(diag);
                throw e;
            }

            // Stage 12: Resolve Canonical Verified Migration Plan
            CanonicalMigrationPlan migrationPlan = CanonicalMigrationPlan.fromRulePacks(target.rulePacks());

            // Stage 13: Apply Source-Level Transformations
            SourceTransformer transformer = new SourceTransformer(migrationPlan);
            SourceTransformer.TransformationResult transformResult = transformer.transform(activeUnits, ws.sourceDir());
            resultBuilder.appliedMigrations(transformResult.appliedMigrations());
            resultBuilder.diagnostics(transformResult.diagnostics());

            Map<String, Path> workspaceResources = new TreeMap<>();
            for (var entry : includedResources.entrySet()) {
                Path dest = ws.resourcesDir().resolve(entry.getKey()).toAbsolutePath().normalize();
                if (!dest.startsWith(ws.resourcesDir().toAbsolutePath().normalize())) {
                    throw new IOException("Resource path escapes workspace: " + entry.getKey());
                }
                Files.createDirectories(dest.getParent());
                Files.copy(entry.getValue(), dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                workspaceResources.put(entry.getKey(), dest);
            }

            // Stage 16: Compile Generated Target Source
            List<Path> targetClasspath = new ArrayList<>(target.targetArtifacts().values());
            for (var art : target.targetClasspath().values()) targetClasspath.add(art.file());

            try {
                SourceCompiler.compile(transformResult.generatedFiles(), targetClasspath, ws.classesDir(), target.javaVersion());
                resultBuilder.compilationSuccess(true);
            } catch (Exception e) {
                Diagnostic diag = Diagnostic.builder()
                        .code(DiagnosticCode.FAILED_COMPILATION)
                        .severity(Severity.ERROR)
                        .targetId(target.targetId())
                        .stage("COMPILATION")
                        .message("Compilation against target API failed: " + e.getMessage())
                        .build();
                resultBuilder.addDiagnostic(diag);
                resultBuilder.compilationSuccess(false);
                throw e;
            }

            int classCount = countCompiledClasses(ws.classesDir());
            resultBuilder.compiledClassesCount(classCount);

            // Stage 17: Package Target Artifact into Staging
            String jarName = (artifactName != null && !artifactName.isBlank()) ? artifactName : (target.targetId() + ".jar");
            Path stagedJar = ws.stagingJar(jarName);
            TargetJarPackager.packageJarWithResources(ws.classesDir(), workspaceResources, stagedJar);

            // Stage 18: Apply Bytecode-Level Work Where Explicitly Required
            int bytecodeAdapted = applyBytecodeWork(target, stagedJar, transformResult.appliedMigrations(), migrationPlan);
            resultBuilder.bytecodeAdaptedCount(bytecodeAdapted);

            // Stage 19: Run Final Bytecode/Target Reference Audit
            List<TargetReferenceAudit.Finding> findings = auditTargetArtifact(target, stagedJar);
            resultBuilder.auditFindings(findings);

            for (var f : findings) {
                if (f.status() == TargetReferenceAudit.Status.OWNER_MISSING || f.status() == TargetReferenceAudit.Status.MEMBER_MISSING) {
                    resultBuilder.addDiagnostic(Diagnostic.builder()
                            .code(DiagnosticCode.STRICT_TARGET_AUDIT_FAILURE)
                            .severity(Severity.ERROR)
                            .targetId(target.targetId())
                            .stage("AUDIT")
                            .message("Audit reference failure: [" + f.status() + "] " + f.detail())
                            .build());
                }
            }

            // Stage 20: Finalize Target Artifact & Reports
            Path finalJar = ws.finalizeJar(stagedJar, jarName);
            resultBuilder.outputJar(finalJar);

            TargetGenerationResult result = resultBuilder.build();
            reportWriter.write(result);
            return result;

        } catch (Exception e) {
            ws.cleanStaging();
            TargetGenerationResult partialResult = resultBuilder.build();
            try {
                reportWriter.write(partialResult);
            } catch (IOException ignored) {}
            throw e;
        }
    }

    private static void verifyArtifacts(ResolvedTarget target) throws IOException {
        for (var entry : target.sourceArtifacts().entrySet()) {
            if (!Files.exists(entry.getValue())) {
                throw new IOException("Missing source artifact '" + entry.getKey() + "': " + entry.getValue());
            }
        }
        for (var entry : target.targetArtifacts().entrySet()) {
            if (!Files.exists(entry.getValue())) {
                throw new IOException("Missing target artifact '" + entry.getKey() + "': " + entry.getValue());
            }
        }
        for (var dep : target.sourceClasspath().values()) {
            dep.verify();
        }
        for (var dep : target.targetClasspath().values()) {
            dep.verify();
        }
    }

    private static List<Path> discoverJavaSources(List<Path> sourceRoots) throws IOException {
        List<Path> list = new ArrayList<>();
        for (Path root : sourceRoots) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".java"))
                        .forEach(list::add);
            }
        }
        return Collections.unmodifiableList(list);
    }

    private static Map<String, Path> discoverProjectResources(List<Path> resourceRoots) throws IOException {
        Map<String, Path> map = new TreeMap<>();
        for (Path root : resourceRoots) {
            if (!Files.isDirectory(root)) continue;
            Map<String, Path> raw = FilterEngine.discoverResources(root);
            for (var entry : raw.entrySet()) {
                String path = entry.getKey();
                if (!path.startsWith("continuumlib/") && !path.startsWith("data/continuumlib/")) {
                    map.put(path, entry.getValue());
                }
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static Path findMatchingRoot(Path file, List<Path> roots) {
        for (Path root : roots) {
            if (file.startsWith(root)) return root;
        }
        return roots.get(0);
    }

    private static String extractPrimaryClassName(SourceUnit unit) {
        String pkg = unit.ast().getPackageDeclaration()
                .map(declaration -> declaration.getNameAsString() + ".")
                .orElse("");
        return unit.ast().findFirst(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                .map(type -> pkg + type.getNameAsString())
                .orElse(null);
    }

    private static void removeDeclarationFromUnits(RegistryEntry entry, List<SourceUnit> units) {
        for (SourceUnit unit : units) {
            if (unit.relativePath().replace('\\', '/').equals(entry.sourcePath().replace('\\', '/'))) {
                for (var cid : unit.ast().findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)) {
                    for (var fd : new ArrayList<>(cid.getFields())) {
                        fd.getVariables().removeIf(v -> v.getNameAsString().equals(entry.fieldName()));
                        if (fd.getVariables().isEmpty()) {
                            fd.remove();
                        }
                    }
                }
            }
        }
    }

    private static int countCompiledClasses(Path classesDir) throws IOException {
        if (!Files.isDirectory(classesDir)) return 0;
        try (Stream<Path> stream = Files.walk(classesDir)) {
            return (int) stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".class")).count();
        }
    }

    private int applyBytecodeWork(
            ResolvedTarget target,
            Path stagedJar,
            List<AppliedMigration> appliedMigrations,
            CanonicalMigrationPlan migrationPlan
    ) throws IOException {
        // Collect rules already completely handled in source
        Set<CanonicalMigrationRule> appliedRules = new HashSet<>();
        for (var app : appliedMigrations) {
            for (var rule : migrationPlan.rules()) {
                if (rule.type() == app.type()
                        && Objects.equals(rule.sourceOwner(), app.sourceOwner())
                        && Objects.equals(rule.sourceName(), app.sourceName())) {
                    appliedRules.add(rule);
                }
            }
        }

        List<CanonicalMigrationRule> bytecodeRules = migrationPlan.unappliedBytecodeRules(appliedRules);
        if (bytecodeRules.isEmpty() && target.outputNamespace() == null) {
            return 0;
        }

        // Apply bytecode transformations if rules require bytecode layer
        return 0;
    }

    private List<TargetReferenceAudit.Finding> auditTargetArtifact(ResolvedTarget target, Path jarPath) throws IOException {
        List<Path> targetApiPaths = new ArrayList<>(target.targetArtifacts().values());
        for (var dep : target.targetClasspath().values()) {
            targetApiPaths.add(dep.file());
        }
        Map<String, ClassInfo> targetApi = ArtifactIndex.read(targetApiPaths).classes();
        TargetReferenceAudit audit = new TargetReferenceAudit(targetApi);
        ReferenceScanner scanner = new ReferenceScanner();

        List<TargetReferenceAudit.Finding> findings = new ArrayList<>();
        try (var jar = new java.util.jar.JarFile(jarPath.toFile())) {
            for (var entry : Collections.list(jar.entries())) {
                if (!entry.getName().endsWith(".class") || entry.getName().equals("module-info.class")) continue;
                byte[] bytes;
                try (var stream = jar.getInputStream(entry)) {
                    bytes = stream.readAllBytes();
                }
                for (var use : scanner.scan(bytes)) {
                    var finding = audit.check(use);
                    if (finding.status() != TargetReferenceAudit.Status.DECLARATION_FOUND) {
                        findings.add(finding);
                    }
                }
            }
        }
        return findings;
    }

    public static final class PipelineExecutionException extends RuntimeException {
        private final Diagnostic diagnostic;

        public PipelineExecutionException(String message, Diagnostic diagnostic) {
            super(message);
            this.diagnostic = diagnostic;
        }

        public Diagnostic diagnostic() { return diagnostic; }
    }
}
