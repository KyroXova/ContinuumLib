package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.continuumlib.bytecode.MemberReference;
import com.kyroxova.continuumlib.bytecode.ReferenceScanner;
import com.kyroxova.continuumlib.pipeline.migration.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class BytecodeMigrationExecutorTest {
    private static final MemberReference SOURCE =
            new MemberReference("api/Old", "compute", "(I)I");
    private static final MemberReference HOOK =
            new MemberReference("compat/Hooks", "compute", "(I)I");

    @Test
    void appliesRemainingCallBridgeAndAccountsForUse(@TempDir Path tempDir) throws Exception {
        Path jar = tempDir.resolve("mod.jar");
        writeCallerJar(jar);

        CanonicalMigrationRule rule = bridgeRule();
        var result = new BytecodeMigrationExecutor().apply(
                jar,
                new CanonicalMigrationPlan(List.of(rule)),
                List.of()
        );

        assertEquals(1, result.adaptedClasses());
        assertEquals(1, result.appliedMigrations().size());
        assertEquals(MigrationLayer.BYTECODE, result.appliedMigrations().get(0).layer());

        var uses = new ReferenceScanner().scan(readClass(jar, "example/Caller.class"));
        assertTrue(uses.stream().anyMatch(use -> use.target().equals(HOOK)));
        assertFalse(uses.stream().anyMatch(use -> use.target().equals(SOURCE)));
    }

    @Test
    void skipsBytecodeRuleAlreadyConsumedBySource(@TempDir Path tempDir) throws Exception {
        Path jar = tempDir.resolve("mod.jar");
        writeCallerJar(jar);

        CanonicalMigrationRule rule = bridgeRule();
        AppliedMigration sourceApplied = AppliedMigration.from(
                rule,
                MigrationLayer.SOURCE_AST,
                MigrationConfidence.SEMANTICALLY_RESOLVED,
                "example/Caller.java",
                10
        );

        var result = new BytecodeMigrationExecutor().apply(
                jar,
                new CanonicalMigrationPlan(List.of(rule)),
                List.of(sourceApplied)
        );

        assertEquals(0, result.adaptedClasses());
        assertTrue(result.appliedMigrations().isEmpty());

        var uses = new ReferenceScanner().scan(readClass(jar, "example/Caller.class"));
        assertTrue(uses.stream().anyMatch(use -> use.target().equals(SOURCE)));
    }

    private static CanonicalMigrationRule bridgeRule() {
        return CanonicalMigrationRule.builder()
                .rulePackId("bridge")
                .type(MigrationType.CALL_BRIDGE)
                .sourceOwner(SOURCE.owner())
                .sourceName(SOURCE.name())
                .sourceDescriptor(SOURCE.descriptor())
                .targetOwner(HOOK.owner())
                .targetName(HOOK.name())
                .targetDescriptor(HOOK.descriptor())
                .opcode(Opcodes.INVOKESTATIC)
                .layer(MigrationLayer.BYTECODE)
                .build();
    }

    private static void writeCallerJar(Path jar) throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "example/Caller", null, "java/lang/Object", null);

        var method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "run",
                "(I)I",
                null,
                null
        );
        method.visitCode();
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                SOURCE.owner(),
                SOURCE.name(),
                SOURCE.descriptor(),
                false
        );
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();

        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("example/Caller.class"));
            output.write(writer.toByteArray());
            output.closeEntry();
        }
    }

    private static byte[] readClass(Path jar, String name) throws Exception {
        try (JarFile input = new JarFile(jar.toFile())) {
            JarEntry entry = input.getJarEntry(name);
            assertNotNull(entry);
            try (var stream = input.getInputStream(entry)) {
                return stream.readAllBytes();
            }
        }
    }
}
