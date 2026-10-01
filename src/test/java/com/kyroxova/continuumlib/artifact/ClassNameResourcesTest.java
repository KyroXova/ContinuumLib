package com.kyroxova.continuumlib.artifact;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.jar.Manifest;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;

class ClassNameResourcesTest {
    private final Map<String, String> names = Map.of("old/Service", "next/Service", "old/Provider", "next/Provider", "old/Entry", "next/Entry");
    @Test void remapsServiceFileAndProvidersWithoutDiscardingComments() throws Exception {
        var resources = new ClassNameResources(name -> names.getOrDefault(name, name));
        byte[] contents = "# providers\r\n  old.Provider  # custom factory\r\nother.Unchanged\n".getBytes(StandardCharsets.UTF_8);
        var result = resources.adapt("META-INF/services/old.Service", contents);
        assertEquals("META-INF/services/next.Service", result.name());
        assertEquals("# providers\r\n  next.Provider  # custom factory\r\nother.Unchanged\n", new String(result.contents(), StandardCharsets.UTF_8));
    }
    @Test void remapsKnownManifestEntrypointsAndPreservesOtherAttributes() throws Exception {
        var resources = new ClassNameResources(name -> names.getOrDefault(name, name));
        var result = resources.adapt("META-INF/MANIFEST.MF", ("Manifest-Version: 1.0\r\nMain-Class: old.Entry\r\nPremain-Class: old.Entry\r\nImplementation-Title: Keep me\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        var manifest = new Manifest(new ByteArrayInputStream(result.contents()));
        assertEquals("next.Entry", manifest.getMainAttributes().getValue("Main-Class"));
        assertEquals("next.Entry", manifest.getMainAttributes().getValue("Premain-Class"));
        assertEquals("Keep me", manifest.getMainAttributes().getValue("Implementation-Title"));
    }
    @Test void unrelatedResourcesStayByteIdenticalAndMalformedServiceTextFails() throws Exception {
        var resources = new ClassNameResources(name -> names.getOrDefault(name, name));
        byte[] binary = {0, -1, 10};
        assertArrayEquals(binary, resources.adapt("assets/example/image.png", binary).contents());
        assertThrows(java.io.IOException.class, () -> resources.adapt("META-INF/services/old.Service", new byte[]{(byte) 0xc3, 0x28}));
    }
}
