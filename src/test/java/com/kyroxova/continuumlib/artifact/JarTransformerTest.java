package com.kyroxova.continuumlib.artifact;

import com.kyroxova.continuumlib.bytecode.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;

class JarTransformerTest {
    @TempDir Path dir;
    public static class Api { public static int oldCall(int x) { return x * 2; } public static int newCall(int x) { return x * 3; } }
    public static class Mod { public int call() { return Api.oldCall(4) + 7; } }
    private byte[] bytes(Class<?> type) throws Exception {
        try (var in = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) { return in.readAllBytes(); }
    }
    private Path jar(String name, Map<String, byte[]> entries) throws Exception {
        Path path = dir.resolve(name);
        try (var out = new JarOutputStream(Files.newOutputStream(path))) {
            for (var entry : entries.entrySet()) {
                out.putNextEntry(new JarEntry(entry.getKey())); out.write(entry.getValue()); out.closeEntry();
            }
        }
        return path;
    }
    @Test void transformedJarExecutesAndPreservesResourcesAndOriginal() throws Exception {
        String api = Api.class.getName().replace('.', '/');
        String originalName = Mod.class.getName().replace('.', '/');
        Path input = jar("input.jar", Map.of(originalName + ".class", bytes(Mod.class), "assets/mod/data.bin", new byte[]{0, 1, -1}));
        byte[] original = Files.readAllBytes(input);
        var adapter = new ClassAdapter(Map.of(originalName, "fixture/AdaptedMod"), Map.of(
                new MemberReference(api, "oldCall", "(I)I"), new MemberReference(api, "newCall", "(I)I")));
        Path output = dir.resolve("out.jar"), second = dir.resolve("second.jar");
        var result = new JarTransformer().transform(input, output, adapter::adapt);
        new JarTransformer().transform(input, second, adapter::adapt);
        assertEquals(1, result.classes()); assertEquals(1, result.resources());
        assertArrayEquals(original, Files.readAllBytes(input));
        assertArrayEquals(Files.readAllBytes(output), Files.readAllBytes(second));
        try (var jar = new JarFile(output.toFile())) {
            assertNull(jar.getEntry(originalName + ".class"));
            assertArrayEquals(new byte[]{0, 1, -1}, jar.getInputStream(jar.getJarEntry("assets/mod/data.bin")).readAllBytes());
        }
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, getClass().getClassLoader())) {
            var type = loader.loadClass("fixture.AdaptedMod");
            assertEquals(19, type.getMethod("call").invoke(type.getConstructor().newInstance()));
        }
    }
    @Test void failedTransformPreservesExistingOutputAndRejectsInPlaceWrites() throws Exception {
        String name = Mod.class.getName().replace('.', '/');
        Path input = jar("input.jar", Map.of(name + ".class", bytes(Mod.class)));
        Path output = Files.write(dir.resolve("out.jar"), new byte[]{9});
        assertThrows(IllegalStateException.class, () -> new JarTransformer().transform(input, output, b -> { throw new IllegalStateException("Unresolved API"); }));
        assertArrayEquals(new byte[]{9}, Files.readAllBytes(output));
        assertThrows(java.io.IOException.class, () -> new JarTransformer().transform(input, input, b -> b));
    }
    @Test void rejectsSignaturesUnsafePathsAndClassCollisions() throws Exception {
        Path signed = jar("signed.jar", Map.of("META-INF/SIGNATURE.SF", new byte[]{1}));
        assertThrows(java.io.IOException.class, () -> new JarTransformer().transform(signed, dir.resolve("out.jar"), b -> b));
        Path unsafe = jar("unsafe.jar", Map.of("../escape", new byte[]{1}));
        assertThrows(java.io.IOException.class, () -> new JarTransformer().transform(unsafe, dir.resolve("out.jar"), b -> b));
        String one = Mod.class.getName().replace('.', '/'), two = Api.class.getName().replace('.', '/');
        Path collision = jar("collision.jar", Map.of(one + ".class", bytes(Mod.class), two + ".class", bytes(Api.class)));
        var rename = new ClassAdapter(Map.of(one, two), Map.of());
        assertThrows(java.io.IOException.class, () -> new JarTransformer().transform(collision, dir.resolve("out.jar"), rename::adapt));
        assertFalse(Files.exists(dir.resolve("out.jar")));
    }
    @Test void rejectsMethodCollisionsWithinOneClass() throws Exception {
        String api = Api.class.getName().replace('.', '/');
        Path input = jar("input.jar", Map.of(api + ".class", bytes(Api.class)));
        var adapter = new ClassAdapter(Map.of(), Map.of(new MemberReference(api, "oldCall", "(I)I"), new MemberReference(api, "newCall", "(I)I")));
        assertThrows(java.io.IOException.class, () -> new JarTransformer().transform(input, dir.resolve("output.jar"), adapter::adapt));
        assertFalse(Files.exists(dir.resolve("output.jar")));
    }
}
