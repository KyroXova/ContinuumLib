package com.kyroxova.continuumlib.knowledge.mapping;

import com.kyroxova.continuumlib.api.config.MappingRequest;
import com.kyroxova.continuumlib.bytecode.*;
import com.kyroxova.continuumlib.model.environment.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DeclarationNamespaceTest {
    @TempDir Path directory;
    @Test void mapsEveryDeclaredMemberDescriptorAndHierarchyAndVerifiesMappingBytes() throws Exception {
        var file = Files.writeString(directory.resolve("api.tiny"), "tiny\t2\t0\tobf\tnamed\nc\ta\tgame/Id\n\tf\tLa;\tb\tvalue\n\tm\t(La;)La;\tc\tcopy\nc\td\tgame/Child\n");
        var digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        var config = new MappingRequest(file, digest, MappingNamespace.OBFUSCATED,
                Map.of("obf", MappingNamespace.OBFUSCATED, "named", MappingNamespace.MOJMAP), "Synthetic fixture");
        var env = new EnvironmentId("1.21.1", Loader.FORGE, MappingNamespace.MOJMAP, 21);
        var input = Map.of("a", new ClassInfo("a", "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC,
                        List.of(new ClassInfo.Member("b", "La;", Opcodes.ACC_PUBLIC, null)),
                        List.of(new ClassInfo.Member("c", "(La;)La;", Opcodes.ACC_PUBLIC, null))),
                "d", new ClassInfo("d", "a", List.of(), Opcodes.ACC_PUBLIC, List.of(), List.of()));
        var output = DeclarationNamespace.remap(input, config, env);
        assertEquals("game/Id", output.get("game/Child").superName());
        assertEquals("value", output.get("game/Id").fields().get(0).name());
        assertEquals("Lgame/Id;", output.get("game/Id").fields().get(0).descriptor());
        assertEquals("copy", output.get("game/Id").methods().get(0).name());
        assertEquals("(Lgame/Id;)Lgame/Id;", output.get("game/Id").methods().get(0).descriptor());
        Files.writeString(file, "mutated");
        assertThrows(java.io.IOException.class, () -> DeclarationNamespace.remap(input, config, env));
    }
    @Test void indexesActual1710BlocksItemsAndEntitiesIncludingAllTheirDeclaredMembers() throws Exception {
        String root = System.getProperty("continuumlib.minecraftArtifacts");
        org.junit.jupiter.api.Assumptions.assumeTrue(root != null, "Supply -PminecraftArtifacts=ref/artifacts");
        Path jar = Path.of(root, "minecraft-1.7.10-server.jar");
        assertEquals("c70870f00c4024d829e154f7e5f4e885b02dd87991726a3308d81f513972f3fc",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))));
        var input = ArtifactIndex.read(List.of(jar)).classes();
        var config = new MappingRequest(Path.of(root, "mcp-1.7.10-joined.srg"), "43d4376110b2638213d3096f80f8395c076eb05b58d6c0dbd22c0d999b37a357",
                MappingNamespace.OBFUSCATED, Map.of("source", MappingNamespace.OBFUSCATED, "target", MappingNamespace.SRG), "MCP mappings; local verification only");
        var output = DeclarationNamespace.remap(input, config, new EnvironmentId("1.7.10", Loader.VANILLA, MappingNamespace.SRG, 8));
        assertEquals(input.size(), output.size());
        assertEquals(input.values().stream().mapToInt(c -> c.methods().size()).sum(), output.values().stream().mapToInt(c -> c.methods().size()).sum());
        assertEquals(input.values().stream().mapToInt(c -> c.fields().size()).sum(), output.values().stream().mapToInt(c -> c.fields().size()).sum());
        for (String type : List.of("net/minecraft/block/Block", "net/minecraft/item/Item", "net/minecraft/entity/Entity")) {
            assertNotNull(output.get(type), type);
            assertTrue(output.get(type).methods().size() > 20, type);
        }
        System.out.printf("Legacy 1.7.10 API: %d classes, %d methods/constructors, %d fields%n", output.size(),
                output.values().stream().mapToInt(c -> c.methods().size()).sum(), output.values().stream().mapToInt(c -> c.fields().size()).sum());
    }
}
