package com.kyroxova.continuumlib.source.compile;

import java.io.IOException;
import java.nio.file.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

public final class TargetJarPackager {

    public static void packageJar(Path classesDir, Path resourcesDir, Path outputJar) throws IOException {
        Files.createDirectories(outputJar.toAbsolutePath().getParent());
        Path tempJar = outputJar.resolveSibling(outputJar.getFileName().toString() + ".tmp");

        try (var out = new JarOutputStream(Files.newOutputStream(tempJar))) {
            if (Files.exists(classesDir) && Files.isDirectory(classesDir)) {
                try (Stream<Path> stream = Files.walk(classesDir)) {
                    for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                        String entryName = classesDir.relativize(file).toString().replace('\\', '/');
                        out.putNextEntry(new JarEntry(entryName));
                        Files.copy(file, out);
                        out.closeEntry();
                    }
                }
            }

            if (resourcesDir != null && Files.exists(resourcesDir) && Files.isDirectory(resourcesDir)) {
                try (Stream<Path> stream = Files.walk(resourcesDir)) {
                    for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                        String entryName = resourcesDir.relativize(file).toString().replace('\\', '/');
                        if (entryName.endsWith(".class")) continue;
                        out.putNextEntry(new JarEntry(entryName));
                        Files.copy(file, out);
                        out.closeEntry();
                    }
                }
            }
        }

        Files.move(tempJar, outputJar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public static void packageJarWithResources(Path classesDir, java.util.Map<String, Path> resources, Path outputJar) throws IOException {
        Files.createDirectories(outputJar.toAbsolutePath().getParent());
        Path tempJar = outputJar.resolveSibling(outputJar.getFileName().toString() + ".tmp");

        try (var out = new JarOutputStream(Files.newOutputStream(tempJar))) {
            if (Files.exists(classesDir) && Files.isDirectory(classesDir)) {
                try (Stream<Path> stream = Files.walk(classesDir)) {
                    for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                        String entryName = classesDir.relativize(file).toString().replace('\\', '/');
                        out.putNextEntry(new JarEntry(entryName));
                        Files.copy(file, out);
                        out.closeEntry();
                    }
                }
            }

            if (resources != null && !resources.isEmpty()) {
                for (var entry : resources.entrySet()) {
                    String entryName = entry.getKey().replace('\\', '/');
                    if (entryName.endsWith(".class")) continue;
                    out.putNextEntry(new JarEntry(entryName));
                    Files.copy(entry.getValue(), out);
                    out.closeEntry();
                }
            }
        }

        Files.move(tempJar, outputJar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
