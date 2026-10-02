package com.kyroxova.continuumlib.source;

import com.kyroxova.continuumlib.source.compile.TargetJarPackager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

class TargetJarPackagerTest {
    @Test
    void resourceMapPackagingIsDeterministic(@TempDir Path root) throws Exception {
        Path classes = root.resolve("classes");
        Files.createDirectories(classes.resolve("example"));
        Files.write(classes.resolve("example/Example.class"), new byte[]{1, 2, 3});

        Path a = root.resolve("a.json");
        Path z = root.resolve("z.json");
        Files.writeString(a, "{\"a\":1}");
        Files.writeString(z, "{\"z\":1}");

        Map<String, Path> firstOrder = new HashMap<>();
        firstOrder.put("assets/example/z.json", z);
        firstOrder.put("assets/example/a.json", a);

        Map<String, Path> secondOrder = new HashMap<>();
        secondOrder.put("assets/example/a.json", a);
        secondOrder.put("assets/example/z.json", z);

        Path first = root.resolve("first.jar");
        Path second = root.resolve("second.jar");
        TargetJarPackager.packageJarWithResources(classes, firstOrder, first);
        TargetJarPackager.packageJarWithResources(classes, secondOrder, second);

        assertArrayEquals(sha256(first), sha256(second));

        try (JarFile jar = new JarFile(first.toFile())) {
            assertEquals(0L, jar.getJarEntry("example/Example.class").getTime());
            assertEquals(0L, jar.getJarEntry("assets/example/a.json").getTime());
        }
    }

    @Test
    void preservesConfiguredManifestAttributes(@TempDir Path root) throws Exception {
        Path classes = root.resolve("classes");
        Files.createDirectories(classes.resolve("example"));
        Files.write(classes.resolve("example/Example.class"), new byte[]{1, 2, 3});

        Path resourceManifest = root.resolve("resource-manifest.mf");
        Files.writeString(resourceManifest, "Manifest-Version: 1.0\nIgnored: true\n\n");

        Path output = root.resolve("manifest.jar");
        TargetJarPackager.packageJarWithResources(
                classes,
                Map.of("META-INF/MANIFEST.MF", resourceManifest),
                Map.of(
                        "MixinConfigs", "example.mixins.json",
                        "FMLAT", "accesstransformer.cfg"
                ),
                output
        );

        try (JarFile jar = new JarFile(output.toFile())) {
            assertEquals("example.mixins.json",
                    jar.getManifest().getMainAttributes().getValue("MixinConfigs"));
            assertEquals("accesstransformer.cfg",
                    jar.getManifest().getMainAttributes().getValue("FMLAT"));
            assertNull(jar.getManifest().getMainAttributes().getValue("Ignored"));
            assertEquals(0L, jar.getJarEntry("META-INF/MANIFEST.MF").getTime());
        }
    }

    @Test
    void failedPackagingRemovesTemporaryJar(@TempDir Path root) throws Exception {
        Path classes = root.resolve("classes");
        Files.createDirectories(classes);
        Path output = root.resolve("broken.jar");

        IOException failure = assertThrows(IOException.class, () ->
                TargetJarPackager.packageJarWithResources(
                        classes,
                        Map.of("assets/example/missing.json", root.resolve("missing.json")),
                        output
                )
        );

        assertNotNull(failure.getMessage());
        assertFalse(Files.exists(root.resolve("broken.jar.tmp")));
        assertFalse(Files.exists(output));
    }

    @Test
    void rejectsTraversalResourceEntry(@TempDir Path root) throws Exception {
        Path resource = root.resolve("value.json");
        Files.writeString(resource, "{}");

        assertThrows(IOException.class, () ->
                TargetJarPackager.packageJarWithResources(
                        root.resolve("classes"),
                        Map.of("../escape.json", resource),
                        root.resolve("out.jar")
                )
        );
        assertFalse(Files.exists(root.resolve("out.jar.tmp")));
    }

    private static byte[] sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(Files.readAllBytes(file));
        return digest.digest();
    }
}
