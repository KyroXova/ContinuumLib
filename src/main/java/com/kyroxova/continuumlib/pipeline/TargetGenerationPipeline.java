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
import com.kyroxova.continuumlib.pipeline.config.ProjectConfigurationLocator;
import com.kyroxova.continuumlib.pipeline.migration.*;
import com.kyroxova.continuumlib.pipeline.report.GenerationReportWriter;
import com.kyroxova.continuumlib.source.ast.SourceParser;
import com.kyroxova.continuumlib.source.ast.SourceUnit;
import com.kyroxova.continuumlib.source.compile.SourceCompiler;
import com.kyroxova.continuumlib.source.compile.TargetJarPackager;
import com.kyroxova.continuumlib.source.transform.SourceTransformer;
import com.kyroxova.continuumlib.resolver.ClassAdaptationPlan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public final class TargetGenerationPipeline {
    private final FilterEngine filterEngine = new FilterEngine();
    private final GenerationReportWriter reportWriter = new GenerationReportWriter();
    private final com.kyroxova.continuumlib.source.compile.SourceCompilationStrategy sourceCompiler;

    public TargetGenerationPipeline() {
        this(SourceCompiler::compile);
    }

    public TargetGenerationPipeline(
            com.kyroxova.continuumlib.source.compile.SourceCompilationStrategy sourceCompiler
    ) {
        this.sourceCompiler = Objects.requireNonNull(sourceCompiler, "sourceCompiler");
    }

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
        validateWorkspaceIsolation(target, sourceRoots, resourceRoots);
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
                var discoveredConfig = new ProjectConfigurationLocator().locate(projectRoot);
                projConfig = new FilterConfigurationReader().load(discoveredConfig.root());
            }

            InclusionRuleSet activeInclusions = projConfig.inclusions() != null
                    ? projConfig.inclusions().filterFor(targetContext)
                    : InclusionRuleSet.EMPTY;
            ExclusionRuleSet activeExclusions = projConfig.exclusions() != null
                    ? projConfig.exclusions().filterFor(targetContext)
                    : ExclusionRuleSet.EMPTY;

            // Stage 5: Discover Source Files and Resources
            List<Path> discoveredSources = discoverJavaSources(sourceRoots);
            validateUniqueSourcePaths(discoveredSources, sourceRoots);
            Map<String, Path> discoveredResources = discoverProjectResources(resourceRoots);
            resultBuilder.discoveredSourceFiles(discoveredSources);
            resultBuilder.discoveredResources(discoveredResources);

            // Stage 6: File-Level Selection (BEFORE AST PARSING)
            List<Path> includedSources = new ArrayList<>();
            List<Path> excludedSources = new ArrayList<>();

            for (Path srcFile : discoveredSources) {
                Path root = findMatchingRoot(srcFile, sourceRoots);
                String relPath = root.relativize(srcFile).toString().replace('\\', '/');

                boolean included = !activeInclusions.rules().hasSourceRules()
                        || activeInclusions.rules().matchesSource(relPath);
                boolean excluded = activeExclusions.rules().matchesSource(relPath);
                if (included && !excluded) {
                    includedSources.add(srcFile);
                } else {
                    excludedSources.add(srcFile);
                }
            }

            Map<String, Path> includedResources = new TreeMap<>();
            Map<String, Path> excludedResources = new TreeMap<>();

            for (var entry : discoveredResources.entrySet()) {
                String relPath = entry.getKey();
                boolean included = !activeInclusions.rules().hasResourceRules()
                        || activeInclusions.rules().matchesResource(relPath);
                boolean excluded = activeExclusions.rules().matchesResource(relPath);
                if (included && !excluded) {
                    includedResources.put(relPath, entry.getValue());
                } else {
                    excludedResources.put(relPath, entry.getValue());
                }
            }

            resultBuilder.includedResources(includedResources);
            resultBuilder.excludedResources(excludedResources);

            // Stage 7-11: Parse selected source, then apply semantic class/registry filtering.
            List<Path> srcClasspath = new ArrayList<>(target.sourceArtifacts().values());
            for (var artifact : target.sourceClasspath().values()) srcClasspath.add(artifact.file());

            SourceParser parser = new SourceParser(sourceRoots, srcClasspath);
            List<SourceUnit> parsedUnits = parser.parseFiles(includedSources, sourceRoots);

            FilterEngine.FilterResult filterResult;
            try {
                filterResult = filterEngine.process(
                        targetContext,
                        activeInclusions,
                        activeExclusions,
                        parsedUnits,
                        includedResources
                );
            } catch (ExclusionConflictException e) {
                Diagnostic diagnostic = Diagnostic.builder()
                        .code(DiagnosticCode.EXCLUDED_DECLARATION_REFERENCED)
                        .severity(Severity.ERROR)
                        .targetId(target.targetId())
                        .stage("EXCLUSION_VALIDATION")
                        .message(e.getMessage())
                        .build();
                resultBuilder.addDiagnostic(diagnostic);
                throw e;
            }

            List<SourceUnit> activeUnits = filterResult.activeSources();
            excludedSources.addAll(filterResult.excludedSources().stream()
                    .map(SourceUnit::sourceFile)
                    .filter(path -> !excludedSources.contains(path))
                    .toList());
            includedSources = activeUnits.stream().map(SourceUnit::sourceFile).toList();

            resultBuilder.includedSourceFiles(includedSources);
            resultBuilder.excludedSourceFiles(excludedSources);
            resultBuilder.excludedRegistryEntries(filterResult.excludedRegistryEntries());

            // Stage 12: Resolve Canonical Verified Migration Plan
            CanonicalMigrationPlan migrationPlan = CanonicalMigrationPlan.fromRulePacks(target.rulePacks());

            // Stage 13: Apply Source-Level Transformations
            List<Path> sourceIndexPaths = srcClasspath.stream()
                    .map(path -> path.toAbsolutePath().normalize())
                    .distinct()
                    .sorted()
                    .toList();
            Map<String, ClassInfo> sourceApi = ArtifactIndex.read(sourceIndexPaths).classes();

            SourceTransformer transformer = new SourceTransformer(migrationPlan, sourceApi);
            SourceTransformer.TransformationResult transformResult = transformer.transform(activeUnits, ws.sourceDir());
            resultBuilder.appliedMigrations(transformResult.appliedMigrations());
            resultBuilder.diagnostics(transformResult.diagnostics());

            Optional<Diagnostic> transformFailure = transformResult.diagnostics().stream()
                    .filter(diagnostic -> diagnostic.severity() == Severity.ERROR)
                    .findFirst();
            if (transformFailure.isPresent()) {
                throw new PipelineExecutionException("Source transformation produced fatal diagnostics", transformFailure.get());
            }

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
                sourceCompiler.compile(transformResult.generatedFiles(), targetClasspath, ws.classesDir(), target.javaVersion());
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

            // Stage 18: Apply bytecode-only migrations that source transformation did not consume.
            var bytecodeResult = new BytecodeMigrationExecutor().apply(
                    stagedJar,
                    migrationPlan,
                    transformResult.appliedMigrations()
            );
            resultBuilder.appliedMigrations(bytecodeResult.appliedMigrations());

            int namespaceAdapted;
            try {
                namespaceAdapted = new NamespaceExporter().export(stagedJar, target).adaptedClasses();
            } catch (IOException | IllegalArgumentException e) {
                Diagnostic diagnostic = Diagnostic.builder()
                        .code(DiagnosticCode.INVALID_MAPPING_SETUP)
                        .severity(Severity.ERROR)
                        .targetId(target.targetId())
                        .stage("NAMESPACE_EXPORT")
                        .message("Namespace export failed: " + e.getMessage())
                        .build();
                resultBuilder.addDiagnostic(diagnostic);
                throw new PipelineExecutionException("Namespace export failed", diagnostic);
            }
            resultBuilder.bytecodeAdaptedCount(bytecodeResult.adaptedClasses() + namespaceAdapted);

            // Stage 19: Run Final Bytecode/Target Reference Audit
            List<TargetReferenceAudit.Finding> findings = auditTargetArtifact(target, stagedJar);
            resultBuilder.auditFindings(findings);

            Diagnostic firstAuditFailure = null;
            for (var finding : findings) {
                Severity severity = fatalAuditStatus(finding.status()) ? Severity.ERROR : Severity.WARNING;
                Diagnostic diagnostic = Diagnostic.builder()
                        .code(severity == Severity.ERROR
                                ? DiagnosticCode.STRICT_TARGET_AUDIT_FAILURE
                                : DiagnosticCode.TARGET_AUDIT_REVIEW)
                        .severity(severity)
                        .targetId(target.targetId())
                        .stage("AUDIT")
                        .message("Audit reference result: [" + finding.status() + "] " + finding.detail())
                        .build();
                resultBuilder.addDiagnostic(diagnostic);
                if (severity == Severity.ERROR && firstAuditFailure == null) {
                    firstAuditFailure = diagnostic;
                }
            }
            if (firstAuditFailure != null) {
                throw new PipelineExecutionException("Target reference audit failed", firstAuditFailure);
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

    private static void validateWorkspaceIsolation(
            ResolvedTarget target,
            List<Path> sourceRoots,
            List<Path> resourceRoots
    ) throws IOException {
        Path workspace = target.workspace().rootDir().toAbsolutePath().normalize();
        List<Path> inputs = new ArrayList<>();
        if (sourceRoots != null) inputs.addAll(sourceRoots);
        if (resourceRoots != null) inputs.addAll(resourceRoots);
        inputs.addAll(target.sourceArtifacts().values());
        inputs.addAll(target.targetArtifacts().values());
        target.sourceClasspath().values().forEach(artifact -> inputs.add(artifact.file()));
        target.targetClasspath().values().forEach(artifact -> inputs.add(artifact.file()));
        if (target.sourceMapping() != null) inputs.add(target.sourceMapping().file());
        if (target.targetMapping() != null) inputs.add(target.targetMapping().file());
        if (target.projectConfiguration() != null && target.projectConfiguration().configurationRoot() != null) {
            inputs.add(target.projectConfiguration().configurationRoot());
        }

        for (Path input : inputs) {
            if (input == null) continue;
            Path normalizedInput = input.toAbsolutePath().normalize();
            if (pathsOverlap(workspace, normalizedInput)) {
                throw new IOException("Generated workspace must not overlap consumer input path: "
                        + workspace + " vs " + normalizedInput);
            }
            if (Files.exists(workspace) && Files.exists(normalizedInput)) {
                Path realWorkspace = workspace.toRealPath();
                Path realInput = normalizedInput.toRealPath();
                if (pathsOverlap(realWorkspace, realInput)) {
                    throw new IOException("Generated workspace must not overlap consumer input path through symbolic links: "
                            + workspace + " vs " + normalizedInput);
                }
            }
        }
    }

    private static boolean pathsOverlap(Path left, Path right) {
        return left.equals(right) || left.startsWith(right) || right.startsWith(left);
    }

    private static void verifyArtifacts(ResolvedTarget target) throws IOException {
        for (var entry : target.sourceArtifacts().entrySet()) {
            if (!Files.isRegularFile(entry.getValue())) {
                throw new IOException("Missing source artifact '" + entry.getKey() + "': " + entry.getValue());
            }
        }
        for (var entry : target.targetArtifacts().entrySet()) {
            if (!Files.isRegularFile(entry.getValue())) {
                throw new IOException("Missing target artifact '" + entry.getKey() + "': " + entry.getValue());
            }
        }
        for (var dependency : target.sourceClasspath().values()) {
            dependency.verify();
        }
        for (var dependency : target.targetClasspath().values()) {
            dependency.verify();
        }

        if (!target.rulePacks().isEmpty()) {
            new ClassAdaptationPlan(
                    target.sourceEnvironment(),
                    target.targetEnvironment(),
                    target.rulePacks()
            ).bind(target.sourceArtifacts(), target.targetArtifacts());
        }
    }

    private static List<Path> discoverJavaSources(List<Path> sourceRoots) throws IOException {
        Set<Path> files = new TreeSet<>(Comparator.comparing(Path::toString));
        for (Path root : sourceRoots) {
            Path normalizedRoot = root.toAbsolutePath().normalize();
            if (!Files.isDirectory(normalizedRoot)) continue;
            try (Stream<Path> stream = Files.walk(normalizedRoot)) {
                stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".java"))
                        .map(path -> path.toAbsolutePath().normalize())
                        .forEach(files::add);
            }
        }
        return List.copyOf(files);
    }

    private static Map<String, Path> discoverProjectResources(List<Path> resourceRoots) throws IOException {
        Map<String, Path> map = new TreeMap<>();
        for (Path root : resourceRoots) {
            Path normalizedRoot = root.toAbsolutePath().normalize();
            if (!Files.isDirectory(normalizedRoot)) continue;
            Map<String, Path> raw = FilterEngine.discoverResources(normalizedRoot);
            for (var entry : raw.entrySet()) {
                String path = entry.getKey();
                if (path.startsWith("continuumlib/") || path.startsWith("data/continuumlib/")) {
                    continue;
                }
                Path value = entry.getValue().toAbsolutePath().normalize();
                Path previous = map.putIfAbsent(path, value);
                if (previous != null && !previous.equals(value)) {
                    throw new IOException("Duplicate resource path across roots: " + path
                            + " -> " + previous + " and " + value);
                }
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static void validateUniqueSourcePaths(List<Path> sourceFiles, List<Path> sourceRoots) throws IOException {
        Map<String, Path> logicalPaths = new TreeMap<>();
        for (Path file : sourceFiles) {
            Path root = findMatchingRoot(file, sourceRoots);
            String relative = root.relativize(file).toString().replace('\\', '/');
            Path normalized = file.toAbsolutePath().normalize();
            Path previous = logicalPaths.putIfAbsent(relative, normalized);
            if (previous != null && !previous.equals(normalized)) {
                throw new IOException("Duplicate source path across roots: " + relative
                        + " -> " + previous + " and " + normalized);
            }
        }
    }

    private static Path findMatchingRoot(Path file, List<Path> roots) {
        Path normalizedFile = file.toAbsolutePath().normalize();
        return roots.stream()
                .map(root -> root.toAbsolutePath().normalize())
                .filter(normalizedFile::startsWith)
                .max(Comparator.comparingInt(Path::getNameCount))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Source file is outside configured source roots: " + normalizedFile));
    }

    private static int countCompiledClasses(Path classesDir) throws IOException {
        if (!Files.isDirectory(classesDir)) return 0;
        try (Stream<Path> stream = Files.walk(classesDir)) {
            return (int) stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".class")).count();
        }
    }

    private static boolean fatalAuditStatus(TargetReferenceAudit.Status status) {
        return switch (status) {
            case OWNER_MISSING, MEMBER_MISSING, STATIC_MISMATCH, OWNER_KIND_MISMATCH,
                    ACCESS_DENIED, FINAL_WRITE_ILLEGAL -> true;
            case DECLARATION_FOUND, HIERARCHY_INCOMPLETE, INHERITANCE_REQUIRES_REVIEW,
                    ACCESS_REQUIRES_REVIEW -> false;
        };
    }

    private List<TargetReferenceAudit.Finding> auditTargetArtifact(ResolvedTarget target, Path jarPath) throws IOException {
        Map<String, ClassInfo> targetApi = new TargetApiResolver().forNamespace(
                target,
                target.mappingNamespace()
        );
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
                    if (isPlatformOwner(use.target().owner())) {
                        continue;
                    }
                    var finding = audit.check(use);
                    if (finding.status() != TargetReferenceAudit.Status.DECLARATION_FOUND) {
                        findings.add(finding);
                    }
                }
            }
        }
        return findings;
    }

    private static boolean isPlatformOwner(String owner) {
        return owner.startsWith("java/")
                || owner.startsWith("javax/")
                || owner.startsWith("jdk/")
                || owner.startsWith("sun/")
                || owner.startsWith("com/sun/")
                || owner.startsWith("org/w3c/dom/")
                || owner.startsWith("org/xml/sax/");
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
