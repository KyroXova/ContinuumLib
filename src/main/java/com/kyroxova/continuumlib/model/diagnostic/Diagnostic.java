package com.kyroxova.continuumlib.model.diagnostic;

import java.util.Objects;

public record Diagnostic(DiagnosticCode code, Severity severity, String message) {
    public Diagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message cannot be blank");
        }
    }
}
