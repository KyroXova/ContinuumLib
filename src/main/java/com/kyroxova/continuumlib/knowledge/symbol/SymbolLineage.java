package com.kyroxova.continuumlib.knowledge.symbol;

import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import java.util.*;

public final class SymbolLineage {
    private final SymbolId id;
    private final Map<EnvironmentId, SymbolVersion> versions;

    public SymbolLineage(SymbolId id, Map<EnvironmentId, SymbolVersion> versions) {
        this.id = Objects.requireNonNull(id, "id");
        this.versions = Map.copyOf(versions);
    }

    public SymbolId id() { return id; }

    public Optional<SymbolVersion> resolve(EnvironmentId environment) {
        return Optional.ofNullable(versions.get(Objects.requireNonNull(environment, "environment")));
    }

    public Map<EnvironmentId, SymbolVersion> versions() { return versions; }

    public static Builder builder(SymbolId id) { return new Builder(id); }

    public static final class Builder {
        private final SymbolId id;
        private final Map<EnvironmentId, SymbolVersion> versions = new LinkedHashMap<>();

        public Builder(SymbolId id) { this.id = Objects.requireNonNull(id); }

        public Builder bind(EnvironmentId environment, SymbolVersion version) {
            versions.put(Objects.requireNonNull(environment), Objects.requireNonNull(version));
            return this;
        }

        public SymbolLineage build() {
            return new SymbolLineage(id, versions);
        }
    }
}
