package com.kyroxova.continuumlib.model.project;

import com.kyroxova.continuumlib.model.diagnostic.Diagnostic;
import com.kyroxova.continuumlib.model.operation.SemanticOperation;

import java.util.List;
import java.util.Objects;

public record ProjectModel(List<SemanticOperation> operations, List<Diagnostic> diagnostics) {
    public ProjectModel {
        operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    public <T extends SemanticOperation> List<T> operations(Class<T> type) {
        return operations.stream().filter(type::isInstance).map(type::cast).toList();
    }
}
