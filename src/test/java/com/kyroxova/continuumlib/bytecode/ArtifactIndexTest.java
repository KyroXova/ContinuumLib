package com.kyroxova.continuumlib.bytecode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;

class ArtifactIndexTest {
    @TempDir Path temp;
    @Test void indexesExplicitTargetJmodWithoutTreatingItsModuleDescriptorAsAClass() throws Exception {
        Path runtime = Path.of(System.getProperty("java.home"), "jmods", "java.base.jmod");
        assertTrue(Files.isRegularFile(runtime), "Tests require a full JDK");
        var index = ArtifactIndex.read(List.of(runtime));
        assertFalse(index.classes().containsKey("module-info"));
        assertTrue(index.declaredMethod(new MemberReference("java/lang/Object", "<init>", "()V")).isPresent());
        assertTrue(index.declaredMethod(new MemberReference("java/lang/String", "length", "()I")).isPresent());
    }
    @Test void rejectsClassWhoseArchivePathCannotBeLoadedAsItsDeclaredName() throws Exception {
        Path artifact = temp.resolve("bad-name.jar");
        try (var jar = new JarOutputStream(Files.newOutputStream(artifact));
             var bytes = Object.class.getResourceAsStream("/java/lang/Object.class")) {
            jar.putNextEntry(new JarEntry("wrong/Name.class")); bytes.transferTo(jar); jar.closeEntry();
        }
        assertThrows(java.io.IOException.class, () -> ArtifactIndex.read(List.of(artifact)));
    }
    @Test void indexesActualJarAndRejectsClasspathCollisions() throws Exception {
        Path artifact = temp.resolve("fixture.jar");
        String name = ClassAdaptationTest.ConnectedBlock.class.getName().replace('.', '/');
        try (var jar = new JarOutputStream(Files.newOutputStream(artifact));
             var bytes = getClass().getResourceAsStream("/" + name + ".class")) {
            jar.putNextEntry(new JarEntry(name + ".class"));
            jar.write(bytes.readAllBytes());
            jar.closeEntry();
        }
        var index = ArtifactIndex.read(List.of(artifact));
        assertEquals(1, index.classes().size());
        assertTrue(index.declaredMethod(new MemberReference(name, "shape", "(I)I")).isPresent());
        assertTrue(index.declaredMethod(new MemberReference(name, "shape", "(D)I")).isEmpty());
        assertThrows(java.io.IOException.class, () -> ArtifactIndex.read(List.of(artifact, artifact)));
    }
    @Test void inspectsLocalMinecraftArtifactWhenExplicitlySupplied() throws Exception {
        String artifact = System.getProperty("continuumlib.testArtifact");
        org.junit.jupiter.api.Assumptions.assumeTrue(artifact != null, "Optional real artifact inspection");
        var index = ArtifactIndex.read(List.of(Path.of(artifact)));
        assertTrue(index.classes().size() > 1000);
        long methods = index.classes().values().stream().mapToLong(c -> c.methods().size()).sum();
        System.out.println("Inspected artifact: " + index.classes().size() + " classes, " + methods + " methods/constructors");
    }
}
