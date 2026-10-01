package com.kyroxova.continuumlib.artifact;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.jar.Manifest;

/** Standard JVM class-name resources only. Minecraft JSON schemas and mixins need separate migrations. */
public final class ClassNameResources {
    public record Resource(String name, byte[] contents) {}
    private static final String SERVICES = "META-INF/services/";
    private final UnaryOperator<String> mapInternalName;
    public ClassNameResources(UnaryOperator<String> mapInternalName) { this.mapInternalName = Objects.requireNonNull(mapInternalName); }
    public Resource adapt(String name, byte[] original) throws IOException {
        if (name.startsWith(SERVICES)) {
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(original)).toString();
            var result = new StringBuilder();
            for (String line : text.split("(?<=\n)", -1)) {
                int comment = line.indexOf('#');
                String prefix = comment < 0 ? line : line.substring(0, comment);
                String provider = prefix.trim();
                if (provider.isEmpty()) result.append(line);
                else {
                    int start = prefix.indexOf(provider);
                    result.append(line, 0, start).append(binary(provider)).append(line.substring(start + provider.length()));
                }
            }
            return new Resource(SERVICES + binary(name.substring(SERVICES.length())), result.toString().getBytes(StandardCharsets.UTF_8));
        }
        if (name.equalsIgnoreCase("META-INF/MANIFEST.MF")) {
            var manifest = new Manifest(new ByteArrayInputStream(original));
            boolean changed = false;
            for (String key : List.of("Main-Class", "Premain-Class", "Agent-Class", "Launcher-Agent-Class", "FMLCorePlugin")) {
                String value = manifest.getMainAttributes().getValue(key);
                if (value != null && !binary(value).equals(value)) {
                    manifest.getMainAttributes().putValue(key, binary(value)); changed = true;
                }
            }
            var sections = new LinkedHashMap<>(manifest.getEntries());
            manifest.getEntries().clear();
            for (var section : sections.entrySet()) {
                String key = section.getKey();
                String mapped = key.endsWith(".class") ? mapInternalName.apply(key.substring(0, key.length() - 6)) + ".class" : key;
                if (manifest.getEntries().putIfAbsent(mapped, section.getValue()) != null)
                    throw new IOException("Manifest section collision: " + mapped);
                changed |= !mapped.equals(key);
            }
            if (changed) {
                var out = new ByteArrayOutputStream(); manifest.write(out);
                return new Resource(name, out.toByteArray());
            }
        }
        return new Resource(name, original);
    }
    private String binary(String name) { return mapInternalName.apply(name.replace('.', '/')).replace('/', '.'); }
}
