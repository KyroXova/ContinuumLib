package com.kyroxova.continuumlib.knowledge.symbol;

import com.kyroxova.continuumlib.model.symbol.SymbolKey;
import com.kyroxova.continuumlib.model.symbol.SymbolName;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class InMemorySymbolDatabase implements SymbolDatabase {
    private final Map<SymbolKey, SymbolName> byAlias;

    public InMemorySymbolDatabase(Collection<SymbolName> symbols) {
        Objects.requireNonNull(symbols, "symbols");
        Map<SymbolKey, SymbolName> index = new LinkedHashMap<>();
        for (SymbolName symbol : symbols) {
            for (SymbolKey alias : symbol.aliases()) {
                SymbolName previous = index.putIfAbsent(alias, symbol);
                if (previous != null && !previous.equals(symbol)) {
                    throw new IllegalArgumentException("Symbol alias collision: " + alias);
                }
            }
        }
        byAlias = Map.copyOf(index);
    }

    @Override
    public Optional<SymbolName> find(SymbolKey key) {
        return Optional.ofNullable(byAlias.get(Objects.requireNonNull(key, "key")));
    }
}
