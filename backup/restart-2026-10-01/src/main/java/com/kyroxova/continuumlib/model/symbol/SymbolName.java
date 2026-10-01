package com.kyroxova.continuumlib.model.symbol;

import java.util.List;
import java.util.Objects;

public record SymbolName(String stableId, List<SymbolKey> aliases) {
    public SymbolName {
        Objects.requireNonNull(stableId, "stableId");
        if (stableId.isBlank()) {
            throw new IllegalArgumentException("stableId cannot be blank");
        }
        aliases = List.copyOf(Objects.requireNonNull(aliases, "aliases"));
        if (aliases.isEmpty()) {
            throw new IllegalArgumentException("aliases cannot be empty");
        }
    }
}
