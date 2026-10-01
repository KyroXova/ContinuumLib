package com.kyroxova.continuumlib.bytecode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

/** Complete declared API surface of explicitly supplied classpath artifacts. */
public final class ArtifactIndex {
    private final Map<String, ClassInfo> classes;
    private ArtifactIndex(Map<String, ClassInfo> classes) { this.classes = Map.copyOf(classes); }
    public Map<String, ClassInfo> classes() { return classes; }

    public static ArtifactIndex read(List<Path> artifacts) throws IOException {
        Map<String, ClassInfo> classes = new TreeMap<>();
        ClassInspector inspector = new ClassInspector();
        for (Path path : artifacts) {
            try (JarFile jar = new JarFile(path.toFile())) {
                if (jar.isMultiRelease()) {
                    throw new IOException("Multi-release artifact needs an explicit runtime selection: " + path);
                }
                for (var entry : Collections.list(jar.entries())) {
                    if (!entry.getName().endsWith(".class") || entry.getName().equals("module-info.class")) continue;
                    try (var input = jar.getInputStream(entry)) {
                        ClassInfo info = inspector.inspect(input.readAllBytes());
                        if (classes.putIfAbsent(info.name(), info) != null) {
                            throw new IOException("Duplicate class " + info.name() + " in " + path);
                        }
                    } catch (IllegalArgumentException ex) {
                        throw new IOException("Cannot inspect " + path + "!" + entry.getName(), ex);
                    }
                }
            }
        }
        return new ArtifactIndex(classes);
    }

    /** Declared members only; callers must not confuse this with full JVM linkage validation. */
    public Optional<ClassInfo.Member> declaredMethod(MemberReference reference) {
        ClassInfo owner = classes.get(reference.owner());
        if (owner == null) return Optional.empty();
        return owner.methods().stream().filter(m -> m.name().equals(reference.name())
                && m.descriptor().equals(reference.descriptor())).findFirst();
    }
}
