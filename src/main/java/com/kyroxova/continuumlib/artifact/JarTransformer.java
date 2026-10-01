package com.kyroxova.continuumlib.artifact;

import org.objectweb.asm.ClassReader;
import com.kyroxova.continuumlib.bytecode.ClassInspector;
import com.kyroxova.continuumlib.bytecode.ClassInfo;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.jar.*;
import java.util.zip.ZipEntry;

/** Deterministic class transformation container. Resources are copied, not migrated.
 * Output is not a compatibility certificate. Signed, modular and multi-release inputs
 * require policies not implemented here and are rejected rather than silently damaged.
 */
public final class JarTransformer {
    public record Result(int classes, int resources) {}
    public Result transform(Path input, Path output, UnaryOperator<byte[]> adapter) throws IOException {
        return transform(input, output, adapter, UnaryOperator.identity());
    }
    public Result transform(Path input, Path output, UnaryOperator<byte[]> adapter, UnaryOperator<String> mapClassName) throws IOException {
        Objects.requireNonNull(adapter);
        var resourceAdapter = new ClassNameResources(mapClassName);
        Path source = input.toRealPath(), destination = output.toAbsolutePath().normalize();
        if (source.equals(destination) || (Files.exists(destination) && Files.isSameFile(source, destination)))
            throw new IOException("Input and output JAR must be different files");
        if (Files.isSymbolicLink(destination)) throw new IOException("Output JAR cannot be a symbolic link");
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), ".continuumlib-", ".jar.tmp");
        try {
            int classes = 0, resources = 0;
            try (JarFile jar = new JarFile(source.toFile())) {
                if (jar.isMultiRelease()) throw new IOException("Multi-release input requires an explicit runtime policy");
                Manifest manifest = jar.getManifest();
                if (manifest != null) {
                    rejectDigest(manifest.getMainAttributes());
                    for (Attributes attributes : manifest.getEntries().values()) rejectDigest(attributes);
                }
                List<JarEntry> entries = jar.stream().sorted(Comparator.comparing(ZipEntry::getName)).toList();
                Set<String> written = new HashSet<>();
                try (var stream = new JarOutputStream(Files.newOutputStream(temporary))) {
                    for (JarEntry entry : entries) {
                        String name = entry.getName();
                        safeName(name);
                        String upper = name.toUpperCase(Locale.ROOT);
                        if (upper.startsWith("META-INF/") && (upper.endsWith(".SF") || upper.endsWith(".RSA")
                                || upper.endsWith(".DSA") || upper.endsWith(".EC") || upper.startsWith("META-INF/SIG-")))
                            throw new IOException("Signed input requires an explicit re-signing policy: " + name);
                        if (name.equals("module-info.class") || name.startsWith("META-INF/versions/"))
                            throw new IOException("Modular or versioned entries are not supported: " + name);
                        if (entry.isDirectory()) continue;
                        try (var contents = jar.getInputStream(entry)) {
                            if (name.endsWith(".class")) {
                                byte[] original = contents.readAllBytes();
                                if (!(new ClassReader(original).getClassName() + ".class").equals(name))
                                    throw new IOException("Class name does not match its JAR entry: " + name);
                                byte[] adapted = adapter.apply(original);
                                var info = new ClassInspector().inspect(adapted);
                                uniqueMembers(info.name(), info.methods());
                                uniqueMembers(info.name(), info.fields());
                                String mappedName = new ClassReader(adapted).getClassName() + ".class";
                                writeEntry(stream, written, mappedName);
                                stream.write(adapted);
                                classes++;
                            } else if (name.startsWith("META-INF/services/") || name.equalsIgnoreCase("META-INF/MANIFEST.MF")) {
                                var resource = resourceAdapter.adapt(name, contents.readAllBytes());
                                writeEntry(stream, written, resource.name());
                                stream.write(resource.contents());
                                resources++;
                            } else {
                                writeEntry(stream, written, name);
                                contents.transferTo(stream);
                                resources++;
                            }
                            stream.closeEntry();
                        }
                    }
                }
            }
            // Never expose a partially transformed JAR. Unsupported atomic moves fail safely.
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return new Result(classes, resources);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
    private static void writeEntry(JarOutputStream stream, Set<String> names, String name) throws IOException {
        safeName(name);
        if (!names.add(name)) throw new IOException("Transformed JAR entry collision: " + name);
        var entry = new JarEntry(name);
        entry.setTime(0);
        stream.putNextEntry(entry);
    }
    private static void safeName(String name) throws IOException {
        if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains(":"))
            throw new IOException("Unsafe JAR entry: " + name);
        for (String part : name.split("/", -1))
            if (part.equals("..") || part.equals(".")) throw new IOException("Unsafe JAR entry: " + name);
    }
    private static void rejectDigest(Attributes attributes) throws IOException {
        for (Object key : attributes.keySet()) {
            String name = key.toString().toUpperCase(Locale.ROOT);
            if (name.contains("-DIGEST") || name.equals("SIGNATURE-VERSION"))
                throw new IOException("Manifest contains signing metadata; re-signing is not implemented");
        }
    }
    private static void uniqueMembers(String owner, List<ClassInfo.Member> members) throws IOException {
        Set<String> signatures = new HashSet<>();
        for (var member : members)
            if (!signatures.add(member.name() + "\u0000" + member.descriptor()))
                throw new IOException("Transformed member collision: " + owner + "." + member.name() + member.descriptor());
    }
}
