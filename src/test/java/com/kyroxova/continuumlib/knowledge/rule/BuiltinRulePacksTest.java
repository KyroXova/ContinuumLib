package com.kyroxova.continuumlib.knowledge.rule;

import com.kyroxova.continuumlib.bytecode.*;
import org.junit.jupiter.api.*;
import org.objectweb.asm.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BuiltinRulePacksTest {
    @Test void includesAllThreeForgeScreenOpeningOverloads() throws Exception {
        var packs = BuiltinRulePacks.load();
        var pack = packs.stream().filter(p -> p.id().equals("forge-network-1.18.2-to-1.19.2")).findFirst().orElseThrow();
        assertEquals(3, pack.members().size());
        for (var rule : pack.members().entrySet()) {
            assertEquals("openGui", rule.getKey().name());
            assertEquals("openScreen", rule.getValue().name());
            assertEquals(rule.getKey().descriptor(), rule.getValue().descriptor());
        }
    }
    @Test void verifiesBundledRuleAgainstRealForgeArtifactsWhenSupplied() throws Exception {
        String root = System.getProperty("continuumlib.forgeArtifacts");
        Assumptions.assumeTrue(root != null, "Supply -PforgeArtifacts=ref/artifacts for real Forge checks");
        var pack = BuiltinRulePacks.load().stream().filter(p -> p.id().equals("forge-network-1.18.2-to-1.19.2")).findFirst().orElseThrow();
        Path source = Path.of(root, "forge-1.18.2-40.3.12-universal.jar");
        Path target = Path.of(root, "forge-1.19.2-43.5.1-universal.jar");
        var sourceIndex = ArtifactIndex.read(List.of(source));
        var targetIndex = ArtifactIndex.read(List.of(target));
        assertTrue(new com.kyroxova.continuumlib.resolver.RuleDeclarationVerifier()
                .validate(List.of(pack), sourceIndex.classes(), targetIndex.classes()).isEmpty());
        var bound = new RuleCatalog(List.of(pack)).select(pack.source(), pack.target())
                .bind(Map.of("forge", source), Map.of("forge", target));
        for (var rule : pack.members().entrySet()) {
            var oldMethod = sourceIndex.declaredMethod(rule.getKey()).orElseThrow();
            var newMethod = targetIndex.declaredMethod(rule.getValue()).orElseThrow();
            assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, oldMethod.access() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC));
            assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, newMethod.access() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC));
            assertTrue(targetIndex.declaredMethod(rule.getKey()).isEmpty());
            ClassWriter writer = new ClassWriter(0);
            writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "fixture/Caller", null, "java/lang/Object", null);
            var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "call", "()V", null, null);
            method.visitCode();
            int arguments = Type.getArgumentTypes(rule.getKey().descriptor()).length;
            for (int i = 0; i < arguments; i++) method.visitInsn(Opcodes.ACONST_NULL);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, rule.getKey().owner(), rule.getKey().name(), rule.getKey().descriptor(), false);
            method.visitInsn(Opcodes.RETURN); method.visitMaxs(arguments, 0); method.visitEnd(); writer.visitEnd();
            var references = new ReferenceScanner().scan(bound.adapt(writer.toByteArray()));
            assertEquals(List.of(rule.getValue()), references.stream().map(ReferenceScanner.Use::target).toList());
        }
    }
    @Test void configScreenPackRemapsEntireFactoryTypeAndItsConstructors() throws Exception {
        var pack = BuiltinRulePacks.load().stream().filter(p -> p.id().equals("forge-config-screen-1.18.2-to-1.19.2")).findFirst().orElseThrow();
        String oldFactory = "net/minecraftforge/client/ConfigGuiHandler$ConfigGuiFactory";
        String newFactory = "net/minecraftforge/client/ConfigScreenHandler$ConfigScreenFactory";
        assertEquals(newFactory, pack.classes().get(oldFactory));
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "fixture/ConfigFactory", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create", "(Ljava/util/function/Function;)L" + oldFactory + ";", null, null);
        method.visitCode(); method.visitTypeInsn(Opcodes.NEW, oldFactory); method.visitInsn(Opcodes.DUP); method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, oldFactory, "<init>", "(Ljava/util/function/Function;)V", false);
        method.visitInsn(Opcodes.ARETURN); method.visitMaxs(3, 1); method.visitEnd(); writer.visitEnd();
        byte[] adapted = new ClassAdapter(pack.classes(), pack.members()).adapt(writer.toByteArray());
        assertTrue(new ClassInspector().inspect(adapted).methods().stream().anyMatch(m -> m.descriptor().equals("(Ljava/util/function/Function;)L" + newFactory + ";")));
        assertEquals(newFactory, new ReferenceScanner().scan(adapted).get(0).target().owner());
        String root = System.getProperty("continuumlib.forgeArtifacts");
        if (root != null) {
            var source = ArtifactIndex.read(List.of(Path.of(root, "forge-1.18.2-40.3.12-universal.jar")));
            var target = ArtifactIndex.read(List.of(Path.of(root, "forge-1.19.2-43.5.1-universal.jar")));
            assertTrue(new com.kyroxova.continuumlib.resolver.RuleDeclarationVerifier().validate(List.of(pack), source.classes(), target.classes()).isEmpty());
            for (String descriptor : List.of("(Ljava/util/function/Function;)V", "(Ljava/util/function/BiFunction;)V")) {
                assertTrue(source.declaredMethod(new MemberReference(oldFactory, "<init>", descriptor)).isPresent());
                assertTrue(target.declaredMethod(new MemberReference(newFactory, "<init>", descriptor)).isPresent());
            }
        }
    }
}
