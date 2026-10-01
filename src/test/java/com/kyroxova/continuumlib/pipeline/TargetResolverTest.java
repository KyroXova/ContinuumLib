package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.continuumlib.api.config.TransformRequest;
import com.kyroxova.continuumlib.knowledge.rule.RulePack;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.Loader;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TargetResolverTest {
    private static final String HASH = "0".repeat(64);

    @Test
    void resolvesOneAuthoritativeTargetContext() {
        EnvironmentId source = new EnvironmentId("1.20.1", Loader.FORGE, MappingNamespace.MOJMAP, 17);
        EnvironmentId target = new EnvironmentId("1.21.1", Loader.NEOFORGE, MappingNamespace.MOJMAP, 21);
        RulePack pack = new RulePack("route", "fixture", source, target,
                Map.of("api", HASH), Map.of("api", HASH), Map.of(), Map.of(), List.of(), List.of());
        TransformRequest request = new TransformRequest("route",
                Map.of("api", Path.of("source.jar")), Map.of("api", Path.of("target.jar")));

        ResolvedTarget resolved = new TargetResolver().resolve("neoforge-1.21.1", request, List.of(pack), "21.1.0", "per_version");

        assertEquals(source, resolved.sourceEnvironment());
        assertEquals(target, resolved.targetEnvironment());
        assertEquals(target, resolved.context().environmentId());
        assertEquals("21.1.0", resolved.context().loaderVersion());
        assertEquals(List.of(Path.of("source.jar").toAbsolutePath().normalize()), resolved.sourceClasspath());
        assertEquals(List.of(Path.of("target.jar").toAbsolutePath().normalize()), resolved.targetClasspath());
    }

    @Test
    void rejectsUnknownPack() {
        TransformRequest request = new TransformRequest("missing",
                Map.of("api", Path.of("source.jar")), Map.of("api", Path.of("target.jar")));
        assertThrows(IllegalArgumentException.class,
                () -> new TargetResolver().resolve("target", request, List.of()));
    }
}
