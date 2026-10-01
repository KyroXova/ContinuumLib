package com.kyroxova.continuumlib.resolver;

import com.kyroxova.continuumlib.model.diagnostic.*;
import com.kyroxova.continuumlib.model.environment.*;
import com.kyroxova.continuumlib.model.project.ProjectModel;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ResolutionFailureTest {
    @Test void emptyAnalysisCannotClaimCompatibility() {
        var env = new EnvironmentId("1.18.2", Loader.FORGE, MappingNamespace.MOJMAP, 17);
        assertFalse(new ContinuumLibResolver(List.of()).resolve(new ProjectModel(List.of(), List.of()), env, env).isComplete());
    }
    @Test void preservesAnalysisErrors() {
        var env = new EnvironmentId("1.18.2", Loader.FORGE, MappingNamespace.MOJMAP, 17);
        var error = new Diagnostic(DiagnosticCode.UNRESOLVED_OPERATION, Severity.ERROR, "Unresolved source symbol");
        var plan = new ContinuumLibResolver(List.of()).resolve(new ProjectModel(List.of(), List.of(error)), env, env);
        assertFalse(plan.isComplete());
        assertEquals(List.of(error), plan.diagnostics());
    }
}
