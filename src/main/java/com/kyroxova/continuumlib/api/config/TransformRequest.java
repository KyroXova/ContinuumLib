package com.kyroxova.continuumlib.api.config;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;

/** One explicit route request in the consuming project; paths are not downloads. */
public record TransformRequest(String packId, Map<String, Path> sourceArtifacts, Map<String, Path> targetArtifacts,
                               MappingRequest sourceMapping, MappingRequest targetMapping, MappingNamespace outputNamespace,
                               Map<String, ClasspathArtifact> sourceClasspath, Map<String, ClasspathArtifact> targetClasspath,
                               String targetLoaderVersion) {
    public TransformRequest(String packId, Map<String, Path> sourceArtifacts, Map<String, Path> targetArtifacts) {
        this(packId, sourceArtifacts, targetArtifacts, null, null, null, Map.of(), Map.of(), null);
    }
    public TransformRequest(String packId, Map<String, Path> sourceArtifacts, Map<String, Path> targetArtifacts,
                            MappingRequest sourceMapping, MappingRequest targetMapping, MappingNamespace outputNamespace) {
        this(packId, sourceArtifacts, targetArtifacts, sourceMapping, targetMapping, outputNamespace, Map.of(), Map.of(), null);
    }
    public TransformRequest(String packId, Map<String, Path> sourceArtifacts, Map<String, Path> targetArtifacts,
                            MappingRequest sourceMapping, MappingRequest targetMapping, MappingNamespace outputNamespace,
                            Map<String, ClasspathArtifact> sourceClasspath, Map<String, ClasspathArtifact> targetClasspath) {
        this(packId, sourceArtifacts, targetArtifacts, sourceMapping, targetMapping, outputNamespace,
                sourceClasspath, targetClasspath, null);
    }
    public TransformRequest {
        if (packId == null || packId.isBlank() || sourceArtifacts.isEmpty() || targetArtifacts.isEmpty())
            throw new IllegalArgumentException("pack, source artifacts and target artifacts are required");
        sourceArtifacts = Map.copyOf(sourceArtifacts);
        targetArtifacts = Map.copyOf(targetArtifacts);
        sourceClasspath = Map.copyOf(sourceClasspath);
        targetClasspath = Map.copyOf(targetClasspath);
        targetLoaderVersion = targetLoaderVersion == null || targetLoaderVersion.isBlank()
                ? null
                : targetLoaderVersion.trim();
    }
    public static TransformRequest read(Path config, Path projectRoot) throws IOException {
        Properties properties = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new IllegalArgumentException("Duplicate setting: " + key);
                return super.put(key, value);
            }
        };
        try (var input = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
            properties.load(input);
            var source = new TreeMap<String, Path>();
            var target = new TreeMap<String, Path>();
            for (String key : properties.stringPropertyNames()) {
                if (key.equals("pack") || key.equals("output.namespace") || key.equals("target.loaderVersion")) continue;
                if (key.startsWith("mapping.source.") || key.startsWith("mapping.target.")) continue;
                if (key.startsWith("classpath.source.") || key.startsWith("classpath.target.")) continue;
                Map<String, Path> paths;
                String name;
                if (key.startsWith("source.")) { paths = source; name = key.substring(7); }
                else if (key.startsWith("target.")) { paths = target; name = key.substring(7); }
                else throw new IllegalArgumentException("Unknown setting: " + key);
                String value = properties.getProperty(key).trim();
                if (name.isBlank() || value.isBlank()) throw new IllegalArgumentException("Blank artifact setting: " + key);
                paths.put(name, projectRoot.toAbsolutePath().resolve(value).normalize());
            }
            String loaderVersion = properties.getProperty("target.loaderVersion");
            if (loaderVersion != null && loaderVersion.isBlank()) {
                throw new IllegalArgumentException("target.loaderVersion must not be blank");
            }
            return new TransformRequest(properties.getProperty("pack", "").trim(), source, target,
                    MappingRequest.read(properties, "source", projectRoot), MappingRequest.read(properties, "target", projectRoot),
                    properties.containsKey("output.namespace") ? MappingNamespace.valueOf(properties.getProperty("output.namespace").trim()) : null,
                    ClasspathArtifact.read(properties, "source", projectRoot), ClasspathArtifact.read(properties, "target", projectRoot),
                    loaderVersion);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid ContinuumLib transform request: " + e.getMessage(), e);
        }
    }
}
