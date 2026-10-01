package com.kyroxova.continuumlib.api.config;

import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Explicit declaration-only dependency. Never installed, executed, or embedded in the mod. */
public record ClasspathArtifact(Path file, String sha256) {
    public ClasspathArtifact {
        Objects.requireNonNull(file);
        if (sha256 == null || !sha256.matches("[a-fA-F0-9]{64}"))
            throw new IllegalArgumentException("Classpath artifacts require a SHA-256 digest");
        file = file.toAbsolutePath().normalize();
        sha256 = sha256.toLowerCase(Locale.ROOT);
    }
    public void verify() throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) != -1;) digest.update(buffer, 0, read);
        }
        if (!sha256.equals(HexFormat.of().formatHex(digest.digest())))
            throw new IOException("Classpath SHA-256 mismatch: " + file);
    }
    static Map<String, ClasspathArtifact> read(Properties properties, String side, Path root) {
        String prefix = "classpath." + side + ".";
        var names = new TreeSet<String>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix)) continue;
            String suffix = key.substring(prefix.length());
            int separator = suffix.lastIndexOf('.');
            if (separator < 1 || !suffix.substring(0, separator).matches("[A-Za-z0-9_-]+")
                    || !Set.of("file", "sha256").contains(suffix.substring(separator + 1)))
                throw new IllegalArgumentException("Unknown classpath setting: " + key);
            names.add(suffix.substring(0, separator));
        }
        var result = new TreeMap<String, ClasspathArtifact>();
        for (String name : names) {
            String file = properties.getProperty(prefix + name + ".file", "").trim();
            if (file.isEmpty()) throw new IllegalArgumentException("Missing classpath file: " + prefix + name);
            result.put(name, new ClasspathArtifact(root.toAbsolutePath().resolve(file),
                    properties.getProperty(prefix + name + ".sha256", "").trim()));
        }
        return Map.copyOf(result);
    }
}
