package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.continuumlib.api.config.MappingRequest;
import com.kyroxova.continuumlib.bytecode.MemberReference;
import com.kyroxova.continuumlib.bytecode.ReferenceScanner;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.Loader;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class NamespaceExporterTest {
    @Test
    void exportsCompiledReferencesIntoRequestedNamespace(@TempDir Path tempDir) throws Exception {
        Path api = tempDir.resolve("api.jar");
        Path mod = tempDir.resolve("mod.jar");
        Path mappings = tempDir.resolve("mappings.tiny");

        writeApiJar(api);
        writeCallerJar(mod);
        Files.writeString(mappings, """
                tiny	2	0	official	intermediary
                c	api/Thing	net/minecraft/class_1
                	m	(I)I	oldMethod	method_1
                """);

        MappingRequest mapping = new MappingRequest(
                mappings,
                sha256(mappings),
                MappingNamespace.OFFICIAL,
                Map.of(
                        "official", MappingNamespace.OFFICIAL,
                        "intermediary", MappingNamespace.INTERMEDIARY
                ),
                "test-fixture"
        );

        EnvironmentId environment = new EnvironmentId(
                "1.20.1",
                Loader.FABRIC,
                MappingNamespace.OFFICIAL,
                17
        );

        ResolvedTarget target = ResolvedTarget.builder()
                .targetId("fabric-test")
                .sourceEnvironment(environment)
                .targetEnvironment(environment)
                .targetArtifacts(Map.of("game", api))
                .targetMapping(mapping)
                .outputNamespace(MappingNamespace.INTERMEDIARY)
                .workspace(new GeneratedWorkspace(tempDir.resolve("build"), "fabric-test"))
                .build();

        var result = new NamespaceExporter().export(mod, target);

        assertEquals(1, result.adaptedClasses());

        var uses = new ReferenceScanner().scan(readClass(mod, "example/Caller.class"));
        MemberReference mapped = new MemberReference(
                "net/minecraft/class_1",
                "method_1",
                "(I)I"
        );
        assertTrue(uses.stream().anyMatch(use -> use.target().equals(mapped)));
        assertFalse(uses.stream().anyMatch(use -> use.target().owner().equals("api/Thing")));

        var outputApi = new TargetApiResolver().forNamespace(
                target,
                MappingNamespace.INTERMEDIARY
        );
        assertTrue(outputApi.containsKey("net/minecraft/class_1"));
        assertTrue(outputApi.get("net/minecraft/class_1").methods().stream()
                .anyMatch(method -> method.name().equals("method_1") && method.descriptor().equals("(I)I")));
    }

    private static void writeApiJar(Path jar) throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "api/Thing", null, "java/lang/Object", null);
        var method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "oldMethod",
                "(I)I",
                null,
                null
        );
        method.visitCode();
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();

        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("api/Thing.class"));
            output.write(writer.toByteArray());
            output.closeEntry();
        }
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
                "api/Thing",
                "oldMethod",
                "(I)I",
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

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))
        );
    }
}
