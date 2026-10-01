package com.kyroxova.continuumlib.model.operation;

import java.util.Objects;

public record SourceExpression(String source) {
    public SourceExpression {
        Objects.requireNonNull(source, "source");
        source = source.trim();
        if (source.isEmpty()) {
            throw new IllegalArgumentException("source cannot be blank");
        }
    }
}
