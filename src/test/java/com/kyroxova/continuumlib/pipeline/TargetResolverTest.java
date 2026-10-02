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
    @Test
    void rejectsUnsafeOrMismatchedWorkspaceIdentity(@TempDir Path root) {
        EnvironmentId env = new EnvironmentId("1.20.1", Loader.FORGE, MappingNamespace.MOJMAP, 17);

        assertThrows(IllegalArgumentException.class, () -> ResolvedTarget.builder()
                .targetId("../escape")
                .sourceEnvironment(env)
                .targetEnvironment(env)
                .workspace(new GeneratedWorkspace(root.resolve("build"), "safe"))
                .build());

        assertThrows(IllegalArgumentException.class, () -> ResolvedTarget.builder()
                .targetId("expected")
                .sourceEnvironment(env)
                .targetEnvironment(env)
                .workspace(new GeneratedWorkspace(root.resolve("build"), "different"))
                .build());

        assertThrows(NullPointerException.class, () -> ResolvedTarget.builder()
                .targetId("missing-workspace")
                .sourceEnvironment(env)
                .targetEnvironment(env)
                .build());
    }

    @Test
    void carriesConfiguredLoaderVersionIntoTargetContext(@TempDir Path project) throws Exception {
        String hash = "0".repeat(64);
        Path sourceArtifact = project.resolve("source.jar");
        Path targetArtifact = project.resolve("target.jar");
        Files.write(sourceArtifact, new byte[0]);
        Files.write(targetArtifact, new byte[0]);

        EnvironmentId env = new EnvironmentId("1.20.1", Loader.FORGE, MappingNamespace.MOJMAP, 17);
        RulePack pack = new RulePack(
                "loader-version-route",
                "fixture",
                env,
                env,
                Map.of("api", hash),
                Map.of("api", hash),
                Map.of(),
                Map.of(),
                List.of(),
                List.of()
        );

        Path configRoot = project.resolve("src/main/resources/continuumlib");
        Files.createDirectories(configRoot);
        Path transform = configRoot.resolve("transform.properties");
        Files.writeString(transform,
                "pack=loader-version-route\n"
                        + "source.api=source.jar\n"
                        + "target.api=target.jar\n"
                        + "target.loaderVersion=47.2.17\n");

        ResolvedTarget resolved = new TargetResolver().resolve(
                project,
                "loader-version",
                project.resolve("build"),
                List.of(pack),
                transform
        );

        assertEquals("47.2.17", resolved.loaderVersion());
        assertEquals("47.2.17", resolved.toTargetContext().loaderVersion());
    }

    @Test
    void targetContextUsesFinalOutputNamespace(@TempDir Path root) {
        EnvironmentId env = new EnvironmentId("1.20.1", Loader.FORGE, MappingNamespace.MOJMAP, 17);
        ResolvedTarget target = ResolvedTarget.builder()
                .targetId("obfuscated")
                .sourceEnvironment(env)
                .targetEnvironment(env)
                .outputNamespace(MappingNamespace.OBFUSCATED)
                .workspace(new GeneratedWorkspace(root.resolve("build"), "obfuscated"))
                .build();

        assertEquals(MappingNamespace.OBFUSCATED, target.mappingNamespace());
        assertEquals(MappingNamespace.OBFUSCATED, target.toTargetContext().environmentId().mappings());
    }

}
