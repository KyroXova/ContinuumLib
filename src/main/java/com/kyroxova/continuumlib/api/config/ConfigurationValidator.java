package com.kyroxova.continuumlib.api.config;

import com.kyroxova.continuumlib.model.diagnostic.Diagnostic;
import com.kyroxova.continuumlib.model.diagnostic.DiagnosticCode;
import com.kyroxova.continuumlib.model.diagnostic.Severity;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ConfigurationValidator {
    public List<Diagnostic> validate(ContinuumLibConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        List<Diagnostic> diagnostics = new ArrayList<>();
        Set<EnvironmentId> seen = new HashSet<>();
        boolean duplicateReported = false;
        boolean baseReported = false;

        for (EnvironmentId target : configuration.targets()) {
            if (!seen.add(target) && !duplicateReported) {
                diagnostics.add(error(DiagnosticCode.DUPLICATE_TARGET,
                        "The same target environment is configured more than once: " + target));
                duplicateReported = true;
            }
            if (configuration.base().equals(target) && !baseReported) {
                diagnostics.add(error(DiagnosticCode.BASE_REPEATED_AS_TARGET,
                        "The base environment must not also be configured as a target: " + target));
                baseReported = true;
            }
        }

        if (!configuration.output().perVersionJars() && !configuration.output().universalJar()) {
            diagnostics.add(error(DiagnosticCode.NO_OUTPUT_MODE,
                    "At least one ContinuumLib output mode must be enabled"));
        }
        return List.copyOf(diagnostics);
    }

    private Diagnostic error(DiagnosticCode code, String message) {
        return new Diagnostic(code, Severity.ERROR, message);
    }
}
