package com.kyroxova.continuumlib.model.version;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class MinecraftVersion implements Comparable<MinecraftVersion> {
    private static final Pattern RELEASE = Pattern.compile("[0-9]+(?:\\.[0-9]+)*");
    private final List<Integer> parts;

    private MinecraftVersion(List<Integer> parts) {
        this.parts = List.copyOf(parts);
    }

    public static MinecraftVersion parse(String value) {
        Objects.requireNonNull(value, "value");
        String normalized = value.trim();
        if (!RELEASE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Unsupported Minecraft version format: " + value);
        }
        List<Integer> parts = new ArrayList<>();
        for (String part : normalized.split("\\.")) {
            parts.add(Integer.parseInt(part));
        }
        while (parts.size() > 1 && parts.get(parts.size() - 1) == 0) {
            parts.remove(parts.size() - 1);
        }
        return new MinecraftVersion(parts);
    }

    @Override
    public int compareTo(MinecraftVersion other) {
        int length = Math.max(parts.size(), other.parts.size());
        for (int index = 0; index < length; index++) {
            int left = index < parts.size() ? parts.get(index) : 0;
            int right = index < other.parts.size() ? other.parts.get(index) : 0;
            int comparison = Integer.compare(left, right);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof MinecraftVersion other && compareTo(other) == 0;
    }

    @Override
    public int hashCode() {
        return parts.hashCode();
    }

    @Override
    public String toString() {
        return parts.stream().map(String::valueOf).reduce((a, b) -> a + "." + b).orElseThrow();
    }
}
