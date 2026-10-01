package com.kyroxova.continuumlib.knowledge.mapping;

import java.net.URI;
import java.util.Objects;

public record MappingProvenance(String sourceName, URI source, String checksum, String license) {
    public MappingProvenance {
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(checksum, "checksum");
        Objects.requireNonNull(license, "license");
    }
}
