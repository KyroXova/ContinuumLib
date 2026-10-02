package com.kyroxova.continuumlib.source.compile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

public final class TargetJarPackager {
    private TargetJarPackager() {
    }

    public static void packageJar(Path classesDir, Path resourcesDir, Path outputJar) throws IOException {
        Path tempJar = prepareTemp(outputJar);
        try {
            try (var out = new JarOutputStream(Files.newOutputStream(tempJar))) {
                Set<String> writtenEntries = new HashSet<>();
                writeDirectory(out, classesDir, true, writtenEntries, false);
                writeDirectory(out, resourcesDir, false, writtenEntries, false);
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
        packageJarWithResources(classesDir, resources, null, outputJar);
    }

    public static void packageJarWithResources(
            Path classesDir,
            Map<String, Path> resources,
            Map<String, String> manifestAttributes,
            Path outputJar
    ) throws IOException {
        Path tempJar = prepareTemp(outputJar);
        try {
            try (var out = new JarOutputStream(Files.newOutputStream(tempJar))) {
                Set<String> writtenEntries = new HashSet<>();
                boolean explicitManifest = manifestAttributes != null;
                if (explicitManifest) {
                    writeManifest(out, manifestAttributes, writtenEntries);
                }
                writeDirectory(out, classesDir, true, writtenEntries, explicitManifest);
                if (resources != null && !resources.isEmpty()) {
                    for (var entry : new TreeMap<>(resources).entrySet()) {
                        String entryName = normalizeEntryName(entry.getKey());
                        if (entryName.endsWith(".class")) continue;
                        if (explicitManifest && isManifest(entryName)) continue;
                        writeEntry(out, entryName, entry.getValue(), writtenEntries);
                    }
                }
            }
            replace(tempJar, outputJar);
        } finally {
            Files.deleteIfExists(tempJar);
        }
    }

    private static void writeManifest(
            JarOutputStream out,
            Map<String, String> manifestAttributes,
            Set<String> writtenEntries
    ) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();

        TreeMap<String, String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (var entry : manifestAttributes.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IOException("Manifest attributes must not contain null keys or values");
            }
            if (sorted.put(entry.getKey(), entry.getValue()) != null) {
                throw new IOException("Duplicate manifest attribute: " + entry.getKey());
            }
        }

        String version = sorted.remove(Attributes.Name.MANIFEST_VERSION.toString());
        String effectiveVersion = version == null || version.isBlank() ? "1.0" : version;
        validateManifestValue(Attributes.Name.MANIFEST_VERSION.toString(), effectiveVersion);
        attributes.put(Attributes.Name.MANIFEST_VERSION, effectiveVersion);

        for (var entry : sorted.entrySet()) {
            String value = entry.getValue();
            validateManifestValue(entry.getKey(), value);
            try {
                attributes.put(new Attributes.Name(entry.getKey()), value);
            } catch (IllegalArgumentException invalidName) {
                throw new IOException("Invalid manifest attribute name: " + entry.getKey(), invalidName);
            }
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        manifest.write(bytes);

        String entryName = "META-INF/MANIFEST.MF";
        registerEntry(entryName, writtenEntries);
        JarEntry entry = new JarEntry(entryName);
        entry.setTime(0L);
        out.putNextEntry(entry);
        try {
            out.write(bytes.toByteArray());
        } finally {
            out.closeEntry();
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
            boolean includeClasses,
            Set<String> writtenEntries,
            boolean skipManifest
    ) throws IOException {
        if (root == null || !Files.isDirectory(root)) return;

        Path normalizedRoot = root.toAbsolutePath().normalize();
        try (Stream<Path> stream = Files.walk(normalizedRoot)) {
            for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                String entryName = normalizeEntryName(normalizedRoot.relativize(file).toString());
                if (!includeClasses && entryName.endsWith(".class")) continue;
                if (skipManifest && isManifest(entryName)) continue;
                writeEntry(out, entryName, file, writtenEntries);
            }
        }
    }

    private static void writeEntry(
            JarOutputStream out,
            String entryName,
            Path file,
            Set<String> writtenEntries
    ) throws IOException {
        registerEntry(entryName, writtenEntries);
        JarEntry entry = new JarEntry(entryName);
        entry.setTime(0L);
        out.putNextEntry(entry);
        try {
            Files.copy(file, out);
        } finally {
            out.closeEntry();
        }
    }


    private static void validateManifestValue(String name, String value) throws IOException {
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IOException("Manifest attribute contains a line break: " + name);
        }
    }

    private static boolean isManifest(String entryName) {
        return entryName.equalsIgnoreCase("META-INF/MANIFEST.MF");
    }

    private static void registerEntry(String entryName, Set<String> writtenEntries) throws IOException {
        if (!writtenEntries.add(entryName)) {
            throw new IOException("Duplicate JAR entry: " + entryName);
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
