package com.kyroxova.continuumlib.api.config;

import com.kyroxova.continuumlib.model.diagnostic.DiagnosticCode;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.Loader;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationValidatorTest {
    private static final EnvironmentId FORGE_1182 =
            new EnvironmentId("1.18.2", Loader.FORGE, MappingNamespace.MOJMAP, 17);

    @Test
    void acceptsExplicitForge1182Configuration() {
        var config = new ContinuumLibConfiguration(
                FORGE_1182,
                List.of(new EnvironmentId("1.20.1", Loader.FORGE, MappingNamespace.MOJMAP, 17)),
                new OutputConfiguration(true, true));

        assertTrue(new ConfigurationValidator().validate(config).isEmpty());
    }

    @Test
    void rejectsMissingEnvironmentDataInsteadOfDefaulting() {
        assertThrows(IllegalArgumentException.class,
                () -> new EnvironmentId(" ", Loader.FORGE, MappingNamespace.MOJMAP, 17));
        assertThrows(NullPointerException.class,
                () -> new ContinuumLibConfiguration(null, List.of(), new OutputConfiguration(true, false)));
    }

    @Test
    void rejectsDuplicateAndBaseTargets() {
        var target = new EnvironmentId("1.20.1", Loader.FORGE, MappingNamespace.MOJMAP, 17);
        var config = new ContinuumLibConfiguration(
                FORGE_1182, List.of(target, target, FORGE_1182), new OutputConfiguration(true, false));

        var codes = new ConfigurationValidator().validate(config).stream().map(d -> d.code()).toList();
        assertEquals(List.of(DiagnosticCode.DUPLICATE_TARGET, DiagnosticCode.BASE_REPEATED_AS_TARGET), codes);
    }

    @Test
    void requiresAtLeastOneOutputMode() {
        var config = new ContinuumLibConfiguration(
                FORGE_1182, List.of(), new OutputConfiguration(false, false));

        var diagnostics = new ConfigurationValidator().validate(config);
        assertFalse(diagnostics.isEmpty());
        assertEquals(DiagnosticCode.NO_OUTPUT_MODE, diagnostics.get(0).code());
    }
}
