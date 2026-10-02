package com.kyroxova.continuumlib.api.config;

import com.kyroxova.continuumlib.model.environment.MappingNamespace;

import java.nio.file.Path;
import java.util.*;

public record MappingRequest(
        Path file,
        String sha256,
        MappingNamespace from,
        Map<String, MappingNamespace> namespaces,
        String license
) {
    public MappingRequest {
        file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(namespaces, "namespaces");

        if (sha256 == null || !sha256.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Mapping SHA-256 is required");
        }
        sha256 = sha256.toLowerCase(Locale.ROOT);

        if (license == null || license.isBlank()) {
            throw new IllegalArgumentException("Mapping license declaration is required");
        }
        license = license.trim();

        namespaces = Map.copyOf(namespaces);
        if (!namespaces.containsValue(from)
                || new HashSet<>(namespaces.values()).size() != namespaces.size()) {
            throw new IllegalArgumentException(
                    "Mapping namespaces must be distinct and include the input namespace");
        }
    }

    static MappingRequest read(Properties properties, String side, Path root) {
        String prefix = "mapping." + side + ".";
        var keys = properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith(prefix))
                .toList();
        if (keys.isEmpty()) return null;

        Set<String> allowed = Set.of("file", "sha256", "from", "namespaces", "license");
        for (String key : keys) {
            if (!allowed.contains(key.substring(prefix.length()))) {
                throw new IllegalArgumentException("Unknown mapping setting: " + key);
            }
        }
        for (String key : allowed) {
            if (properties.getProperty(prefix + key, "").isBlank()) {
                throw new IllegalArgumentException("Missing mapping setting: " + prefix + key);
            }
        }

        Map<String, MappingNamespace> namespaces = new LinkedHashMap<>();
        for (String binding : properties.getProperty(prefix + "namespaces").split(",", -1)) {
            String[] pair = binding.trim().split(":", -1);
            if (pair.length != 2
                    || pair[0].isBlank()
                    || namespaces.putIfAbsent(
                            pair[0].trim(),
                            MappingNamespace.valueOf(pair[1].trim())
                    ) != null) {
                throw new IllegalArgumentException(
                        "Invalid or duplicate mapping namespace binding: " + binding);
            }
        }

        return new MappingRequest(
                root.toAbsolutePath()
                        .resolve(properties.getProperty(prefix + "file").trim())
                        .normalize(),
                properties.getProperty(prefix + "sha256").trim(),
                MappingNamespace.valueOf(properties.getProperty(prefix + "from").trim()),
                namespaces,
                properties.getProperty(prefix + "license")
        );
    }
}
