package com.kyroxova.continuumlib.artifact;

import com.kyroxova.continuumlib.bytecode.*;
import com.kyroxova.continuumlib.knowledge.rule.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;

/** Optional real-reference transformation check, not target-game startup certification. */
class ExternalModReferenceTest {
    @TempDir Path temporary;
    @Test void transformsExternalCompiledModWithoutChangingItsDeclaredCustomApi() throws Exception {
        String classesPath = System.getProperty("continuumlib.referenceClasses");
        String artifactsPath = System.getProperty("continuumlib.forgeArtifacts");
        Assumptions.assumeTrue(classesPath != null && artifactsPath != null,
                "Provide referenceClasses for a compiled Forge 1.18.2 mod and forgeArtifacts for the pinned API pair");
        Path root = Path.of(classesPath);
        var pack = BuiltinRulePacks.load().stream().filter(p -> p.id().equals("forge-network-1.18.2-to-1.19.2")).findFirst().orElseThrow();
        var bound = new RuleCatalog(List.of(pack)).select(pack.source(), pack.target()).bind(
                Map.of("forge", Path.of(artifactsPath, "forge-1.18.2-40.3.12-universal.jar")),
                Map.of("forge", Path.of(artifactsPath, "forge-1.19.2-43.5.1-universal.jar")));
        List<Path> files;
        try (var paths = Files.walk(root)) { files = paths.filter(p -> p.toString().endsWith(".class")).sorted().toList(); }
        assertFalse(files.isEmpty(), "Reference classes directory must contain compiled classes");
        Path input = temporary.resolve("reference-classes.jar"), output = temporary.resolve("reference-transformed-classes.jar");
        try (var jar = new JarOutputStream(Files.newOutputStream(input))) {
            for (Path file : files) {
                jar.putNextEntry(new JarEntry(root.relativize(file).toString().replace('\\', '/')));
                jar.write(Files.readAllBytes(file)); jar.closeEntry();
            }
        }
        var hierarchy = new HashMap<>(ArtifactIndex.read(List.of(input)).classes());
        hierarchy.putAll(ArtifactIndex.read(List.of(Path.of(artifactsPath, "forge-1.18.2-40.3.12-universal.jar"))).classes());
        var hierarchyBound = bound.withSourceHierarchy(hierarchy);
        var transformed = new JarTransformer().transform(input, output, hierarchyBound::adapt, hierarchyBound::mapClassName);
        assertEquals(files.size(), transformed.classes());
        int affectedClasses = 0, calls = 0, totalUses = 0;
        var scanner = new ReferenceScanner(); var inspector = new ClassInspector();
        try (var jar = new JarFile(output.toFile())) {
            for (Path file : files) {
                byte[] before = Files.readAllBytes(file), after;
                try (var stream = jar.getInputStream(jar.getJarEntry(root.relativize(file).toString().replace('\\', '/')))) { after = stream.readAllBytes(); }
                assertEquals(inspector.inspect(before), inspector.inspect(after), "Custom declared API must remain intact: " + file);
                var original = scanner.scan(before); var adapted = scanner.scan(after);
                totalUses += original.size();
                long changed = original.stream().filter(use -> pack.members().containsKey(use.target())).count();
                if (changed > 0) { affectedClasses++; calls += (int) changed; }
                var expected = original.stream().map(use -> pack.members().getOrDefault(use.target(), use.target())).toList();
                assertEquals(expected, adapted.stream().map(ReferenceScanner.Use::target).toList(), "Only specified references may change: " + file);
            }
        }
        System.out.printf("External mod reference: %d classes, %d member uses, %d rewritten calls across %d classes. NOT_GAMEPLAY_CERTIFIED%n",
                files.size(), totalUses, calls, affectedClasses);
    }
}
