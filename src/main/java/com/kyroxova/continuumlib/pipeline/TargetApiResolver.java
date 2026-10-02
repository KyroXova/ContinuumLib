package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.continuumlib.bytecode.ArtifactIndex;
import com.kyroxova.continuumlib.bytecode.ClassInfo;
import com.kyroxova.continuumlib.knowledge.mapping.DeclarationNamespace;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class TargetApiResolver {
    public Map<String, ClassInfo> raw(ResolvedTarget target) throws IOException {
        List<Path> paths = new ArrayList<>(target.targetArtifacts().values());
        for (var dependency : new TreeMap<>(target.targetClasspath()).values()) {
            dependency.verify();
            paths.add(dependency.file());
        }
        return ArtifactIndex.read(paths.stream().sorted().toList()).classes();
    }

    public Map<String, ClassInfo> forNamespace(ResolvedTarget target, MappingNamespace namespace) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(namespace, "namespace");

        Map<String, ClassInfo> raw = raw(target);
        var mapping = target.targetMapping();

        if (mapping == null) {
            if (namespace != target.targetEnvironment().mappings()) {
                throw new IOException("Output namespace " + namespace + " requires explicit target mappings");
            }
            return raw;
        }

        if (mapping.from() == namespace) {
            return raw;
        }
        if (!mapping.namespaces().containsValue(namespace)) {
            throw new IOException("Target mapping does not contain namespace " + namespace);
        }

        EnvironmentId environment = new EnvironmentId(
                target.targetEnvironment().minecraftVersion(),
                target.targetEnvironment().loader(),
                namespace,
                target.targetEnvironment().javaVersion()
        );
        return DeclarationNamespace.remap(raw, mapping, environment);
    }
}
