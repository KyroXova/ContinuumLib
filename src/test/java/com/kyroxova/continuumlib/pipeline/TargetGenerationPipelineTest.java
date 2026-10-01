package com.kyroxova.continuumlib.pipeline;

import com.github.javaparser.ast.CompilationUnit;
import com.kyroxova.continuumlib.bytecode.MemberReference;
import com.kyroxova.continuumlib.filter.config.ContinuumProjectConfiguration;
import com.kyroxova.continuumlib.filter.domain.RegistryType;
import com.kyroxova.continuumlib.filter.rule.ExclusionRuleSet;
import com.kyroxova.continuumlib.filter.rule.RegistryFilterRule;
import com.kyroxova.continuumlib.filter.rule.RuleSet;
import com.kyroxova.continuumlib.filter.rule.SourceFilterRule;
import com.kyroxova.continuumlib.knowledge.rule.RulePack;
import com.kyroxova.continuumlib.model.diagnostic.Diagnostic;
import com.kyroxova.continuumlib.model.diagnostic.DiagnosticCode;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.Loader;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;
import com.kyroxova.continuumlib.pipeline.config.ProjectConfigurationLocator;
import com.kyroxova.continuumlib.pipeline.migration.*;
import com.kyroxova.continuumlib.source.ast.SourceParser;
import com.kyroxova.continuumlib.source.ast.SourceUnit;
import com.kyroxova.continuumlib.source.transform.SourceTransformer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class TargetGenerationPipelineTest {

    @Test
    void testPipelineEndToEndExecution(@TempDir Path tempDir) throws Exception {
        // 1. Create synthetic API jars
        Path srcApiJar = tempDir.resolve("src-api.jar");
        createApiJar(srcApiJar, "net/minecraft/resources/ResourceLocation", false);

        Path tgtApiJar = tempDir.resolve("tgt-api.jar");
        createApiJar(tgtApiJar, "net/minecraft/resources/ResourceLocation", true);

        String srcDigest = digest(srcApiJar);
        String tgtDigest = digest(tgtApiJar);

        // 2. Setup project structure
        Path projectRoot = tempDir.resolve("mod-project");
        Path srcDir = projectRoot.resolve("src/main/java");
        Path resDir = projectRoot.resolve("src/main/resources");
        Path configDir = projectRoot.resolve("src/main/resources/continuumlib");
        Files.createDirectories(srcDir.resolve("com/example"));
        Files.createDirectories(resDir.resolve("assets/mymod"));
        Files.createDirectories(configDir.resolve("exclusions"));

        // Source file 1: active mod file using ResourceLocation
        Path activeJava = srcDir.resolve("com/example/MyMod.java");
        Files.writeString(activeJava, """
                package com.example;
                
                import net.minecraft.resources.ResourceLocation;
                
                public class MyMod {
                    public void init() {
                        ResourceLocation loc = new ResourceLocation("mymod", "test_item");
                    }
                }
                """);

        // Source file 2: file to be excluded by source filter
        Path excludedJava = srcDir.resolve("com/example/ExcludedHelper.java");
        Files.writeString(excludedJava, """
                package com.example;
                
                public class ExcludedHelper {
                    public static void unused() {}
                }
                """);

        // Resources
        Path activeResource = resDir.resolve("assets/mymod/active.json");
        Files.writeString(activeResource, "{\"status\": \"active\"}");
        Path excludedResource = resDir.resolve("assets/mymod/excluded.json");
        Files.writeString(excludedResource, "{\"status\": \"excluded\"}");

        // Project exclusion rules (excluding ExcludedHelper.java and excluded.json)
        Files.writeString(configDir.resolve("exclusions/rules.json"), """
                {
                    "rules": [
                        {"domain": "source", "path": "com/example/ExcludedHelper.java"},
                        {"domain": "resource", "path": "assets/mymod/excluded.json"}
                    ]
                }
                """);

        // 3. Define RulePack
        EnvironmentId srcEnv = new EnvironmentId("1.20.1", Loader.FABRIC, MappingNamespace.OFFICIAL, 17);
        EnvironmentId tgtEnv = new EnvironmentId("1.21.1", Loader.FABRIC, MappingNamespace.OFFICIAL, 17);

        CanonicalMigrationRule c2f = CanonicalMigrationRule.builder()
                .rulePackId("test-rl-pack")
                .type(MigrationType.CONSTRUCTOR_TO_FACTORY)
                .sourceOwner("net/minecraft/resources/ResourceLocation")
                .sourceDescriptor("(Ljava/lang/String;Ljava/lang/String;)V")
                .targetOwner("net/minecraft/resources/ResourceLocation")
                .targetName("fromNamespaceAndPath")
                .targetDescriptor("(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;")
                .sourceEnvironment(srcEnv)
                .targetEnvironment(tgtEnv)
                .build();

        RulePack rulePack = new RulePack(
                "test-rl-pack",
                "Official Mojang mappings diff",
                srcEnv,
                tgtEnv,
                Map.of("game", srcDigest),
                Map.of("game", tgtDigest),
                Map.of(),
                Map.of(),
                List.of(),
                List.of(new com.kyroxova.continuumlib.bytecode.ConstructorFactory.Rule(
                        new MemberReference("net/minecraft/resources/ResourceLocation", "<init>", "(Ljava/lang/String;Ljava/lang/String;)V"),
                        new MemberReference("net/minecraft/resources/ResourceLocation", "fromNamespaceAndPath", "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;")
                ))
        );

        // 4. Build ResolvedTarget
        Path buildRoot = projectRoot.resolve("build");
        GeneratedWorkspace workspace = new GeneratedWorkspace(buildRoot, "1.21.1-fabric");

        ResolvedTarget target = ResolvedTarget.builder()
                .targetId("1.21.1-fabric")
                .sourceEnvironment(srcEnv)
                .targetEnvironment(tgtEnv)
                .sourceArtifacts(Map.of("game", srcApiJar))
                .targetArtifacts(Map.of("game", tgtApiJar))
                .addRulePack(rulePack)
                .workspace(workspace)
                .build();

        // 5. Execute Pipeline
        TargetGenerationPipeline pipeline = new TargetGenerationPipeline();
        TargetGenerationResult result = pipeline.execute(
                target,
                projectRoot,
                List.of(srcDir),
                List.of(resDir),
                "mymod-1.21.1-fabric.jar"
        );

        // 6. Verify Result
        assertTrue(result.isSuccess(), "Pipeline should succeed");
        assertTrue(Files.exists(result.outputJar()), "Final output jar must exist");
        assertFalse(Files.exists(workspace.stagingDir().resolve("mymod-1.21.1-fabric.jar")), "Staging jar should be cleaned or moved");

        // Verify source file filtering: only MyMod.java was included, ExcludedHelper.java was excluded
        assertEquals(2, result.discoveredSourceFiles().size());
        assertEquals(1, result.includedSourceFiles().size());
        assertEquals(1, result.excludedSourceFiles().size());
        assertTrue(result.excludedSourceFiles().get(0).toString().contains("ExcludedHelper.java"));

        // Verify resource filtering
        assertEquals(1, result.includedResources().size());
        assertEquals(1, result.excludedResources().size());
        assertTrue(result.excludedResources().containsKey("assets/mymod/excluded.json"));

        // Verify AST transformation was applied
        assertEquals(1, result.appliedMigrations().size());
        AppliedMigration migration = result.appliedMigrations().get(0);
        assertEquals(MigrationType.CONSTRUCTOR_TO_FACTORY, migration.type());
        assertEquals("fromNamespaceAndPath", migration.targetName());

        // Verify workspace directory structure
        assertTrue(Files.isDirectory(workspace.sourceDir()));
        assertTrue(Files.isDirectory(workspace.classesDir()));
        assertTrue(Files.isDirectory(workspace.reportsDir()));
        assertTrue(Files.isDirectory(workspace.outputDir()));

        // Verify report generated
        Path reportFile = workspace.reportsDir().resolve("generation.txt");
        assertTrue(Files.exists(reportFile));
        String reportText = Files.readString(reportFile);
        assertTrue(reportText.contains("Target ID: 1.21.1-fabric"));
        assertTrue(reportText.contains("FINAL STATUS: PASSED"));
        assertTrue(reportText.contains("fromNamespaceAndPath"));
    }

    @Test
    void testOverloadDisambiguationByDescriptor() {
        // Setup overloads with same name but different descriptors
        CanonicalMigrationRule r1 = CanonicalMigrationRule.builder()
                .type(MigrationType.MEMBER_RENAME)
                .sourceOwner("com.example.Helper")
                .sourceName("process")
                .sourceDescriptor("(Ljava/lang/String;)V")
                .targetOwner("com.example.Helper")
                .targetName("processString")
                .targetDescriptor("(Ljava/lang/String;)V")
                .build();

        CanonicalMigrationRule r2 = CanonicalMigrationRule.builder()
                .type(MigrationType.MEMBER_RENAME)
                .sourceOwner("com.example.Helper")
                .sourceName("process")
                .sourceDescriptor("(I)V")
                .targetOwner("com.example.Helper")
                .targetName("processInt")
                .targetDescriptor("(I)V")
                .build();

        CanonicalMigrationRule r3 = CanonicalMigrationRule.builder()
                .type(MigrationType.MEMBER_RENAME)
                .sourceOwner("com.example.Helper")
                .sourceName("process")
                .sourceDescriptor("(Ljava/lang/String;Ljava/lang/String;)V")
                .targetOwner("com.example.Helper")
                .targetName("processPair")
                .targetDescriptor("(Ljava/lang/String;Ljava/lang/String;)V")
                .build();

        CanonicalMigrationPlan plan = new CanonicalMigrationPlan(List.of(r1, r2, r3));
        SourceTransformer transformer = new SourceTransformer(plan);

        String code = """
                package com.test;
                import com.example.Helper;
                public class TestOverloads {
                    public void test() {
                        Helper.process("hello");
                        Helper.process(42);
                        Helper.process("a", "b");
                    }
                }
                """;

        SourceParser parser = new SourceParser(List.of(), List.of());
        SourceUnit unit = parser.parseString("com/test/TestOverloads.java", code);

        List<AppliedMigration> applied = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        transformer.transformAstWithAccounting(unit.ast(), unit.relativePath(), applied, diagnostics);

        String result = unit.ast().toString();
        assertTrue(result.contains("Helper.processString(\"hello\");"));
        assertTrue(result.contains("Helper.processInt(42);"));
        assertTrue(result.contains("Helper.processPair(\"a\", \"b\");"));

        assertEquals(3, applied.size());
        assertEquals("processString", applied.get(0).targetName());
        assertEquals("processInt", applied.get(1).targetName());
        assertEquals("processPair", applied.get(2).targetName());
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void testAmbiguousOverloadEmitsDiagnosticAndDoesNotGuess() {
        // Two rules with same owner, same name, same parameter count, neither distinguishable by literal types
        CanonicalMigrationRule r1 = CanonicalMigrationRule.builder()
                .type(MigrationType.MEMBER_RENAME)
                .sourceOwner("com.example.Service")
                .sourceName("dispatch")
                .sourceDescriptor("(Lcom/example/Alpha;)V")
                .targetOwner("com.example.Service")
                .targetName("dispatchAlpha")
                .build();

        CanonicalMigrationRule r2 = CanonicalMigrationRule.builder()
                .type(MigrationType.MEMBER_RENAME)
                .sourceOwner("com.example.Service")
                .sourceName("dispatch")
                .sourceDescriptor("(Lcom/example/Beta;)V")
                .targetOwner("com.example.Service")
                .targetName("dispatchBeta")
                .build();

        CanonicalMigrationPlan plan = new CanonicalMigrationPlan(List.of(r1, r2));
        SourceTransformer transformer = new SourceTransformer(plan);

        String code = """
                package com.test;
                import com.example.Service;
                public class TestAmbiguity {
                    public void test(Object param) {
                        Service.dispatch(param);
                    }
                }
                """;

        SourceParser parser = new SourceParser(List.of(), List.of());
        SourceUnit unit = parser.parseString("com/test/TestAmbiguity.java", code);

        List<AppliedMigration> applied = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        transformer.transformAstWithAccounting(unit.ast(), unit.relativePath(), applied, diagnostics);

        // Verification: MUST NOT GUESS or transform
        String result = unit.ast().toString();
        assertTrue(result.contains("Service.dispatch(param);"));
        assertFalse(result.contains("dispatchAlpha"));
        assertFalse(result.contains("dispatchBeta"));

        // Diagnostic emitted
        assertEquals(1, diagnostics.size());
        assertEquals(DiagnosticCode.AMBIGUOUS_MIGRATION, diagnostics.get(0).code());
        assertTrue(diagnostics.get(0).message().contains("Ambiguous method invocation"));
    }

    @Test
    void testUnifiedConfigurationDiscoveryAndDualConflict(@TempDir Path tempDir) throws IOException {
        ProjectConfigurationLocator locator = new ProjectConfigurationLocator();

        // 1. Only Canonical
        Path canonicalDir = tempDir.resolve("canonical-mod");
        Files.createDirectories(canonicalDir.resolve("src/main/resources/continuumlib/inclusions"));
        Files.writeString(canonicalDir.resolve("src/main/resources/continuumlib/targets.properties"), "targets=test\nperVersion=true\n");
        var cfg1 = locator.locate(canonicalDir);
        assertFalse(cfg1.isLegacy());
        assertTrue(cfg1.exists());

        // 2. Only Legacy
        Path legacyDir = tempDir.resolve("legacy-mod");
        Files.createDirectories(legacyDir.resolve("src/main/resources/data/continuumlib"));
        Files.writeString(legacyDir.resolve("src/main/resources/data/continuumlib/targets.properties"), "targets=test\nperVersion=true\n");
        var cfg2 = locator.locate(legacyDir);
        assertTrue(cfg2.isLegacy());
        assertTrue(cfg2.exists());

        // 3. Dual Configuration Conflict
        Path dualDir = tempDir.resolve("dual-mod");
        Files.createDirectories(dualDir.resolve("src/main/resources/continuumlib"));
        Files.writeString(dualDir.resolve("src/main/resources/continuumlib/targets.properties"), "targets=test1\n");
        Files.createDirectories(dualDir.resolve("src/main/resources/data/continuumlib"));
        Files.writeString(dualDir.resolve("src/main/resources/data/continuumlib/targets.properties"), "targets=test2\n");

        assertThrows(ProjectConfigurationLocator.DualConfigurationException.class, () -> locator.locate(dualDir));
    }

    @Test
    void testTargetWorkspaceIsolation(@TempDir Path tempDir) {
        Path buildRoot = tempDir.resolve("build");
        GeneratedWorkspace wsA = new GeneratedWorkspace(buildRoot, "1.20.1-fabric");
        GeneratedWorkspace wsB = new GeneratedWorkspace(buildRoot, "1.18.2-forge");

        assertNotEquals(wsA.rootDir(), wsB.rootDir());
        assertNotEquals(wsA.sourceDir(), wsB.sourceDir());
        assertNotEquals(wsA.classesDir(), wsB.classesDir());
        assertNotEquals(wsA.reportsDir(), wsB.reportsDir());
        assertNotEquals(wsA.outputDir(), wsB.outputDir());
        assertNotEquals(wsA.stagingDir(), wsB.stagingDir());

        assertTrue(wsA.rootDir().toString().contains("1.20.1-fabric"));
        assertTrue(wsB.rootDir().toString().contains("1.18.2-forge"));

        // Unsafe target IDs must be rejected
        assertThrows(IllegalArgumentException.class, () -> new GeneratedWorkspace(buildRoot, "../escaping"));
        assertThrows(IllegalArgumentException.class, () -> new GeneratedWorkspace(buildRoot, "CON"));
        assertThrows(IllegalArgumentException.class, () -> new GeneratedWorkspace(buildRoot, "PRN"));
        assertThrows(IllegalArgumentException.class, () -> new GeneratedWorkspace(buildRoot, "foo/bar"));
    }

    @Test
    void testRegistryExclusionConflictStopsPipelineAndCleansStaging(@TempDir Path tempDir) throws Exception {
        Path srcApiJar = tempDir.resolve("src-api.jar");
        createApiJar(srcApiJar, "net/minecraft/resources/ResourceLocation", false);
        Path tgtApiJar = tempDir.resolve("tgt-api.jar");
        createApiJar(tgtApiJar, "net/minecraft/resources/ResourceLocation", true);

        Path projectRoot = tempDir.resolve("conflict-project");
        Path srcDir = projectRoot.resolve("src/main/java");
        Path resDir = projectRoot.resolve("src/main/resources");
        Path configDir = projectRoot.resolve("src/main/resources/continuumlib");
        Files.createDirectories(srcDir.resolve("com/example"));
        Files.createDirectories(resDir);
        Files.createDirectories(configDir.resolve("exclusions"));

        // Mod declaring RUBY_BLOCK
        Files.writeString(srcDir.resolve("com/example/ModBlocks.java"), """
                package com.example;
                public class ModBlocks {
                    public static final Object BLOCKS = null;
                    public static final Object RUBY_BLOCK = BLOCKS.register("ruby_block", () -> null);
                }
                """);

        // Another class still referencing RUBY_BLOCK
        Files.writeString(srcDir.resolve("com/example/ModItems.java"), """
                package com.example;
                public class ModItems {
                    public void use() {
                        Object b = ModBlocks.RUBY_BLOCK;
                    }
                }
                """);

        // Exclude RUBY_BLOCK via registry rule
        Files.writeString(configDir.resolve("exclusions/blocks.json"), """
                {
                    "rules": [
                        {"type": "BLOCK", "id": "ruby_block"}
                    ]
                }
                """);

        EnvironmentId srcEnv = new EnvironmentId("1.20.1", Loader.FABRIC, MappingNamespace.OFFICIAL, 17);
        EnvironmentId tgtEnv = new EnvironmentId("1.21.1", Loader.FABRIC, MappingNamespace.OFFICIAL, 17);

        Path buildRoot = projectRoot.resolve("build");
        GeneratedWorkspace workspace = new GeneratedWorkspace(buildRoot, "test-target");

        ResolvedTarget target = ResolvedTarget.builder()
                .targetId("test-target")
                .sourceEnvironment(srcEnv)
                .targetEnvironment(tgtEnv)
                .sourceArtifacts(Map.of("game", srcApiJar))
                .targetArtifacts(Map.of("game", tgtApiJar))
                .workspace(workspace)
                .build();

        TargetGenerationPipeline pipeline = new TargetGenerationPipeline();
        assertThrows(Exception.class, () -> pipeline.execute(
                target,
                projectRoot,
                List.of(srcDir),
                List.of(resDir),
                "conflict-target.jar"
        ));

        // Ensure failure cleans staging and does not produce final JAR
        assertFalse(Files.exists(workspace.finalJar("conflict-target.jar")));
        assertFalse(Files.exists(workspace.stagingJar("conflict-target.jar")));
    }

    @Test
    void pipelineFallbackRejectsDualProjectConfiguration(@TempDir Path tempDir) throws Exception {
        Path srcApiJar = tempDir.resolve("src-api.jar");
        createApiJar(srcApiJar, "example/SourceApi", false);
        Path tgtApiJar = tempDir.resolve("tgt-api.jar");
        createApiJar(tgtApiJar, "example/TargetApi", true);

        Path projectRoot = tempDir.resolve("dual-config-project");
        Path srcDir = projectRoot.resolve("src/main/java");
        Path resDir = projectRoot.resolve("src/main/resources");
        Path canonical = resDir.resolve("continuumlib");
        Path legacy = resDir.resolve("data/continuumlib");
        Files.createDirectories(srcDir);
        Files.createDirectories(canonical);
        Files.createDirectories(legacy);
        Files.writeString(canonical.resolve("targets.properties"), "targets=test-target\n");
        Files.writeString(legacy.resolve("transform.properties"), "pack=test\n");

        EnvironmentId source = new EnvironmentId("1.20.1", Loader.FABRIC, MappingNamespace.OFFICIAL, 17);
        EnvironmentId targetEnv = new EnvironmentId("1.21.1", Loader.FABRIC, MappingNamespace.OFFICIAL, 17);
        GeneratedWorkspace workspace = new GeneratedWorkspace(projectRoot.resolve("build"), "test-target");

        ResolvedTarget target = ResolvedTarget.builder()
                .targetId("test-target")
                .sourceEnvironment(source)
                .targetEnvironment(targetEnv)
                .sourceArtifacts(Map.of("game", srcApiJar))
                .targetArtifacts(Map.of("game", tgtApiJar))
                .workspace(workspace)
                .build();

        assertThrows(ProjectConfigurationLocator.DualConfigurationException.class, () ->
                new TargetGenerationPipeline().execute(
                        target,
                        projectRoot,
                        List.of(srcDir),
                        List.of(resDir),
                        "test-target.jar"
                ));
    }

    private static void createApiJar(Path jarPath, String className, boolean isTarget) throws IOException {
        Files.createDirectories(jarPath.getParent());
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);

        if (!isTarget) {
            // Source version: constructor <init>(String, String)
            var init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V", null, null);
            init.visitCode();
            init.visitVarInsn(Opcodes.ALOAD, 0);
            init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            init.visitInsn(Opcodes.RETURN);
            init.visitMaxs(1, 3);
            init.visitEnd();
        } else {
            // Target version: static factory fromNamespaceAndPath(String, String)
            var factory = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "fromNamespaceAndPath",
                    "(Ljava/lang/String;Ljava/lang/String;)L" + className + ";", null, null);
            factory.visitCode();
            factory.visitTypeInsn(Opcodes.NEW, className);
            factory.visitInsn(Opcodes.DUP);
            factory.visitMethodInsn(Opcodes.INVOKESPECIAL, className, "<init>", "()V", false);
            factory.visitInsn(Opcodes.ARETURN);
            factory.visitMaxs(2, 2);
            factory.visitEnd();

            var init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            init.visitCode();
            init.visitVarInsn(Opcodes.ALOAD, 0);
            init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            init.visitInsn(Opcodes.RETURN);
            init.visitMaxs(1, 1);
            init.visitEnd();
        }

        cw.visitEnd();

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            jos.putNextEntry(new JarEntry(className + ".class"));
            jos.write(cw.toByteArray());
            jos.closeEntry();

            try (var stream = Object.class.getResourceAsStream("/java/lang/Object.class")) {
                if (stream != null) {
                    jos.putNextEntry(new JarEntry("java/lang/Object.class"));
                    stream.transferTo(jos);
                    jos.closeEntry();
                }
            }
        }
    }

    private static String digest(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
    @Test
    void rejectsDuplicateLogicalPathsAcrossInputRoots(@TempDir Path tempDir) throws Exception {
        Path sourceApi = tempDir.resolve("source-api.jar");
        Path targetApi = tempDir.resolve("target-api.jar");
        try (var ignored = new JarOutputStream(Files.newOutputStream(sourceApi))) {}
        try (var ignored = new JarOutputStream(Files.newOutputStream(targetApi))) {}

        Path projectRoot = tempDir.resolve("duplicate-project");
        Path sourceA = projectRoot.resolve("src-a");
        Path sourceB = projectRoot.resolve("src-b");
        Path resourceA = projectRoot.resolve("res-a");
        Path resourceB = projectRoot.resolve("res-b");
        Files.createDirectories(sourceA.resolve("example"));
        Files.createDirectories(sourceB.resolve("example"));
        Files.createDirectories(resourceA.resolve("assets/example"));
        Files.createDirectories(resourceB.resolve("assets/example"));
        Files.writeString(sourceA.resolve("example/Duplicate.java"), "package example; class Duplicate {}");
        Files.writeString(sourceB.resolve("example/Duplicate.java"), "package example; class Duplicate {}");
        Files.writeString(resourceA.resolve("assets/example/data.json"), "{}");
        Files.writeString(resourceB.resolve("assets/example/data.json"), "{}");

        EnvironmentId env = new EnvironmentId("1.20.1", Loader.FABRIC, MappingNamespace.OFFICIAL, 17);
        GeneratedWorkspace sourceWorkspace = new GeneratedWorkspace(projectRoot.resolve("build"), "source-duplicate");
        ResolvedTarget sourceTarget = ResolvedTarget.builder()
                .targetId("source-duplicate")
                .sourceEnvironment(env)
                .targetEnvironment(env)
                .sourceArtifacts(Map.of("api", sourceApi))
                .targetArtifacts(Map.of("api", targetApi))
                .workspace(sourceWorkspace)
                .projectConfiguration(ContinuumProjectConfiguration.empty(projectRoot.resolve("src/main/resources/continuumlib")))
                .build();

        IOException sourceFailure = assertThrows(IOException.class, () ->
                new TargetGenerationPipeline().execute(
                        sourceTarget,
                        projectRoot,
                        List.of(sourceA, sourceB),
                        List.of(),
                        "source.jar"
                ));
        assertTrue(sourceFailure.getMessage().contains("Duplicate source path across roots"));

        GeneratedWorkspace resourceWorkspace = new GeneratedWorkspace(projectRoot.resolve("build"), "resource-duplicate");
        ResolvedTarget resourceTarget = ResolvedTarget.builder()
                .targetId("resource-duplicate")
                .sourceEnvironment(env)
                .targetEnvironment(env)
                .sourceArtifacts(Map.of("api", sourceApi))
                .targetArtifacts(Map.of("api", targetApi))
                .workspace(resourceWorkspace)
                .projectConfiguration(ContinuumProjectConfiguration.empty(projectRoot.resolve("src/main/resources/continuumlib")))
                .build();

        IOException resourceFailure = assertThrows(IOException.class, () ->
                new TargetGenerationPipeline().execute(
                        resourceTarget,
                        projectRoot,
                        List.of(),
                        List.of(resourceA, resourceB),
                        "resource.jar"
                ));
        assertTrue(resourceFailure.getMessage().contains("Duplicate resource path across roots"));
    }

    @Test
    void directPipelineRejectsWorkspaceOverlappingSourceBeforeCleanup(@TempDir Path project) throws Exception {
        Path sourceRoot = project.resolve("src/main/java");
        Path source = sourceRoot.resolve("example/Keep.java");
        Files.createDirectories(source.getParent());
        String original = "package example; public class Keep {}";
        Files.writeString(source, original);

        EnvironmentId env = new EnvironmentId("1.20.1", Loader.FABRIC, MappingNamespace.OFFICIAL, 17);
        GeneratedWorkspace workspace = GeneratedWorkspace.atTargetRoot(sourceRoot, "overlap");
        ResolvedTarget target = ResolvedTarget.builder()
                .targetId("overlap")
                .sourceEnvironment(env)
                .targetEnvironment(env)
                .workspace(workspace)
                .projectConfiguration(ContinuumProjectConfiguration.empty(
                        project.resolve("src/main/resources/continuumlib")))
                .build();

        IOException failure = assertThrows(IOException.class, () ->
                new TargetGenerationPipeline().execute(
                        target,
                        project,
                        List.of(sourceRoot),
                        List.of(project.resolve("src/main/resources")),
                        "target.jar"
                ));

        assertTrue(failure.getMessage().contains("must not overlap consumer input path"));
        assertTrue(Files.isRegularFile(source));
        assertEquals(original, Files.readString(source));
    }

}
