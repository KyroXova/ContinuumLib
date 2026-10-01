package com.kyroxova.continuumlib.model.diagnostic;

import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import java.nio.file.Path;
import java.util.Objects;

public record Diagnostic(
        DiagnosticCode code,
        Severity severity,
        String message,
        String targetId,
        String stage,
        Path path,
        int line,
        int column,
        String ruleId,
        EnvironmentId sourceEnvironment,
        EnvironmentId targetEnvironment
) {
    public Diagnostic(DiagnosticCode code, Severity severity, String message) {
        this(code, severity, message, null, null, null, -1, -1, null, null, null);
    }

    public Diagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message cannot be blank");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private DiagnosticCode code;
        private Severity severity;
        private String message;
        private String targetId;
        private String stage;
        private Path path;
        private int line = -1;
        private int column = -1;
        private String ruleId;
        private EnvironmentId sourceEnvironment;
        private EnvironmentId targetEnvironment;

        public Builder code(DiagnosticCode code) { this.code = code; return this; }
        public Builder severity(Severity severity) { this.severity = severity; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder targetId(String targetId) { this.targetId = targetId; return this; }
        public Builder stage(String stage) { this.stage = stage; return this; }
        public Builder path(Path path) { this.path = path; return this; }
        public Builder line(int line) { this.line = line; return this; }
        public Builder column(int column) { this.column = column; return this; }
        public Builder ruleId(String ruleId) { this.ruleId = ruleId; return this; }
        public Builder sourceEnvironment(EnvironmentId env) { this.sourceEnvironment = env; return this; }
        public Builder targetEnvironment(EnvironmentId env) { this.targetEnvironment = env; return this; }

        public Diagnostic build() {
            return new Diagnostic(code, severity, message, targetId, stage, path, line, column, ruleId, sourceEnvironment, targetEnvironment);
        }
    }
}
