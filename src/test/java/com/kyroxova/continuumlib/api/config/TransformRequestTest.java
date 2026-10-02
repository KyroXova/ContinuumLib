package com.kyroxova.continuumlib.api.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class TransformRequestTest {
    @TempDir Path dir;
    @Test void acceptsSeparatelyPinnedClasspathWithoutChangingRuleArtifactIdentity() throws Exception {
        Path config = dir.resolve("transform.properties");
        String base = "pack=fixture\nsource.minecraft=old.jar\ntarget.minecraft=new.jar\n";
        Files.writeString(config, base + "classpath.target.runtime.file=runtime.jar\nclasspath.target.runtime.sha256=" + "a".repeat(64) + "\n");
        var request = TransformRequest.read(config, dir);
        assertEquals(java.util.Set.of("minecraft"), request.targetArtifacts().keySet());
        for (String extra : new String[]{"classpath.target.runtime.file=x", "classpath.target.runtime.sha256=" + "a".repeat(64),
                "classpath.target.runtime.file=x\nclasspath.target.runtime.sha256=bad", "classpath.other.runtime.file=x",
                "classpath.target.runtime.file=x\nclasspath.target.runtime.sha256=" + "a".repeat(64) + "\nclasspath.target.runtime.typo=x"}) {
            Files.writeString(config, base + extra);
            assertThrows(IOException.class, () -> TransformRequest.read(config, dir));
        }
    }
    @Test void resolvesArtifactPathsAgainstConsumerRoot() throws Exception {
        Path config = Files.writeString(dir.resolve("transform.properties"), "pack=network\nsource.forge=apis/old.jar\ntarget.forge=apis/new.jar\n");
        var request = TransformRequest.read(config, dir);
        assertEquals("network", request.packId());
        assertEquals(dir.resolve("apis/old.jar"), request.sourceArtifacts().get("forge"));
        assertEquals(dir.resolve("apis/new.jar"), request.targetArtifacts().get("forge"));
    }
    @Test void rejectsDuplicateUnknownAndIncompleteConfiguration() throws Exception {
        Path config = dir.resolve("transform.properties");
        for (String text : new String[]{"pack=a\npack=b", "pack=a\nuniversal=true", "pack=a\nsource.x=a", "pack=\nsource.x=a\ntarget.x=b"}) {
            Files.writeString(config, text);
            assertThrows(IOException.class, () -> TransformRequest.read(config, dir));
        }
    }
    @Test void requiresCompleteExplicitMappingIdentityAndTracksItsFile() throws Exception {
        Path config = dir.resolve("transform.properties");
        String base = "pack=fixture\nsource.minecraft=old.jar\ntarget.minecraft=new.jar\n";
        String mapping = "mapping.source.file=old.txt\nmapping.source.sha256=" + "0".repeat(64)
                + "\nmapping.source.from=OBFUSCATED\nmapping.source.namespaces=source:MOJMAP,target:OBFUSCATED\nmapping.source.license=Mojang mappings license\n";
        Files.writeString(config, base + mapping);
        var request = TransformRequest.read(config, dir);
        assertEquals(dir.resolve("old.txt"), request.sourceMapping().file());
        assertEquals(com.kyroxova.continuumlib.model.environment.MappingNamespace.MOJMAP, request.sourceMapping().namespaces().get("source"));
        Files.writeString(config, base + "mapping.source.file=old.txt\n");
        assertThrows(IOException.class, () -> TransformRequest.read(config, dir));
        Files.writeString(config, base + mapping + "mapping.source.typo=ignored\n");
        assertThrows(IOException.class, () -> TransformRequest.read(config, dir));
    }
    @Test
    void readsExplicitTargetLoaderVersionWithoutTreatingItAsAnArtifact() throws Exception {
        Path config = dir.resolve("transform.properties");
        Files.writeString(config,
                "pack=fixture\nsource.minecraft=old.jar\ntarget.minecraft=new.jar\ntarget.loaderVersion=47.2.17\n");

        var request = TransformRequest.read(config, dir);

        assertEquals("47.2.17", request.targetLoaderVersion());
        assertEquals(java.util.Set.of("minecraft"), request.targetArtifacts().keySet());

        Files.writeString(config,
                "pack=fixture\nsource.minecraft=old.jar\ntarget.minecraft=new.jar\ntarget.loaderVersion=   \n");
        assertThrows(IOException.class, () -> TransformRequest.read(config, dir));
    }

    @Test
    void normalizesProgrammaticMappingIdentity() {
        Path relative = Path.of("mappings/test.tiny");
        var request = new MappingRequest(
                relative,
                "A".repeat(64),
                com.kyroxova.continuumlib.model.environment.MappingNamespace.OBFUSCATED,
                java.util.Map.of(
                        "obf", com.kyroxova.continuumlib.model.environment.MappingNamespace.OBFUSCATED,
                        "named", com.kyroxova.continuumlib.model.environment.MappingNamespace.MOJMAP
                ),
                "  Synthetic License  "
        );

        assertTrue(request.file().isAbsolute());
        assertEquals(relative.toAbsolutePath().normalize(), request.file());
        assertEquals("a".repeat(64), request.sha256());
        assertEquals("Synthetic License", request.license());
    }

    @Test
    void rejectsInvalidProgrammaticMappingIdentity() {
        var namespaces = java.util.Map.of(
                "obf", com.kyroxova.continuumlib.model.environment.MappingNamespace.OBFUSCATED
        );

        assertThrows(NullPointerException.class, () ->
                new MappingRequest(
                        Path.of("map.tiny"),
                        "0".repeat(64),
                        com.kyroxova.continuumlib.model.environment.MappingNamespace.OBFUSCATED,
                        null,
                        "license"
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new MappingRequest(
                        Path.of("map.tiny"),
                        "bad",
                        com.kyroxova.continuumlib.model.environment.MappingNamespace.OBFUSCATED,
                        namespaces,
                        "license"
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new MappingRequest(
                        Path.of("map.tiny"),
                        "0".repeat(64),
                        com.kyroxova.continuumlib.model.environment.MappingNamespace.OBFUSCATED,
                        namespaces,
                        "   "
                )
        );
    }

    @Test
    void normalizesProgrammaticTransformRequestIdentity() {
        var request = new TransformRequest(
                "  fixture  ",
                java.util.Map.of(" game ", Path.of("relative/source.jar")),
                java.util.Map.of("game", Path.of("relative/target.jar"))
        );

        assertEquals("fixture", request.packId());
        assertEquals(
                Path.of("relative/source.jar").toAbsolutePath().normalize(),
                request.sourceArtifacts().get("game")
        );
        assertEquals(
                Path.of("relative/target.jar").toAbsolutePath().normalize(),
                request.targetArtifacts().get("game")
        );
    }

    @Test
    void rejectsInvalidProgrammaticTransformRequestIdentity() {
        assertThrows(IllegalArgumentException.class, () ->
                new TransformRequest(
                        "fixture",
                        java.util.Map.of(),
                        java.util.Map.of("game", Path.of("target.jar"))
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new TransformRequest(
                        "fixture",
                        java.util.Map.of("   ", Path.of("source.jar")),
                        java.util.Map.of("game", Path.of("target.jar"))
                )
        );
        assertThrows(NullPointerException.class, () ->
                new TransformRequest(
                        "fixture",
                        null,
                        java.util.Map.of("game", Path.of("target.jar"))
                )
        );
    }

    @Test
    void preservesDeterministicArtifactOrdering() {
        java.util.Map<String, Path> source = new java.util.LinkedHashMap<>();
        source.put("zeta", Path.of("z.jar"));
        source.put("alpha", Path.of("a.jar"));

        java.util.Map<String, Path> target = new java.util.LinkedHashMap<>();
        target.put("omega", Path.of("o.jar"));
        target.put("beta", Path.of("b.jar"));

        var request = new TransformRequest("fixture", source, target);

        assertEquals(
                java.util.List.of("alpha", "zeta"),
                new java.util.ArrayList<>(request.sourceArtifacts().keySet())
        );
        assertEquals(
                java.util.List.of("beta", "omega"),
                new java.util.ArrayList<>(request.targetArtifacts().keySet())
        );
    }

}
