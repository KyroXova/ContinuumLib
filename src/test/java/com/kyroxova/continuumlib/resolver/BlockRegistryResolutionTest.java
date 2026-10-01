package com.kyroxova.continuumlib.resolver;

import com.kyroxova.continuumlib.analyzer.JavaProjectAnalyzer;
import com.kyroxova.continuumlib.knowledge.capability.registry.Forge1182BlockRegistryCapability;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.Loader;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;
import com.kyroxova.continuumlib.model.resolution.ResolutionStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockRegistryResolutionTest {
    private static final EnvironmentId FORGE_1182 =
            new EnvironmentId("1.18.2", Loader.FORGE, MappingNamespace.MOJMAP, 17);

    @Test
    void resolvesEveryRecognizedOperationForSameEnvironment() throws Exception {
        var project = new JavaProjectAnalyzer().analyze(
                Path.of("src/test/resources/fixtures/forge-1.18.2"), FORGE_1182);
        var resolver = new ContinuumLibResolver(List.of(new Forge1182BlockRegistryCapability()));

        var plan = resolver.resolve(project, FORGE_1182, FORGE_1182);

        assertEquals(project.operations().size(), plan.resolutions().size());
        assertTrue(plan.resolutions().stream().allMatch(r -> r.status() == ResolutionStatus.DIRECT));
        assertTrue(plan.resolutions().stream().allMatch(r -> !r.provenance().isBlank()));
    }

    @Test
    void reportsUnsupportedTargetWithoutDroppingOperations() throws Exception {
        var project = new JavaProjectAnalyzer().analyze(
                Path.of("src/test/resources/fixtures/forge-1.18.2"), FORGE_1182);
        var fabric = new EnvironmentId("1.21.1", Loader.FABRIC, MappingNamespace.INTERMEDIARY, 21);
        var resolver = new ContinuumLibResolver(List.of(new Forge1182BlockRegistryCapability()));

        var plan = resolver.resolve(project, FORGE_1182, fabric);

        assertEquals(project.operations().size(), plan.resolutions().size());
        assertTrue(plan.resolutions().stream().allMatch(r -> r.status() == ResolutionStatus.UNSUPPORTED));
    }
}
