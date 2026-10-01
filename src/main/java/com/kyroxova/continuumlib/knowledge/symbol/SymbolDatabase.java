package com.kyroxova.continuumlib.knowledge.symbol;

import com.kyroxova.continuumlib.model.symbol.SymbolKey;
import com.kyroxova.continuumlib.model.symbol.SymbolName;

import java.util.Optional;

public interface SymbolDatabase {
    Optional<SymbolName> find(SymbolKey key);
}
