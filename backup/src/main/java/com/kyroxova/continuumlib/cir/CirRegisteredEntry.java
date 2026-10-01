package com.kyroxova.continuumlib.cir;

/**
 * Common interface for entries registered into a game registry.
 */
public interface CirRegisteredEntry extends CirSemanticNode {

    String getRegistrationId();

    String getFieldName();

    String getRegistryFieldName();

    String getImplementationType();

    boolean isStatic();

    boolean isFinal();
}
