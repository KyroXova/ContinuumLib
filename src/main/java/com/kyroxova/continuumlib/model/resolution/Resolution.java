package com.kyroxova.continuumlib.model.resolution;

import com.kyroxova.continuumlib.model.operation.SemanticOperation;

import java.util.Objects;

public record Resolution(SemanticOperation operation, ResolutionStatus status, String provenance, String message) {
    public Resolution {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(message, "message");
    }
}
