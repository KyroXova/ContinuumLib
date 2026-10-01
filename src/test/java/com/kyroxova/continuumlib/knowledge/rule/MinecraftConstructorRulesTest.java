package com.kyroxova.continuumlib.knowledge.rule;

import com.kyroxova.continuumlib.api.config.MappingRequest;
import com.kyroxova.continuumlib.bytecode.*;
import com.kyroxova.continuumlib.knowledge.mapping.DeclarationNamespace;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;
import com.kyroxova.continuumlib.resolver.RuleDeclarationVerifier;
import org.junit.jupiter.api.*;
import org.objectweb.asm.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MinecraftConstructorRulesTest {
    static final String ID = "net/minecraft/resources/ResourceLocation";
    @Test void replacesBothResourceLocationConstructorsAndSeparatorMethodBeyond1182() throws Exception {
        var pack = BuiltinRulePacks.load().stream().filter(p -> p.id().equals("vanilla-identifiers-1.20.1-to-1.21.1")).findFirst().orElseThrow();
        var adapter = new ConstructorFactory(pack.constructors());
        for (var rule : pack.constructors()) {
            var writer = new ClassWriter(0);
            writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "example/Identifiers", null, "java/lang/Object", null);
            var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "id", "()L" + ID + ";", null, null);
            method.visitCode(); method.visitTypeInsn(Opcodes.NEW, ID); method.visitInsn(Opcodes.DUP);
            if (Type.getArgumentTypes(rule.source().descriptor()).length == 2) method.visitLdcInsn("example");
            method.visitLdcInsn("path"); method.visitMethodInsn(Opcodes.INVOKESPECIAL, ID, "<init>", rule.source().descriptor(), false);
            method.visitInsn(Opcodes.ARETURN); method.visitMaxs(4, 0); method.visitEnd(); writer.visitEnd();
            var references = new ReferenceScanner().scan(adapter.adapt(writer.toByteArray()));
            assertEquals(List.of(rule.factory()), references.stream().map(ReferenceScanner.Use::target).toList());
        }
        assertEquals("bySeparator", pack.members().get(new MemberReference(ID, "of", "(Ljava/lang/String;C)L" + ID + ";")).name());
    }
    @Test void validatesEveryRuleAgainstOfficialMinecraftArtifactsWhenSupplied() throws Exception {
        String directory = System.getProperty("continuumlib.minecraftArtifacts");
        Assumptions.assumeTrue(directory != null, "Supply -PminecraftArtifacts=ref/artifacts");
        var pack = BuiltinRulePacks.load().stream().filter(p -> p.id().equals("vanilla-identifiers-1.20.1-to-1.21.1")).findFirst().orElseThrow();
        var sourceJar = Path.of(directory, "minecraft-1.20.1-server-extracted.jar");
        var targetJar = Path.of(directory, "minecraft-1.21.1-server-extracted.jar");
        var sourceMapping = mapping(directory, "1.20.1", "dca153d20defb32cfac3f069c3bf77b3e13c30ae63847637479d3137e099bb72");
        var targetMapping = mapping(directory, "1.21.1", "9d0b04bead421c8229aff14b534432bbc927bea642e7c8593d1276b8df8ba53f");
        var source = DeclarationNamespace.remap(ArtifactIndex.read(List.of(sourceJar)).classes(), sourceMapping, pack.source());
        var target = DeclarationNamespace.remap(ArtifactIndex.read(List.of(targetJar)).classes(), targetMapping, pack.target());
        assertEquals(List.of(), new RuleDeclarationVerifier().validate(List.of(pack), source, target));
        new RuleCatalog(List.of(pack)).select(pack.source(), pack.target()).bind(Map.of("minecraft", sourceJar), Map.of("minecraft", targetJar));
        assertTrue(target.get(ID).methods().stream().anyMatch(m -> m.name().equals("<init>") && (m.access() & Opcodes.ACC_PRIVATE) != 0));
        for (var rule : pack.constructors()) assertTrue(source.get(ID).methods().stream().anyMatch(m -> m.name().equals("<init>")
                && m.descriptor().equals(rule.source().descriptor()) && (m.access() & Opcodes.ACC_PUBLIC) != 0));
    }
    static MappingRequest mapping(String directory, String version, String hash) {
        return new MappingRequest(Path.of(directory, "minecraft-" + version + "-server_mappings.txt"), hash, MappingNamespace.OBFUSCATED,
                Map.of("source", MappingNamespace.MOJMAP, "target", MappingNamespace.OBFUSCATED), "Mojang mappings license; local verification only");
    }
    @Test void checksEveryPublicIdentifierMethodAndFieldAgainst263NotOnlyTheClassName() throws Exception {
        String directory = System.getProperty("continuumlib.minecraftArtifacts");
        String targetPath = System.getProperty("continuumlib.testArtifact");
        Assumptions.assumeTrue(directory != null && targetPath != null, "Supply Minecraft artifacts and the 26.3 inspection artifact");
        var pack = BuiltinRulePacks.load().stream().filter(p -> p.id().equals("vanilla-identifiers-1.21.1-to-26.3")).findFirst().orElseThrow();
        var sourceJar = Path.of(directory, "minecraft-1.21.1-server-extracted.jar");
        var source = DeclarationNamespace.remap(ArtifactIndex.read(List.of(sourceJar)).classes(),
                mapping(directory, "1.21.1", "9d0b04bead421c8229aff14b534432bbc927bea642e7c8593d1276b8df8ba53f"), pack.source());
        var target = ArtifactIndex.read(List.of(Path.of(targetPath))).classes();
        assertEquals(List.of(), new RuleDeclarationVerifier().validate(List.of(pack), source, target));
        new RuleCatalog(List.of(pack)).select(pack.source(), pack.target()).bind(Map.of("minecraft", sourceJar), Map.of("minecraft", Path.of(targetPath)));
        var adapter = new ClassAdapter(pack.classes(), pack.members());
        int checked = 0;
        for (var member : source.get(ID).methods()) {
            if ((member.access() & Opcodes.ACC_PUBLIC) == 0 || member.name().startsWith("<")) continue;
            var writer = new ClassWriter(0);
            writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "example/Calls", null, "java/lang/Object", null);
            var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "call", "()V", null, null);
            method.visitMethodInsn((member.access() & Opcodes.ACC_STATIC) != 0 ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL,
                    ID, member.name(), member.descriptor(), false);
            method.visitInsn(Opcodes.RETURN); method.visitMaxs(10, 0); method.visitEnd(); writer.visitEnd();
            var use = new ReferenceScanner().scan(adapter.adapt(writer.toByteArray())).get(0);
            assertEquals(TargetReferenceAudit.Status.DECLARATION_FOUND, new TargetReferenceAudit(target).check(use).status(), member.toString());
            checked++;
        }
        var fields = source.get(ID).fields().stream().filter(field -> (field.access() & Opcodes.ACC_PUBLIC) != 0).toList();
        var destination = target.get("net/minecraft/resources/Identifier");
        for (var field : fields) assertTrue(destination.fields().stream().anyMatch(f -> f.name().equals(field.name()) && f.descriptor().equals(field.descriptor())
                && (f.access() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == (field.access() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC))), field.toString());
        assertEquals(30, checked);
        assertEquals(6, fields.size());
    }
}
