package com.kyroxova.continuumlib.api.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Explicit fan-out configuration. Target IDs are portable filenames, not inferred versions. */
public record OutputTargets(List<String> targets, boolean perVersion, boolean universal) {
    public OutputTargets {
        targets = List.copyOf(targets);
        if (targets.isEmpty() || (!perVersion && !universal)) throw new IllegalArgumentException("Targets and at least one output mode are required");
        Set<String> ids = new HashSet<>();
        for (String id : targets) {
            validateTargetId(id);
            if (!ids.add(id.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Duplicate target ID: " + id);
        }
    }

    public static void validateTargetId(String id) {
        Objects.requireNonNull(id, "id");
        String base = id.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
        if (!id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}") || base.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"))
            throw new IllegalArgumentException("Unsafe target ID: " + id);
    }

    public static OutputTargets read(Path file) throws IOException {
        var values = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new IllegalArgumentException("Duplicate output setting: " + key);
                return super.put(key, value);
            }
        };
        try (var input = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            values.load(input);
            for (String key : values.stringPropertyNames())
                if (!Set.of("targets", "perVersion", "universal").contains(key)) throw new IllegalArgumentException("Unknown output setting: " + key);
            return new OutputTargets(Arrays.stream(values.getProperty("targets", "").split(",", -1)).map(String::trim).toList(),
                    bool(values, "perVersion"), bool(values, "universal"));
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid ContinuumLib output targets: " + e.getMessage(), e);
        }
    }
    private static boolean bool(Properties values, String name) {
        return switch (values.getProperty(name, "").trim()) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(name + " must explicitly be true or false");
        };
    }
}
