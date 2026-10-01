package com.kyroxova.continuumlib.filter;

import com.kyroxova.continuumlib.filter.condition.EnvironmentCondition;
import com.kyroxova.continuumlib.filter.condition.TargetContext;
import com.kyroxova.continuumlib.filter.condition.VersionConstraint;
import com.kyroxova.continuumlib.filter.config.ContinuumProjectConfiguration;
import com.kyroxova.continuumlib.filter.config.FilterConfigurationException;
import com.kyroxova.continuumlib.filter.config.FilterConfigurationReader;
import com.kyroxova.continuumlib.filter.domain.RegistryType;
import com.kyroxova.continuumlib.filter.engine.FilterEngine;
import com.kyroxova.continuumlib.filter.rule.ExclusionRuleSet;
import com.kyroxova.continuumlib.filter.rule.InclusionRuleSet;
import com.kyroxova.continuumlib.filter.rule.RegistryFilterRule;
import com.kyroxova.continuumlib.filter.validation.ExclusionConflictException;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.Loader;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;
import com.kyroxova.continuumlib.source.ast.SourceParser;
import com.kyroxova.continuumlib.source.ast.SourceUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FilterSystemTest {

    private final EnvironmentId env1201 = new EnvironmentId("1.20.1", Loader.FORGE, MappingNamespace.OFFICIAL, 17);
    private final EnvironmentId env1211 = new EnvironmentId("1.21.1", Loader.FORGE, MappingNamespace.OFFICIAL, 21);

    @Test
    void testVersionConstraintEvaluations() {
        var ge121 = VersionConstraint.parse(">=1.21");
        assertFalse(ge121.test("1.20.1"));
        assertTrue(ge121.test("1.21"));
        assertTrue(ge121.test("1.21.1"));

        var range = VersionConstraint.parse(">=1.20 <1.21");
        assertTrue(range.test("1.20"));
        assertTrue(range.test("1.20.1"));
        assertFalse(range.test("1.21"));
        assertFalse(range.test("1.19.4"));

        var exact = VersionConstraint.parse("=1.20.1");
        assertTrue(exact.test("1.20.1"));
        assertFalse(exact.test("1.20"));

        var notEqual = VersionConstraint.parse("!=1.20.1");
        assertFalse(notEqual.test("1.20.1"));
        assertTrue(notEqual.test("1.21.1"));

        // Java version
        assertTrue(VersionConstraint.testJava(">=17", 17));
        assertTrue(VersionConstraint.testJava(">=17", 21));
        assertFalse(VersionConstraint.testJava("<17", 17));
        assertTrue(VersionConstraint.testJava("17", 17));
        assertFalse(VersionConstraint.testJava("17", 21));
    }

    @Test
    void testEnvironmentConditionMatching() {
        TargetContext ctx1201 = TargetContext.of(env1201, "47.1.0", "per_version");
        TargetContext ctx1211 = TargetContext.of(env1211, "51.0.0", "per_version");

        EnvironmentCondition cond = new EnvironmentCondition(
                ">=1.21",
                "forge",
                ">=50.0.0",
                ">=21",
                "official",
                "per_version"
        );

        assertFalse(cond.matches(ctx1201));
        assertTrue(cond.matches(ctx1211));
    }

    @Test
    void testMissingOrEmptyInclusionsAndExclusionsDoNotFilter(@TempDir Path tempDir) throws IOException {
        Path configRoot = tempDir.resolve("continuumlib");
        FilterConfigurationReader reader = new FilterConfigurationReader();

        // 1. Missing directory
        ContinuumProjectConfiguration missing = reader.load(configRoot);
        assertTrue(missing.inclusions().isEmpty());
        assertTrue(missing.exclusions().isEmpty());

        // 2. Empty directories
        Files.createDirectories(configRoot.resolve("inclusions"));
        Files.createDirectories(configRoot.resolve("exclusions"));
        ContinuumProjectConfiguration empty = reader.load(configRoot);
        assertTrue(empty.inclusions().isEmpty());
        assertTrue(empty.exclusions().isEmpty());

        // Run engine with empty configuration
        SourceParser parser = new SourceParser(List.of(), List.of());
        SourceUnit unit = parser.parseString("com/example/MyMod.java", "package com.example; public class MyMod {}");
        Map<String, Path> resources = Map.of("assets/example/test.json", Path.of("test.json"));

        FilterEngine engine = new FilterEngine();
        var result = engine.process(TargetContext.of(env1211), empty.inclusions(), empty.exclusions(),
                List.of(unit), resources);

        assertEquals(1, result.activeSources().size());
        assertEquals(0, result.excludedSources().size());
        assertEquals(1, result.activeResources().size());
        assertEquals(0, result.excludedResources().size());
    }

    @Test
    void testMultiFileRecursiveDiscovery(@TempDir Path tempDir) throws IOException {
        Path configRoot = tempDir.resolve("continuumlib");
        Path exclusions = configRoot.resolve("exclusions");

        // Subdirectories: registry/, source/, resources/
        Path regDir = exclusions.resolve("registry");
        Path srcDir = exclusions.resolve("source");
        Path resDir = exclusions.resolve("resources");
        Files.createDirectories(regDir);
        Files.createDirectories(srcDir);
        Files.createDirectories(resDir);

        // blocks.json
        Files.writeString(regDir.resolve("blocks.json"), """
                {
                  "rules": [
                    {
                      "when": { "minecraft": ">=1.21" },
                      "type": "block",
                      "id": "example:old_block"
                    }
                  ]
                }
                """);

        // items.json
        Files.writeString(regDir.resolve("items.json"), """
                [
                  {
                    "when": { "minecraft": ">=1.21" },
                    "type": "item",
                    "id": "example:old_item"
                  }
                ]
                """);

        // legacy-rendering.json
        Files.writeString(srcDir.resolve("legacy-rendering.json"), """
                {
                  "when": { "minecraft": ">=1.21" },
                  "path": "com/example/legacy/OldRenderer.java"
                }
                """);

        // models.json
        Files.writeString(resDir.resolve("models.json"), """
                {
                  "rules": [
                    {
                      "when": { "minecraft": ">=1.21" },
                      "path": "assets/example/models/block/old_block.json"
                    }
                  ]
                }
                """);

        FilterConfigurationReader reader = new FilterConfigurationReader();
        ContinuumProjectConfiguration config = reader.load(configRoot);

        ExclusionRuleSet loaded = config.exclusions();
        assertEquals(2, loaded.rules().registryRules().size());
        assertEquals(1, loaded.rules().sourceRules().size());
        assertEquals(1, loaded.rules().resourceRules().size());

        // Target context matching
        var active1201 = loaded.filterFor(TargetContext.of(env1201));
        assertTrue(active1201.isEmpty());

        var active1211 = loaded.filterFor(TargetContext.of(env1211));
        assertEquals(2, active1211.rules().registryRules().size());
        assertEquals(1, active1211.rules().sourceRules().size());
        assertEquals(1, active1211.rules().resourceRules().size());
    }

    @Test
    void testRegistryBlockExcludedDoesNotImplyItemExcluded(@TempDir Path tempDir) throws IOException {
        Path srcFile = tempDir.resolve("ModElements.java");
        String code = """
                package com.example;
                import net.minecraftforge.registries.DeferredRegister;
                import net.minecraftforge.registries.RegistryObject;
                import net.minecraft.world.level.block.Block;
                import net.minecraft.world.item.Item;
                
                public class ModElements {
                    public static final DeferredRegister<Block> BLOCKS = null;
                    public static final DeferredRegister<Item> ITEMS = null;
                
                    public static final RegistryObject<Block> TEST = BLOCKS.register("test", () -> null);
                    public static final RegistryObject<Item> TEST_ITEM = ITEMS.register("test", () -> null);
                }
                """;
        Files.writeString(srcFile, code, StandardCharsets.UTF_8);

        SourceParser parser = new SourceParser(List.of(tempDir), List.of());
        List<SourceUnit> units = parser.parseDirectory(tempDir);
        assertEquals(1, units.size());

        // Rule: exclude ONLY the BLOCK "example:test" or "test"
        var rule = new RegistryFilterRule(
                RegistryType.BLOCK,
                "test",
                new EnvironmentCondition(">=1.21", null, null, null, null, null),
                Path.of("test.json")
        );
        ExclusionRuleSet exclusions = new ExclusionRuleSet(
                com.kyroxova.continuumlib.filter.rule.RuleSet.builder().addRegistry(rule).build()
        );

        FilterEngine engine = new FilterEngine();
        var result = engine.process(TargetContext.of(env1211), InclusionRuleSet.EMPTY, exclusions, units, Map.of());

        assertEquals(1, result.excludedRegistryEntries().size());
        assertEquals("test", result.excludedRegistryEntries().get(0).id());
        assertEquals(RegistryType.BLOCK, result.excludedRegistryEntries().get(0).registryType());

        // Verify the generated AST removed TEST (the block) but KEPT TEST_ITEM (the item)
        String transformedCode = result.activeSources().get(0).ast().toString();
        assertFalse(transformedCode.contains("TEST = BLOCKS.register"), "Block TEST should be excluded");
        assertTrue(transformedCode.contains("TEST_ITEM = ITEMS.register"), "Item TEST_ITEM must remain included");

        // Verify original file on disk is NEVER modified
        assertEquals(code, Files.readString(srcFile, StandardCharsets.UTF_8));
    }

    @Test
    void testSourceExclusionOmittedFromTargetAndOriginalUntouched(@TempDir Path tempDir) throws IOException {
        Path legacyFile = tempDir.resolve("OldFeature.java");
        Path activeFile = tempDir.resolve("MainFeature.java");
        String legacyCode = "package com.example; public class OldFeature {}";
        String activeCode = "package com.example; public class MainFeature {}";

        Files.writeString(legacyFile, legacyCode, StandardCharsets.UTF_8);
        Files.writeString(activeFile, activeCode, StandardCharsets.UTF_8);

        SourceParser parser = new SourceParser(List.of(tempDir), List.of());
        List<SourceUnit> units = parser.parseDirectory(tempDir);
        assertEquals(2, units.size());

        var rule = new com.kyroxova.continuumlib.filter.rule.SourceFilterRule(
                "OldFeature.java",
                new EnvironmentCondition(">=1.21", null, null, null, null, null),
                Path.of("test.json")
        );
        ExclusionRuleSet exclusions = new ExclusionRuleSet(
                com.kyroxova.continuumlib.filter.rule.RuleSet.builder().addSource(rule).build()
        );

        FilterEngine engine = new FilterEngine();
        var result = engine.process(TargetContext.of(env1211), InclusionRuleSet.EMPTY, exclusions, units, Map.of());

        assertEquals(1, result.activeSources().size());
        assertEquals("MainFeature.java", result.activeSources().get(0).relativePath());
        assertEquals(1, result.excludedSources().size());
        assertEquals("OldFeature.java", result.excludedSources().get(0).relativePath());

        // Verify original file is untouched
        assertEquals(legacyCode, Files.readString(legacyFile, StandardCharsets.UTF_8));
    }

    @Test
    void testResourceExclusionOmittedFromTarget(@TempDir Path tempDir) throws IOException {
        Path res1 = tempDir.resolve("assets/example/models/block/old_block.json");
        Path res2 = tempDir.resolve("assets/example/models/block/active_block.json");
        Files.createDirectories(res1.getParent());
        Files.writeString(res1, "{}");
        Files.writeString(res2, "{}");

        Map<String, Path> resources = Map.of(
                "assets/example/models/block/old_block.json", res1,
                "assets/example/models/block/active_block.json", res2
        );

        var rule = new com.kyroxova.continuumlib.filter.rule.ResourceFilterRule(
                "assets/example/models/block/old_block.json",
                new EnvironmentCondition(">=1.21", null, null, null, null, null),
                Path.of("test.json")
        );
        ExclusionRuleSet exclusions = new ExclusionRuleSet(
                com.kyroxova.continuumlib.filter.rule.RuleSet.builder().addResource(rule).build()
        );

        FilterEngine engine = new FilterEngine();
        var result = engine.process(TargetContext.of(env1211), InclusionRuleSet.EMPTY, exclusions, List.of(), resources);

        assertEquals(1, result.activeResources().size());
        assertTrue(result.activeResources().containsKey("assets/example/models/block/active_block.json"));
        assertEquals(1, result.excludedResources().size());
        assertTrue(result.excludedResources().containsKey("assets/example/models/block/old_block.json"));
    }

    @Test
    void testReferenceValidationAndConflictDiagnostic() {
        String modBlocksCode = """
                package com.example;
                import net.minecraftforge.registries.DeferredRegister;
                import net.minecraftforge.registries.RegistryObject;
                import net.minecraft.world.level.block.Block;
                
                public class ModBlocks {
                    public static final DeferredRegister<Block> BLOCKS = null;
                    public static final RegistryObject<Block> TEST = BLOCKS.register("test", () -> null);
                }
                """;

        String someFeatureCode = """
                package com.example;
                
                public class SomeFeature {
                    public void useBlock() {
                        ModBlocks.TEST.get();
                    }
                }
                """;

        SourceParser parser = new SourceParser(List.of(), List.of());
        SourceUnit unit1 = parser.parseString("com/example/ModBlocks.java", modBlocksCode);
        SourceUnit unit2 = parser.parseString("com/example/SomeFeature.java", someFeatureCode);

        var rule = new RegistryFilterRule(
                RegistryType.BLOCK,
                "example:test",
                new EnvironmentCondition(">=1.21", null, null, null, null, null),
                Path.of("rules.json")
        );
        ExclusionRuleSet exclusions = new ExclusionRuleSet(
                com.kyroxova.continuumlib.filter.rule.RuleSet.builder().addRegistry(rule).build()
        );

        FilterEngine engine = new FilterEngine();
        ExclusionConflictException ex = assertThrows(ExclusionConflictException.class, () ->
                engine.process(TargetContext.of(env1211), InclusionRuleSet.EMPTY, exclusions, List.of(unit1, unit2), Map.of())
        );

        String message = ex.getMessage();
        assertTrue(message.contains("CONTINUUM EXCLUSION CONFLICT"));
        assertTrue(message.contains("Target:"));
        assertTrue(message.contains("Minecraft 1.21.1"));
        assertTrue(message.contains("Excluded element:"));
        assertTrue(message.contains("BLOCK example:test"));
        assertTrue(message.contains("Declaration:"));
        assertTrue(message.contains("com.example.ModBlocks.TEST"));
        assertTrue(message.contains("Remaining reference:"));
        assertTrue(message.contains("com/example/SomeFeature.java:"));
        assertTrue(message.contains("The excluded registry element is still referenced by target-enabled source."));
    }

    @Test
    void testContradictoryRulesFailFast(@TempDir Path tempDir) throws IOException {
        Path configRoot = tempDir.resolve("continuumlib");
        Path inclusions = configRoot.resolve("inclusions");
        Path exclusions = configRoot.resolve("exclusions");
        Files.createDirectories(inclusions);
        Files.createDirectories(exclusions);

        String ruleJson = """
                {
                  "when": { "minecraft": ">=1.21" },
                  "type": "block",
                  "id": "example:conflict_block"
                }
                """;

        Files.writeString(inclusions.resolve("blocks.json"), ruleJson);
        Files.writeString(exclusions.resolve("blocks.json"), ruleJson);

        FilterConfigurationReader reader = new FilterConfigurationReader();
        assertThrows(FilterConfigurationException.class, () -> reader.load(configRoot));
    }

    @Test
    void testMalformedJsonAndInvalidExpressionsFailFast(@TempDir Path tempDir) throws IOException {
        Path configRoot = tempDir.resolve("continuumlib/exclusions");
        Files.createDirectories(configRoot);

        // 1. Malformed JSON
        Files.writeString(configRoot.resolve("bad_json.json"), "{ invalid json ");
        FilterConfigurationReader reader = new FilterConfigurationReader();
        assertThrows(FilterConfigurationException.class, () -> reader.loadExclusions(configRoot));

        // 2. Invalid version expression
        Files.delete(configRoot.resolve("bad_json.json"));
        Files.writeString(configRoot.resolve("bad_version.json"), """
                {
                  "when": { "minecraft": ">=invalid_version_syntax" },
                  "type": "block",
                  "id": "example:test"
                }
                """);
        assertThrows(FilterConfigurationException.class, () -> reader.loadExclusions(configRoot));
    }
    @Test
    void typedFieldsWithoutExplicitRegistrationAreNotInventedAsRegistryEntries() {
        String code = """
                package com.example;
                import net.minecraft.world.level.block.Block;
                public class Blocks {
                    public static final Block DECORATIVE_ONLY = null;
                }
                """;

        SourceUnit unit = new SourceParser(List.of(), List.of())
                .parseString("com/example/Blocks.java", code);

        var result = new com.kyroxova.continuumlib.filter.registry.RegistryDeclarationScanner()
                .scan(List.of(unit));

        assertTrue(result.entries().isEmpty(), result.entries().toString());
    }

    @Test
    void exclusionValidationDoesNotConfuseSameNamedFieldsOnOtherOwners() {
        String declarations = """
                package com.example;
                class ModBlocks {
                    static final Object BLOCKS = null;
                    static final Object TEST = BLOCKS.register("test", () -> null);
                }
                """;
        String user = """
                package com.example;
                import com.example.ModBlocks;
                class Other { static final Object TEST = new Object(); }
                class User { Object value() { return Other.TEST; } }
                """;

        SourceParser parser = new SourceParser(List.of(), List.of());
        SourceUnit declarationUnit = parser.parseString("com/example/ModBlocks.java", declarations);
        SourceUnit userUnit = parser.parseString("com/example/User.java", user);
        var rule = new RegistryFilterRule(
                RegistryType.CUSTOM_REGISTRY_ENTRY,
                "test",
                EnvironmentCondition.ALWAYS,
                Path.of("rules.json")
        );
        var exclusions = new ExclusionRuleSet(
                com.kyroxova.continuumlib.filter.rule.RuleSet.builder().addRegistry(rule).build());

        assertDoesNotThrow(() -> new FilterEngine().process(
                TargetContext.of(env1211),
                InclusionRuleSet.EMPTY,
                exclusions,
                List.of(declarationUnit, userUnit),
                Map.of()
        ));
    }

    @Test
    void exclusionValidationCatchesUnqualifiedSameClassReferenceBeforeRemoval() {
        String code = """
                package com.example;
                class ModBlocks {
                    static final Object BLOCKS = null;
                    static final Object TEST = BLOCKS.register("test", () -> null);
                    Object use() { return TEST; }
                }
                """;

        SourceUnit unit = new SourceParser(List.of(), List.of())
                .parseString("com/example/ModBlocks.java", code);
        var rule = new RegistryFilterRule(
                RegistryType.CUSTOM_REGISTRY_ENTRY,
                "test",
                EnvironmentCondition.ALWAYS,
                Path.of("rules.json")
        );
        var exclusions = new ExclusionRuleSet(
                com.kyroxova.continuumlib.filter.rule.RuleSet.builder().addRegistry(rule).build());

        assertThrows(ExclusionConflictException.class, () -> new FilterEngine().process(
                TargetContext.of(env1211),
                InclusionRuleSet.EMPTY,
                exclusions,
                List.of(unit),
                Map.of()
        ));
    }

    @Test
    void registryRemovalTargetsExactOwnerWhenFieldNamesRepeat() {
        String code = """
                package com.example;
                class First {
                    static final Object BLOCKS = null;
                    static final Object SAME = BLOCKS.register("first", () -> null);
                }
                class Second {
                    static final Object BLOCKS = null;
                    static final Object SAME = BLOCKS.register("second", () -> null);
                }
                """;

        SourceUnit unit = new SourceParser(List.of(), List.of())
                .parseString("com/example/Combined.java", code);
        var rule = new RegistryFilterRule(
                RegistryType.CUSTOM_REGISTRY_ENTRY,
                "first",
                EnvironmentCondition.ALWAYS,
                Path.of("rules.json")
        );
        var exclusions = new ExclusionRuleSet(
                com.kyroxova.continuumlib.filter.rule.RuleSet.builder().addRegistry(rule).build());

        var result = new FilterEngine().process(
                TargetContext.of(env1211),
                InclusionRuleSet.EMPTY,
                exclusions,
                List.of(unit),
                Map.of()
        );

        String generated = result.activeSources().get(0).ast().toString();
        assertFalse(generated.contains("SAME = BLOCKS.register(\"first\""), generated);
        assertTrue(generated.contains("SAME = BLOCKS.register(\"second\""), generated);
    }

    @Test
    void nestedRegistryDeclarationsKeepQualifiedOwnerIdentity() {
        String code = """
                package com.example;
                class Outer {
                    static class Inner {
                        static final Object BLOCKS = null;
                        static final Object VALUE = BLOCKS.register("nested", () -> null);
                    }
                }
                """;

        SourceUnit unit = new SourceParser(List.of(), List.of())
                .parseString("com/example/Outer.java", code);
        var index = new com.kyroxova.continuumlib.filter.registry.RegistryDeclarationScanner()
                .scan(List.of(unit));

        assertEquals(1, index.entries().size());
        assertEquals("com.example.Outer.Inner", index.entries().get(0).ownerClass());
    }

}
