package com.kyroxova.continuumlib.source.compile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

public final class TargetJarPackager {
    private TargetJarPackager() {
    }

    public static void packageJar(Path classesDir, Path resourcesDir, Path outputJar) throws IOException {
        Path tempJar = prepareTemp(outputJar);
        try {
            try (var out = new JarOutputStream(Files.newOutputStream(tempJar))) {
                writeDirectory(out, classesDir, true);
                writeDirectory(out, resourcesDir, false);
            }
            replace(tempJar, outputJar);
        } finally {
            Files.deleteIfExists(tempJar);
        }
    }

    public static void packageJarWithResources(
            Path classesDir,
            Map<String, Path> resources,
            Path outputJar
    ) throws IOException {
        Path tempJar = prepareTemp(outputJar);
        try {
            try (var out = new JarOutputStream(Files.newOutputStream(tempJar))) {
                writeDirectory(out, classesDir, true);
                if (resources != null && !resources.isEmpty()) {
                    for (var entry : new TreeMap<>(resources).entrySet()) {
                        String entryName = normalizeEntryName(entry.getKey());
                        if (entryName.endsWith(".class")) continue;
                        writeEntry(out, entryName, entry.getValue());
                    }
                }
            }
            replace(tempJar, outputJar);
        } finally {
            Files.deleteIfExists(tempJar);
        }
    }

    private static Path prepareTemp(Path outputJar) throws IOException {
        Path output = outputJar.toAbsolutePath().normalize();
        Path parent = output.getParent();
        if (parent == null) {
            throw new IOException("Output JAR must have a parent directory: " + outputJar);
        }
        Files.createDirectories(parent);
        Path temp = output.resolveSibling(output.getFileName().toString() + ".tmp");
        Files.deleteIfExists(temp);
        return temp;
    }

    private static void writeDirectory(
            JarOutputStream out,
            Path root,
            boolean includeClasses
    ) throws IOException {
        if (root == null || !Files.isDirectory(root)) return;

        Path normalizedRoot = root.toAbsolutePath().normalize();
        try (Stream<Path> stream = Files.walk(normalizedRoot)) {
            for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                String entryName = normalizeEntryName(normalizedRoot.relativize(file).toString());
                if (!includeClasses && entryName.endsWith(".class")) continue;
                writeEntry(out, entryName, file);
            }
        }
    }

    private static void writeEntry(JarOutputStream out, String entryName, Path file) throws IOException {
        JarEntry entry = new JarEntry(entryName);
        entry.setTime(0L);
        out.putNextEntry(entry);
        try {
            Files.copy(file, out);
        } finally {
            out.closeEntry();
        }
    }

    private static String normalizeEntryName(String name) throws IOException {
        String normalized = name.replace('\\', '/');
        if (normalized.isBlank()
                || normalized.startsWith("/")
                || normalized.equals("..")
                || normalized.startsWith("../")
                || normalized.contains("/../")) {
            throw new IOException("Invalid JAR entry path: " + name);
        }
        return normalized;
    }

    private static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
