package com.kyroxova.continuumlib.knowledge.mapping;

import com.kyroxova.continuumlib.model.environment.*;
import com.kyroxova.continuumlib.model.symbol.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import java.nio.file.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MappingIoImporterTest {
    private final EnvironmentId env = new EnvironmentId("1.18.2", Loader.FABRIC, MappingNamespace.INTERMEDIARY, 17);
    private final String tiny = "tiny\t2\t0\tofficial\tintermediary\tnamed\n"
            + "c\ta\tgame/class_1\tgame/Block\n"
            + "\tf\tLb;\ta\tfield_1\tstate\n"
            + "\tm\t(Lb;)Lb;\ta\tmethod_1\tshape\n"
            + "\tm\t(I)I\ta\tmethod_2\tstrength\n"
            + "\tm\t()V\t<init>\t<init>\t<init>\n"
            + "c\tb\tgame/class_2\tgame/State\n";
    private MappingProvenance provenance(byte[] bytes) throws Exception {
        return new MappingProvenance("Synthetic namespace fixture", URI.create("https://example.invalid/fixture.tiny"),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), "Test fixture");
    }
    private MappingIoImporter importer() {
        return new MappingIoImporter(Map.of("official", MappingNamespace.OBFUSCATED, "intermediary", MappingNamespace.INTERMEDIARY, "named", MappingNamespace.YARN));
    }
    @Test void mapsDescriptorsThroughAllNamespacesAndPreservesOverloadsAndConstructors() throws Exception {
        byte[] data = tiny.getBytes(StandardCharsets.UTF_8);
        var symbols = importer().importMappings(new ByteArrayInputStream(data), env, provenance(data));
        assertEquals(6, symbols.size());
        var shape = symbols.stream().filter(s -> s.aliases().stream().anyMatch(a -> a.name().equals("shape"))).findFirst().orElseThrow();
        var intermediary = shape.aliases().stream().filter(a -> a.namespace() == MappingNamespace.INTERMEDIARY).findFirst().orElseThrow();
        assertEquals("game/class_1", intermediary.owner());
        assertEquals("(Lgame/class_2;)Lgame/class_2;", intermediary.descriptor());
        assertEquals(env, intermediary.environment());
        var named = shape.aliases().stream().filter(a -> a.namespace() == MappingNamespace.YARN).findFirst().orElseThrow();
        assertEquals("(Lgame/State;)Lgame/State;", named.descriptor());
        assertEquals(MappingNamespace.YARN, named.environment().mappings());
        assertEquals(1, symbols.stream().filter(s -> s.aliases().get(0).kind() == SymbolKind.CONSTRUCTOR).count());
        assertEquals(2, symbols.stream().filter(s -> s.aliases().get(0).kind() == SymbolKind.METHOD).count());
    }
    @Test void rejectsMismatchedDigestAndUnboundNamespaceNames() throws Exception {
        byte[] data = tiny.getBytes(StandardCharsets.UTF_8);
        var wrong = new MappingProvenance("fixture", URI.create("https://example.invalid"), "0".repeat(64), "fixture");
        assertThrows(IOException.class, () -> importer().importMappings(new ByteArrayInputStream(data), env, wrong));
        var importer = new MappingIoImporter(Map.of("missing", MappingNamespace.MOJMAP));
        assertThrows(IOException.class, () -> importer.importMappings(new ByteArrayInputStream(data), env, provenance(data)));
    }
    @Test void importsActualOfficialMappingsWhenSupplied() throws Exception {
        String path = System.getProperty("continuumlib.officialMappings");
        Assumptions.assumeTrue(path != null, "Supply the official 1.18.2 client mappings path");
        byte[] data = Files.readAllBytes(Path.of(path));
        var provenance = new MappingProvenance("Local official 1.18.2 mapping artifact", Path.of(path).toUri(),
                "a2aa6ee1030bfef79e9b2e08e79de1637fdd7ecb5bf8891cf2e9a4b186042543", "Mojang mapping license in supplied artifact; not redistributed");
        var base = new EnvironmentId("1.18.2", Loader.FORGE, MappingNamespace.MOJMAP, 17);
        var symbols = new MappingIoImporter(Map.of("source", MappingNamespace.MOJMAP, "target", MappingNamespace.OBFUSCATED))
                .importMappings(new ByteArrayInputStream(data), base, provenance);
        long classes = symbols.stream().filter(s -> s.aliases().get(0).kind() == SymbolKind.CLASS).count();
        assertTrue(classes > 5000);
        assertTrue(symbols.stream().flatMap(s -> s.aliases().stream()).anyMatch(a -> a.owner().equals("net/minecraft/world/level/block/Block") && a.kind() == SymbolKind.CLASS));
        System.out.printf("Official mapping import: %d classes, %d total symbols%n", classes, symbols.size());
    }
    @Test void enrichesLegacySrgFieldDescriptorsFromActualDeclarationsInsteadOfGuessing() throws Exception {
        byte[] data = ("CL: a game/Block\nCL: b game/State\nFD: a/c game/Block/field_1\nFD: a/clientOnly game/Block/field_client\n"
                + "MD: a/d (Lb;)Lb; game/Block/func_1 (Lgame/State;)Lgame/State;\n").getBytes(StandardCharsets.UTF_8);
        var bindings = Map.of("source", MappingNamespace.OBFUSCATED, "target", MappingNamespace.SRG);
        var declarations = Map.of("a", new com.kyroxova.continuumlib.bytecode.ClassInfo("a", "java/lang/Object", List.of(), 1,
                List.of(new com.kyroxova.continuumlib.bytecode.ClassInfo.Member("c", "Lb;", 1, null)), List.of()));
        assertThrows(IOException.class, () -> new MappingIoImporter(bindings).importMappings(new ByteArrayInputStream(data), env, provenance(data)));
        var symbols = new MappingIoImporter(bindings, MappingNamespace.OBFUSCATED, declarations)
                .importMappings(new ByteArrayInputStream(data), env, provenance(data));
        var field = symbols.stream().flatMap(s -> s.aliases().stream()).filter(a -> a.name().equals("field_1")).findFirst().orElseThrow();
        assertEquals("Lgame/State;", field.descriptor());
        assertTrue(symbols.stream().flatMap(s -> s.aliases().stream()).noneMatch(a -> a.name().equals("field_client")));
    }
}
