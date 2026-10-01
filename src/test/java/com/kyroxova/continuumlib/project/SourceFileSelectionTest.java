package com.kyroxova.continuumlib.project;

import com.kyroxova.continuumlib.filter.condition.EnvironmentCondition;
import com.kyroxova.continuumlib.filter.condition.TargetContext;
import com.kyroxova.continuumlib.filter.config.ContinuumProjectConfiguration;
import com.kyroxova.continuumlib.filter.rule.ExclusionRuleSet;
import com.kyroxova.continuumlib.filter.rule.InclusionRuleSet;
import com.kyroxova.continuumlib.filter.rule.RuleSet;
import com.kyroxova.continuumlib.filter.rule.SourceFilterRule;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.Loader;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;
import com.kyroxova.continuumlib.source.ast.SourceParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SourceFileSelectionTest {
    @Test
    void sourceExclusionHappensBeforeParsing(@TempDir Path sourceRoot) throws Exception {
        Path valid = sourceRoot.resolve("example/Valid.java");
        Path broken = sourceRoot.resolve("example/Broken.java");
        Files.createDirectories(valid.getParent());
        Files.writeString(valid, "package example; public class Valid {}");
        Files.writeString(broken, "package example; public class Broken { this is not java");

        var exclusions = new ExclusionRuleSet(RuleSet.builder()
                .addSource(new SourceFilterRule(
                        "example/Broken.java",
                        EnvironmentCondition.ALWAYS,
                        Path.of("exclusions/source.json")))
                .build());
        var configuration = new ContinuumProjectConfiguration(
                Path.of("continuumlib"),
                InclusionRuleSet.EMPTY,
                exclusions
        );
        var context = TargetContext.of(new EnvironmentId(
                "1.20.1",
                Loader.FORGE,
                MappingNamespace.MOJMAP,
                17
        ));

        SourceFileSelection selection = SourceFileSelection.discover(sourceRoot, context, configuration);

        assertEquals(List.of(valid.toAbsolutePath().normalize()), selection.activeFiles());
        assertEquals(List.of(broken.toAbsolutePath().normalize()), selection.excludedFiles());

        var parsed = new SourceParser(List.of(sourceRoot), List.of())
                .parseFiles(sourceRoot, selection.activeFiles());
        assertEquals(1, parsed.size());
        assertEquals("example/Valid.java", parsed.get(0).relativePath());
    }
}
