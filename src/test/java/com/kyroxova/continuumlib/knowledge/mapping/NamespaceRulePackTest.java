package com.kyroxova.continuumlib.knowledge.mapping;

import com.kyroxova.continuumlib.knowledge.rule.RuleCatalog;
import com.kyroxova.continuumlib.model.environment.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.net.URI;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NamespaceRulePackTest {
    @TempDir Path dir;
    public static class SourceApi { public int value = 4; public int oldMethod() { return value * 3; } }
    public static class TargetApi { public int strength = 4; public int shape() { return strength * 3; } }
    private byte[] bytes(Class<?> type) throws IOException {
        try (var in = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) { return in.readAllBytes(); }
    }
    private String digest(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    @Test void importedMappingsDriveClassFieldAndMethodTransformationWithoutSourceRegeneration() throws Exception {
        String from = SourceApi.class.getName().replace('.', '/'), to = TargetApi.class.getName().replace('.', '/');
        byte[] mapping = ("tiny\t2\t0\tfrom\tto\nc\t" + from + "\t" + to
                + "\n\tf\tI\tvalue\tstrength\n\tm\t()I\toldMethod\tshape\n\tm\t()V\t<init>\t<init>\n").getBytes(StandardCharsets.UTF_8);
        var source = new EnvironmentId("1.18.2", Loader.FABRIC, MappingNamespace.OBFUSCATED, 17);
        var target = new EnvironmentId("1.18.2", Loader.FABRIC, MappingNamespace.YARN, 17);
        var provenance = new MappingProvenance("Synthetic fixture", URI.create("https://example.invalid/fixture"), digest(mapping), "Fixture");
        var symbols = new MappingIoImporter(Map.of("from", source.mappings(), "to", target.mappings()))
                .importMappings(new ByteArrayInputStream(mapping), source, provenance);
        Path oldApi = Files.write(dir.resolve("old.class"), bytes(SourceApi.class));
        Path newApi = Files.write(dir.resolve("new.class"), bytes(TargetApi.class));
        var pack = NamespaceRulePack.create("fixture", provenance, source, target,
                Map.of("api", digest(bytes(SourceApi.class))), Map.of("api", digest(bytes(TargetApi.class))), symbols);
        byte[] adapted = new RuleCatalog(List.of(pack)).select(source, target).bind(Map.of("api", oldApi), Map.of("api", newApi)).adapt(bytes(SourceApi.class));
        Class<?> type = new ClassLoader(getClass().getClassLoader()) { Class<?> define() { return defineClass(null, adapted, 0, adapted.length); } }.define();
        assertEquals(TargetApi.class.getName(), type.getName());
        Object instance = type.getConstructor().newInstance();
        assertEquals(12, type.getMethod("shape").invoke(instance));
        assertEquals(4, type.getField("strength").get(instance));
        assertThrows(NoSuchMethodException.class, () -> type.getMethod("oldMethod"));
        var wrongVersion = new EnvironmentId("1.19.2", Loader.FABRIC, MappingNamespace.YARN, 17);
        assertThrows(IllegalArgumentException.class, () -> NamespaceRulePack.create("bad", provenance, source, wrongVersion,
                pack.sourceArtifacts(), pack.targetArtifacts(), symbols));
    }
}
