package com.kyroxova.continuumlib.model.environment;

public enum Loader {
    FORGE, NEOFORGE, FABRIC, QUILT,
    /** Vanilla API-only migrations; does not claim loader entrypoint adaptation. */
    VANILLA
}
