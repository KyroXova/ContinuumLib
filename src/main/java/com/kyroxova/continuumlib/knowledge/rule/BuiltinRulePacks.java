package com.kyroxova.continuumlib.knowledge.rule;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit resource index works equally from the development tree and the packaged library. */
public final class BuiltinRulePacks {
    private static final String ROOT = "/data/continuumlib/knowledge/";
    private BuiltinRulePacks() {}
    public static List<RulePack> load() throws IOException {
        var packs = new ArrayList<RulePack>();
        try (var index = new BufferedReader(new InputStreamReader(resource(ROOT + "index.txt"), StandardCharsets.UTF_8))) {
            String entry;
            while ((entry = index.readLine()) != null) {
                entry = entry.trim();
                if (entry.isEmpty() || entry.startsWith("#")) continue;
                if (entry.startsWith("/") || entry.contains("..") || entry.contains("\\") || !entry.endsWith(".xml"))
                    throw new IOException("Invalid bundled rule resource: " + entry);
                try (var input = resource(ROOT + entry)) { packs.add(new RulePackReader().read(input)); }
            }
        }
        new RuleCatalog(packs); // Detect duplicate IDs before exposing the catalog.
        return List.copyOf(packs);
    }
    private static InputStream resource(String name) throws IOException {
        InputStream input = BuiltinRulePacks.class.getResourceAsStream(name);
        if (input == null) throw new IOException("Missing ContinuumLib resource " + name);
        return input;
    }
}
