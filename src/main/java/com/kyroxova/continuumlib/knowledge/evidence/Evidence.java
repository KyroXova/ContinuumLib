package com.kyroxova.continuumlib.knowledge.evidence;

import java.util.Objects;

public record Evidence(EvidenceType type, String description) {
    public Evidence {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(description, "description");
    }

    public static Evidence of(EvidenceType type, String description) {
        return new Evidence(type, description);
    }
}
