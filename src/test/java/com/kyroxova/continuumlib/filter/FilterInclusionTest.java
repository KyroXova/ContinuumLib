package com.kyroxova.continuumlib.filter;

import com.kyroxova.continuumlib.filter.condition.EnvironmentCondition;
import com.kyroxova.continuumlib.filter.condition.TargetContext;
import com.kyroxova.continuumlib.filter.config.FilterConfigurationException;
import com.kyroxova.continuumlib.filter.domain.RegistryType;
import com.kyroxova.continuumlib.filter.engine.FilterEngine;
import com.kyroxova.continuumlib.filter.rule.*;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.Loader;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;
import com.kyroxova.continuumlib.source.ast.SourceParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FilterInclusionTest {
    private static final TargetContext TARGET = TargetContext.of(new EnvironmentId(
            "1.21.1",
            Loader.FORGE,
            MappingNamespace.OFFICIAL,
            21
    ));

    @Test
    void sourceAndResourceInclusionsArePerDomainAllowlists(@TempDir Path tempDir) throws Exception {
        var parser = new SourceParser(List.of(), List.of());
        var keep = parser.parseString("example/Keep.java", "package example; public class Keep {}");
        var omit = parser.parseString("example/Omit.java", "package example; public class Omit {}");

        Path keepResource = tempDir.resolve("keep.json");
        Path omitResource = tempDir.resolve("omit.json");
        Files.writeString(keepResource, "{}");
        Files.writeString(omitResource, "{}");

        RuleSet rules = RuleSet.builder()
                .addSource(new SourceFilterRule("example/Keep.java", EnvironmentCondition.ALWAYS, Path.of("include.json")))
                .addResource(new ResourceFilterRule("assets/example/keep.json", EnvironmentCondition.ALWAYS, Path.of("include.json")))
                .build();

        var result = new FilterEngine().process(
                TARGET,
                new InclusionRuleSet(rules),
                ExclusionRuleSet.EMPTY,
                List.of(keep, omit),
                Map.of(
                        "assets/example/keep.json", keepResource,
                        "assets/example/omit.json", omitResource
                )
        );

        assertEquals(List.of("example/Keep.java"),
                result.activeSources().stream().map(unit -> unit.relativePath()).toList());
        assertEquals(List.of("example/Omit.java"),
                result.excludedSources().stream().map(unit -> unit.relativePath()).toList());
        assertEquals(Map.of("assets/example/keep.json", keepResource), result.activeResources());
        assertEquals(Map.of("assets/example/omit.json", omitResource), result.excludedResources());
    }

    @Test
    void classInclusionFiltersAfterParsing() {
        var parser = new SourceParser(List.of(), List.of());
        var keep = parser.parseString("example/Keep.java", "package example; public class Keep {}");
        var omit = parser.parseString("example/Omit.java", "package example; public class Omit {}");

        RuleSet rules = RuleSet.builder()
                .addClass(new ClassFilterRule("example.Keep", EnvironmentCondition.ALWAYS, Path.of("classes.json")))
                .build();

        var result = new FilterEngine().process(
                TARGET,
                new InclusionRuleSet(rules),
                ExclusionRuleSet.EMPTY,
                List.of(keep, omit),
                Map.of()
        );

        assertEquals(List.of("example/Keep.java"),
                result.activeSources().stream().map(unit -> unit.relativePath()).toList());
        assertEquals(List.of("example/Omit.java"),
                result.excludedSources().stream().map(unit -> unit.relativePath()).toList());
    }

    @Test
    void registryInclusionKeepsOnlySelectedDeclaration() {
        String code = """
                package example;
                import net.minecraftforge.registries.DeferredRegister;
                import net.minecraftforge.registries.RegistryObject;
                import net.minecraft.world.level.block.Block;

                public class Blocks {
                    public static final DeferredRegister<Block> BLOCKS = null;
                    public static final RegistryObject<Block> KEEP = BLOCKS.register("keep", () -> null);
                    public static final RegistryObject<Block> OMIT = BLOCKS.register("omit", () -> null);
                }
                """;

        var unit = new SourceParser(List.of(), List.of()).parseString("example/Blocks.java", code);
        RuleSet rules = RuleSet.builder()
                .addRegistry(new RegistryFilterRule(
                        RegistryType.BLOCK,
                        "keep",
                        EnvironmentCondition.ALWAYS,
                        Path.of("blocks.json")
                ))
                .build();

        var result = new FilterEngine().process(
                TARGET,
                new InclusionRuleSet(rules),
                ExclusionRuleSet.EMPTY,
                List.of(unit),
                Map.of()
        );

        assertEquals(1, result.excludedRegistryEntries().size());
        assertEquals("omit", result.excludedRegistryEntries().get(0).id());
        String generated = result.activeSources().get(0).ast().toString();
        assertTrue(generated.contains("KEEP = BLOCKS.register"));
        assertFalse(generated.contains("OMIT = BLOCKS.register"));
    }

    @Test
    void simultaneouslyActiveIncludeAndExcludeRulesFail() {
        var parser = new SourceParser(List.of(), List.of());
        var unit = parser.parseString("example/Keep.java", "package example; public class Keep {}");

        var inclusion = new InclusionRuleSet(RuleSet.builder()
                .addSource(new SourceFilterRule(
                        "example/Keep.java",
                        new EnvironmentCondition(">=1.20", null, null, null, null, null),
                        Path.of("include.json")
                ))
                .build());
        var exclusion = new ExclusionRuleSet(RuleSet.builder()
                .addSource(new SourceFilterRule(
                        "example/Keep.java",
                        new EnvironmentCondition(">=1.21", null, null, null, null, null),
                        Path.of("exclude.json")
                ))
                .build());

        assertThrows(FilterConfigurationException.class, () ->
                new FilterEngine().process(TARGET, inclusion, exclusion, List.of(unit), Map.of())
        );
    }
    @Test
    void classRulesFilterEveryTopLevelDeclarationInACompilationUnit() {
        var parser = new SourceParser(List.of(), List.of());
        var unit = parser.parseString(
                "example/Combined.java",
                "package example; class Keep {} class Omit {}"
        );

        RuleSet includeRules = RuleSet.builder()
                .addClass(new ClassFilterRule(
                        "example.Keep",
                        EnvironmentCondition.ALWAYS,
                        Path.of("classes.json")
                ))
                .build();

        var result = new FilterEngine().process(
                TARGET,
                new InclusionRuleSet(includeRules),
                ExclusionRuleSet.EMPTY,
                List.of(unit),
                Map.of()
        );

        assertEquals(1, result.activeSources().size());
        String generated = result.activeSources().get(0).ast().toString();
        assertTrue(generated.contains("class Keep"), generated);
        assertFalse(generated.contains("class Omit"), generated);
    }

}
