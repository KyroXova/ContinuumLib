package com.kyroxova.continuumlib.filter.registry;

import com.kyroxova.continuumlib.filter.domain.RegistryType;

import java.util.*;

/**
 * Indexed collection of discovered registry entries in a mod project.
 */
public record RegistryIndex(List<RegistryEntry> entries) {
    public static final RegistryIndex EMPTY = new RegistryIndex(List.of());

    public RegistryIndex {
        entries = List.copyOf(entries);
    }

    public List<RegistryEntry> findByTypeAndId(RegistryType type, String targetId) {
        return entries.stream()
                .filter(e -> e.matches(type, targetId))
                .toList();
    }

    public Optional<RegistryEntry> findByDeclaration(String ownerClass, String fieldName) {
        return entries.stream()
                .filter(e -> e.ownerClass().equals(ownerClass) && e.fieldName().equals(fieldName))
                .findFirst();
    }
}
