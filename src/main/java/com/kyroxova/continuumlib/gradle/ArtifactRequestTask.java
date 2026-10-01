package com.kyroxova.continuumlib.gradle;

import com.kyroxova.continuumlib.api.config.TransformRequest;
import com.kyroxova.continuumlib.bytecode.*;
import com.kyroxova.continuumlib.knowledge.mapping.DeclarationNamespace;
import com.kyroxova.continuumlib.knowledge.mapping.MappingIoImporter;
import com.kyroxova.continuumlib.knowledge.mapping.NamespaceRulePack;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.knowledge.mapping.MappingProvenance;
import com.kyroxova.continuumlib.knowledge.rule.*;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.*;
import org.gradle.api.tasks.*;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Stream;

/** Shared tracked artifact inputs for inspection and transformation tasks. */
public abstract class ArtifactRequestTask extends DefaultTask {
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getConfigFile();
    @Internal public abstract DirectoryProperty getProjectDirectory();
    @InputFiles @PathSensitive(PathSensitivity.RELATIVE) public abstract ConfigurableFileCollection getRuleFiles();
    @InputFiles @PathSensitive(PathSensitivity.NONE)
    public List<File> getArtifactFiles() throws IOException {
        var request = request();
        var paths = new ArrayList<Path>();
        request.sourceClasspath().values().forEach(artifact -> paths.add(artifact.file()));
        request.targetClasspath().values().forEach(artifact -> paths.add(artifact.file()));
        if (request.sourceMapping() != null) paths.add(request.sourceMapping().file());
        if (request.targetMapping() != null) paths.add(request.targetMapping().file());
        return Stream.concat(Stream.concat(request.sourceArtifacts().values().stream(), request.targetArtifacts().values().stream()), paths.stream())
                .map(Path::toFile).distinct().sorted(Comparator.comparing(File::getAbsolutePath)).toList();
    }
    protected TransformRequest request() throws IOException {
        return TransformRequest.read(getConfigFile().get().getAsFile().toPath(), getProjectDirectory().get().getAsFile().toPath());
    }
    protected void protectOutput(Path output, Collection<Path> additionalInputs) throws IOException {
        var inputs = new ArrayList<>(additionalInputs);
        getArtifactFiles().forEach(file -> inputs.add(file.toPath()));
        getRuleFiles().forEach(file -> inputs.add(file.toPath()));
        inputs.add(getConfigFile().get().getAsFile().toPath());
        for (Path input : inputs) {
            if (output.toAbsolutePath().normalize().equals(input.toAbsolutePath().normalize())
                    || (Files.exists(output) && Files.exists(input) && Files.isSameFile(output, input)))
                throw new org.gradle.api.GradleException("Output must not overwrite an input artifact, mapping, rule or configuration");
        }
        if (Files.isSymbolicLink(output)) throw new org.gradle.api.GradleException("Output must not be a symbolic link");
    }
    protected List<RulePack> rulePacks() throws IOException {
        var packs = new ArrayList<>(BuiltinRulePacks.load());
        for (File file : getRuleFiles().getFiles().stream().sorted(Comparator.comparing(File::getAbsolutePath)).toList())
            try (var input = java.nio.file.Files.newInputStream(file.toPath())) { packs.add(new RulePackReader().read(input)); }
        new RuleCatalog(packs); // Reject duplicate IDs rather than choosing whichever was loaded first.
        return List.copyOf(packs);
    }
    protected RulePack selectedPack(List<RulePack> packs, TransformRequest request) {
        return packs.stream().filter(p -> p.id().equals(request.packId())).findFirst()
                .orElseThrow(() -> new org.gradle.api.GradleException("Unknown ContinuumLib pack: " + request.packId()));
    }
    protected Map<String, ClassInfo> api(TransformRequest request, boolean source) throws IOException {
        var mapping = source ? request.sourceMapping() : request.targetMapping();
        var classes = rawApi(request, source);
        if (mapping == null) return classes;
        var pack = selectedPack(rulePacks(), request);
        return DeclarationNamespace.remap(classes, mapping, source ? pack.source() : pack.target());
    }
    private Map<String, ClassInfo> rawApi(TransformRequest request, boolean source) throws IOException {
        var paths = new ArrayList<>((source ? request.sourceArtifacts() : request.targetArtifacts()).values());
        var dependencies = source ? request.sourceClasspath() : request.targetClasspath();
        for (var dependency : new TreeMap<>(dependencies).values()) {
            dependency.verify();
            paths.add(dependency.file());
        }
        return ArtifactIndex.read(paths.stream().sorted().toList()).classes();
    }
    protected RulePack outputNamespace(TransformRequest request, RulePack selected) throws IOException {
        if (request.outputNamespace() == null || request.outputNamespace() == selected.target().mappings()) return null;
        var mapping = request.targetMapping();
        if (mapping == null || !mapping.namespaces().containsValue(request.outputNamespace()))
            throw new IOException("Output namespace requires explicit target mappings containing that namespace");
        var source = selected.target();
        var target = new EnvironmentId(source.minecraftVersion(), source.loader(), request.outputNamespace(), source.javaVersion());
        var provenance = new MappingProvenance(mapping.file().getFileName().toString(), mapping.file().toUri(), mapping.sha256(), mapping.license());
        try (var input = java.nio.file.Files.newInputStream(mapping.file())) {
            var declarations = rawApi(request, false);
            var symbols = new MappingIoImporter(mapping.namespaces(), mapping.from(), declarations).importMappings(input, source, provenance);
            return NamespaceRulePack.create(selected.id() + "-output-namespace", provenance, source, target,
                    selected.targetArtifacts(), selected.targetArtifacts(), symbols);
        }
    }
    protected Map<String, ClassInfo> outputApi(TransformRequest request) throws IOException {
        if (request.outputNamespace() == null) return api(request, false);
        var pack = selectedPack(rulePacks(), request);
        if (request.outputNamespace() == pack.target().mappings()) return api(request, false);
        outputNamespace(request, pack); // Validate explicit namespace bindings and mapping bytes.
        var raw = rawApi(request, false);
        if (request.outputNamespace() == request.targetMapping().from()) return raw;
        var env = new EnvironmentId(pack.target().minecraftVersion(), pack.target().loader(), request.outputNamespace(), pack.target().javaVersion());
        return DeclarationNamespace.remap(raw, request.targetMapping(), env);
    }
}
