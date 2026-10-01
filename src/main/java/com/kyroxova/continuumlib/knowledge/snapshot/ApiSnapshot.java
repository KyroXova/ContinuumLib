package com.kyroxova.continuumlib.knowledge.snapshot;

import com.kyroxova.continuumlib.bytecode.MemberReference;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import java.util.*;

public record ApiSnapshot(
        EnvironmentId environment,
        Map<String, String> artifactManifest,
        Map<String, ClassSnapshot> classes
) {
    public ApiSnapshot {
        Objects.requireNonNull(environment, "environment");
        artifactManifest = Collections.unmodifiableMap(new TreeMap<>(artifactManifest));
        classes = Collections.unmodifiableMap(new TreeMap<>(classes));
    }

    public Optional<ClassSnapshot> findClass(String name) {
        return Optional.ofNullable(classes.get(name));
    }

    public Optional<MemberSnapshot> findMember(MemberReference reference) {
        var cls = classes.get(reference.owner());
        if (cls == null) return Optional.empty();
        String key = reference.name() + ":" + reference.descriptor();
        if (reference.name().equals("<init>")) {
            return Optional.ofNullable(cls.constructors().get(key));
        }
        var m = cls.methods().get(key);
        if (m != null) return Optional.of(m);
        return Optional.ofNullable(cls.fields().get(key));
    }

    public int totalClasses() { return classes.size(); }
    public int totalMethods() { return classes.values().stream().mapToInt(c -> c.methods().size()).sum(); }
    public int totalFields() { return classes.values().stream().mapToInt(c -> c.fields().size()).sum(); }
    public int totalConstructors() { return classes.values().stream().mapToInt(c -> c.constructors().size()).sum(); }
}
