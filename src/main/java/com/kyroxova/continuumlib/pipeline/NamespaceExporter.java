package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.continuumlib.artifact.JarTransformer;
import com.kyroxova.continuumlib.bytecode.*;
import com.kyroxova.continuumlib.knowledge.mapping.MappingIoImporter;
import com.kyroxova.continuumlib.knowledge.mapping.MappingProvenance;
import com.kyroxova.continuumlib.knowledge.mapping.NamespaceRulePack;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class NamespaceExporter {
    public record Result(int adaptedClasses) {}

    public Result export(Path jar, ResolvedTarget target) throws IOException {
        var outputNamespace = target.outputNamespace();
        if (outputNamespace == null || outputNamespace == target.targetEnvironment().mappings()) {
            return new Result(0);
        }

        var mapping = target.targetMapping();
        if (mapping == null || !mapping.namespaces().containsValue(outputNamespace)) {
            throw new IOException("Output namespace " + outputNamespace + " requires explicit target mappings");
        }

        TargetApiResolver apiResolver = new TargetApiResolver();
        Map<String, ClassInfo> rawTargetApi = apiResolver.raw(target);
        Map<String, ClassInfo> targetApi = apiResolver.forNamespace(
                target,
                target.targetEnvironment().mappings()
        );

        MappingProvenance provenance = new MappingProvenance(
                mapping.file().getFileName().toString(),
                mapping.file().toUri(),
                mapping.sha256(),
                mapping.license()
        );

        var symbols = readSymbols(target, mapping, rawTargetApi, provenance);
        EnvironmentId outputEnvironment = new EnvironmentId(
                target.targetEnvironment().minecraftVersion(),
                target.targetEnvironment().loader(),
                outputNamespace,
                target.targetEnvironment().javaVersion()
        );

        Map<String, String> manifest = manifest(target.targetArtifacts());
        var namespacePack = NamespaceRulePack.create(
                target.targetId() + "-output-" + outputNamespace.name().toLowerCase(Locale.ROOT),
                provenance,
                target.targetEnvironment(),
                outputEnvironment,
                manifest,
                manifest,
                symbols
        );

        Map<String, ClassInfo> hierarchy = new HashMap<>(targetApi);
        Map<String, ClassInfo> modApi = ArtifactIndex.read(List.of(jar)).classes();
        Set<String> outputApiNames = new HashSet<>();
        for (String apiName : targetApi.keySet()) {
            outputApiNames.add(namespacePack.classes().getOrDefault(apiName, apiName));
        }

        for (var entry : modApi.entrySet()) {
            String exportedName = namespacePack.classes().getOrDefault(entry.getKey(), entry.getKey());
            if (outputApiNames.contains(exportedName)) {
                throw new IOException("Mod JAR duplicates target API class in output namespace: " + exportedName);
            }
            if (hierarchy.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
                throw new IOException("Mod JAR duplicates target API class: " + entry.getKey());
            }
        }

        ClassAdapter adapter = new ClassAdapter(
                namespacePack.classes(),
                namespacePack.members(),
                hierarchy
        );

        Path transformed = jar.resolveSibling(jar.getFileName() + ".namespace");
        Files.deleteIfExists(transformed);
        AtomicInteger adaptedClasses = new AtomicInteger();

        try {
            new JarTransformer().transform(
                    jar,
                    transformed,
                    original -> {
                        byte[] adapted = adapter.adapt(original);
                        if (!Arrays.equals(original, adapted)) {
                            adaptedClasses.incrementAndGet();
                        }
                        return adapted;
                    },
                    name -> namespacePack.classes().getOrDefault(name, name)
            );
            replace(transformed, jar);
            return new Result(adaptedClasses.get());
        } finally {
            Files.deleteIfExists(transformed);
        }
    }

    private static Collection<com.kyroxova.continuumlib.model.symbol.SymbolName> readSymbols(
            ResolvedTarget target,
            com.kyroxova.continuumlib.api.config.MappingRequest mapping,
            Map<String, ClassInfo> rawTargetApi,
            MappingProvenance provenance
    ) throws IOException {
        try (var input = Files.newInputStream(mapping.file())) {
            return new MappingIoImporter(
                    mapping.namespaces(),
                    mapping.from(),
                    rawTargetApi
            ).importMappings(input, target.targetEnvironment(), provenance);
        }
    }

    private static Map<String, String> manifest(Map<String, Path> artifacts) throws IOException {
        if (artifacts.isEmpty()) {
            throw new IOException("Target artifact manifest is required for namespace export");
        }

        Map<String, String> result = new TreeMap<>();
        for (var entry : artifacts.entrySet()) {
            result.put(entry.getKey(), sha256(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
