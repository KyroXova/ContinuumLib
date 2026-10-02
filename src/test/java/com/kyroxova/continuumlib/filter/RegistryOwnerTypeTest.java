package com.kyroxova.continuumlib.filter;

import com.kyroxova.continuumlib.filter.condition.EnvironmentCondition;
import com.kyroxova.continuumlib.filter.condition.TargetContext;
import com.kyroxova.continuumlib.filter.domain.RegistryType;
import com.kyroxova.continuumlib.filter.engine.FilterEngine;
import com.kyroxova.continuumlib.filter.rule.ExclusionRuleSet;
import com.kyroxova.continuumlib.filter.rule.InclusionRuleSet;
import com.kyroxova.continuumlib.filter.rule.RegistryFilterRule;
import com.kyroxova.continuumlib.filter.rule.RuleSet;
import com.kyroxova.continuumlib.filter.validation.ExclusionConflictException;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.Loader;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;
import com.kyroxova.continuumlib.source.ast.SourceParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RegistryOwnerTypeTest {
    private static final TargetContext TARGET = TargetContext.of(new EnvironmentId(
            "1.21.1",
            Loader.FORGE,
            MappingNamespace.OFFICIAL,
            21
    ));

    @Test
    void registryEntryDeclaredInEnumIsRemoved() {
        String code = """
                package example;
                import net.minecraftforge.registries.RegistryObject;
                import net.minecraft.world.level.block.Block;

                public enum Entries {
                    INSTANCE;

                    public static final RegistryObject<Block> OLD =
                            BLOCKS.register("old", () -> null);
                }
                """;

        var unit = new SourceParser(List.of(), List.of()).parseString("example/Entries.java", code);
        var result = new FilterEngine().process(
                TARGET,
                InclusionRuleSet.EMPTY,
                exclusions(),
                List.of(unit),
                Map.of()
        );

        assertEquals(1, result.excludedRegistryEntries().size());
        assertFalse(result.activeSources().get(0).ast().toString().contains("OLD ="));
    }

    @Test
    void enumReferenceToExcludedEntryIsRejectedBeforeRemoval() {
        String code = """
                package example;
                import net.minecraftforge.registries.RegistryObject;
                import net.minecraft.world.level.block.Block;

                public enum Entries {
                    INSTANCE;

                    public static final RegistryObject<Block> OLD =
                            BLOCKS.register("old", () -> null);

                    public static RegistryObject<Block> selected() {
                        return OLD;
                    }
                }
                """;

        var unit = new SourceParser(List.of(), List.of()).parseString("example/Entries.java", code);

        assertThrows(
                ExclusionConflictException.class,
                () -> new FilterEngine().process(
                        TARGET,
                        InclusionRuleSet.EMPTY,
                        exclusions(),
                        List.of(unit),
                        Map.of()
                )
        );
    }

    private static ExclusionRuleSet exclusions() {
        return new ExclusionRuleSet(RuleSet.builder()
                .addRegistry(new RegistryFilterRule(
                        RegistryType.BLOCK,
                        "old",
                        EnvironmentCondition.ALWAYS,
                        Path.of("registry.json")
                ))
                .build());
    }
}
