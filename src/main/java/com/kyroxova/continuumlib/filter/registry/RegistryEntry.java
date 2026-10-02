package com.kyroxova.continuumlib.filter.registry;

import com.kyroxova.continuumlib.filter.domain.RegistryType;

import java.util.Objects;

/**
 * Resolved semantic registry element declaration within mod source code.
 */
public record RegistryEntry(
        RegistryType registryType,
        String namespace,
        String id,
        String ownerClass,
        String fieldName,
        String sourcePath,
        int lineNumber
) {
    public RegistryEntry {
        Objects.requireNonNull(registryType, "registryType");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerClass, "ownerClass");
        Objects.requireNonNull(fieldName, "fieldName");
    }

    public String fullId() {
        return (namespace != null && !namespace.isBlank()) ? namespace + ":" + id : id;
    }

    public String declaration() {
        return ownerClass + "." + fieldName;
    }

    public boolean matches(RegistryType type, String targetId) {
        if (!this.registryType.equals(type)) return false;
        String trimmed = targetId.trim();
        if (trimmed.contains(":")) {
            if (namespace == null || namespace.isBlank()) return false;
            String nsPart = trimmed.substring(0, trimmed.indexOf(':'));
            String pathPart = trimmed.substring(trimmed.indexOf(':') + 1);
            return namespace.equalsIgnoreCase(nsPart) && id.equals(pathPart);
        }
        return this.id.equals(trimmed);
    }
}
