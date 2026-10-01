package com.kyroxova.continuumlib.knowledge.symbol;

import java.util.Objects;

public record SymbolId(String namespace, String name) implements Comparable<SymbolId> {
    public SymbolId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
        namespace = namespace.trim();
        name = name.trim();
        if (namespace.isEmpty() || name.isEmpty()) {
            throw new IllegalArgumentException("Symbol namespace and name cannot be empty");
        }
    }

    public static SymbolId of(String namespace, String name) {
        return new SymbolId(namespace, name);
    }

    public static SymbolId parse(String qualified) {
        Objects.requireNonNull(qualified, "qualified");
        int idx = qualified.indexOf(':');
        if (idx <= 0 || idx == qualified.length() - 1) {
            throw new IllegalArgumentException("Invalid qualified symbol ID: " + qualified + ". Format must be namespace:name");
        }
        return new SymbolId(qualified.substring(0, idx), qualified.substring(idx + 1));
    }

    @Override
    public String toString() {
        return namespace + ":" + name;
    }

    @Override
    public int compareTo(SymbolId o) {
        int c = namespace.compareTo(o.namespace);
        return c != 0 ? c : name.compareTo(o.name);
    }
}
