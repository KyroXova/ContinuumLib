package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.continuumlib.knowledge.rule.RulePack;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.Loader;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TargetResolverTest {
    @Test
    void resolvesAllVerifiedPacksOnSelectedRouteAndHonorsTrackedConfig(@TempDir Path project) throws Exception {
        Path configRoot = project.resolve("src/main/resources/continuumlib");
        Path targets = configRoot.resolve("targets");
        Files.createDirectories(targets);
        Files.createDirectories(project.resolve("src/main/java"));
        Files.write(project.resolve("source.jar"), new byte[0]);
        Files.write(project.resolve("target.jar"), new byte[0]);

        Files.writeString(configRoot.resolve("transform.properties"), """
                pack=wrong-route
                source.game=source.jar
                target.game=target.jar
                """);

        Path explicitConfig = targets.resolve("chosen.properties");
        Files.writeString(explicitConfig, """
                pack=route-a
                source.game=source.jar
                target.game=target.jar
                """);

        EnvironmentId source = new EnvironmentId(
                "1.20.1",
                Loader.FORGE,
                MappingNamespace.MOJMAP,
                17
        );
        EnvironmentId target = new EnvironmentId(
                "1.21.1",
                Loader.NEOFORGE,
                MappingNamespace.MOJMAP,
                21
        );
        String digest = "0".repeat(64);

        RulePack first = new RulePack(
                "route-a",
                "fixture-a",
                source,
                target,
                Map.of("game", digest),
                Map.of("game", digest),
                Map.of("old/A", "new/A"),
                Map.of(),
                List.of()
        );
        RulePack second = new RulePack(
                "route-b",
                "fixture-b",
                source,
                target,
                Map.of("game", digest),
                Map.of("game", digest),
                Map.of("old/B", "new/B"),
                Map.of(),
                List.of()
        );

        GeneratedWorkspace workspace = GeneratedWorkspace.atTargetRoot(
                project.resolve("custom-workspace"),
                "chosen"
        );

        ResolvedTarget resolved = new TargetResolver().resolve(
                project,
                "chosen",
                project.resolve("build"),
                List.of(second, first),
                explicitConfig,
                workspace
        );

        assertEquals(List.of("route-a", "route-b"),
                resolved.rulePacks().stream().map(RulePack::id).toList());
        assertEquals(workspace.rootDir(), resolved.workspace().rootDir());
        assertEquals(source, resolved.sourceEnvironment());
        assertEquals(target, resolved.targetEnvironment());
    }
}
