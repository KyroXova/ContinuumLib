package com.kyroxova.continuumlib.knowledge.snapshot;

import com.kyroxova.continuumlib.bytecode.ArtifactIndex;
import com.kyroxova.continuumlib.bytecode.ClassInfo;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public final class SnapshotBuilder {
    private SnapshotBuilder() {}

    public static ApiSnapshot fromClassIndex(EnvironmentId environment, Map<String, String> artifactManifest, Map<String, ClassInfo> classes) {
        Map<String, ClassSnapshot> classSnapshots = new TreeMap<>();
        for (var entry : classes.entrySet()) {
            classSnapshots.put(entry.getKey(), ClassSnapshot.from(entry.getValue()));
        }
        return new ApiSnapshot(environment, artifactManifest, classSnapshots);
    }

    public static ApiSnapshot fromArtifacts(EnvironmentId environment, Map<String, Path> artifacts) throws IOException {
        Map<String, String> manifest = new TreeMap<>();
        List<Path> paths = new ArrayList<>();
        for (var entry : artifacts.entrySet()) {
            paths.add(entry.getValue());
            manifest.put(entry.getKey(), sha256(entry.getValue()));
        }
        ArtifactIndex index = ArtifactIndex.read(paths);
        return fromClassIndex(environment, manifest, index.classes());
    }

    private static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        try (var input = Files.newInputStream(file)) {
            byte[] buf = new byte[65536];
            int read;
            while ((read = input.read(buf)) != -1) digest.update(buf, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
