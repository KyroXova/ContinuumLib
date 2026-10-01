package com.kyroxova.continuumlib.knowledge.mapping;

import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.symbol.SymbolName;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

public interface MappingImporter {
    Collection<SymbolName> importMappings(
            InputStream input,
            EnvironmentId environment,
            MappingProvenance provenance
    ) throws IOException;
}
