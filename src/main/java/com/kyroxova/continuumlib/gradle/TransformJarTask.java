package com.kyroxova.continuumlib.gradle;

import com.kyroxova.continuumlib.artifact.JarTransformer;
import com.kyroxova.continuumlib.knowledge.rule.*;
import com.kyroxova.continuumlib.bytecode.ArtifactIndex;
import com.kyroxova.continuumlib.bytecode.ClassAdapter;
import com.kyroxova.continuumlib.bytecode.ClassInspector;
import com.kyroxova.continuumlib.resolver.RuleDeclarationVerifier;
import org.gradle.api.*;
import org.gradle.api.file.*;
import org.gradle.api.tasks.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Explicit development transform task; deliberately separate from assemble/release. */
@CacheableTask
public abstract class TransformJarTask extends ArtifactRequestTask {
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getInputJar();
    @OutputFile public abstract RegularFileProperty getOutputJar();

    @TaskAction public void transform() throws IOException {
        var request = request();
        var packs = rulePacks();
        var selected = selectedPack(packs, request);
        var plan = new RuleCatalog(packs).select(selected.source(), selected.target());
        var bound = plan.bind(request.sourceArtifacts(), request.targetArtifacts());
        var sourceApi = api(request, true);
        var targetApi = api(request, false);
        var route = packs.stream().filter(p -> p.source().equals(selected.source()) && p.target().equals(selected.target())).toList();
        var problems = new RuleDeclarationVerifier().validate(route, sourceApi, targetApi);
        if (!problems.isEmpty()) throw new GradleException("ContinuumLib rule declarations failed verification:\n" + String.join("\n", problems));
        var hierarchy = new HashMap<>(sourceApi);
        var modApi = ArtifactIndex.read(List.of(getInputJar().get().getAsFile().toPath()));
        for (var entry : modApi.classes().entrySet())
            if (hierarchy.putIfAbsent(entry.getKey(), entry.getValue()) != null)
                throw new GradleException("Mod JAR duplicates a source API class: " + entry.getKey());
        var hierarchyBound = bound.withSourceHierarchy(hierarchy);
        var namespace = outputNamespace(request, selected);
        ClassAdapter outputAdapter;
        if (namespace == null) outputAdapter = null;
        else {
            var targetHierarchy = new HashMap<>(targetApi);
            var outputApiNames = new HashSet<String>();
            targetApi.keySet().forEach(name -> outputApiNames.add(namespace.classes().getOrDefault(name, name)));
            try (var jar = new java.util.jar.JarFile(getInputJar().get().getAsFile())) {
                for (var entry : Collections.list(jar.entries())) {
                    if (!entry.getName().endsWith(".class") || entry.getName().equals("module-info.class")) continue;
                    try (var input = jar.getInputStream(entry)) {
                        var type = new ClassInspector().inspect(hierarchyBound.adapt(input.readAllBytes()));
                        String exportedName = namespace.classes().getOrDefault(type.name(), type.name());
                        if (outputApiNames.contains(exportedName))
                            throw new GradleException("Mod JAR duplicates target API class in output namespace: " + exportedName);
                        if (targetHierarchy.putIfAbsent(type.name(), type) != null)
                            throw new GradleException("Mod JAR duplicates target API class: " + type.name());
                    }
                }
            }
            outputAdapter = new ClassAdapter(namespace.classes(), namespace.members(), targetHierarchy);
        }
        Path output = getOutputJar().get().getAsFile().toPath();
        protectOutput(output, List.of(getInputJar().get().getAsFile().toPath()));
        var result = new JarTransformer().transform(getInputJar().get().getAsFile().toPath(), output,
                original -> {
                    byte[] adapted = hierarchyBound.adapt(original);
                    return outputAdapter == null ? adapted : outputAdapter.adapt(adapted);
                }, original -> {
                    String adapted = hierarchyBound.mapClassName(original);
                    return namespace == null ? adapted : namespace.classes().getOrDefault(adapted, adapted);
                });
        getLogger().lifecycle("ContinuumLib transformed {} classes and copied {} resources. NOT_CERTIFIED: target linkage, resources and gameplay still require verification. Output: {}",
                result.classes(), result.resources(), output);
    }
}
