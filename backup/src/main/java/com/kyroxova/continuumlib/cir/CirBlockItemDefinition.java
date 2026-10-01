package com.kyroxova.continuumlib.cir;

import java.util.Objects;

/**
 * Version-independent representation of a registered block item.
 * Represents:
 * REGISTER_BLOCK_ITEM {
 *     id = "example_block"
 *     blockReference = EXAMPLE_BLOCK
 *     properties { ... }
 * }
 */
public final class CirBlockItemDefinition implements CirRegisteredEntry {

    private final String registrationId;
    private final String fieldName;
    private final String registryFieldName;
    private final String blockReference;
    private final CirItemProperties properties;
    private final boolean isStatic;
    private final boolean isFinal;

    public CirBlockItemDefinition(String registrationId, String fieldName, String registryFieldName,
                                  String blockReference, CirItemProperties properties,
                                  boolean isStatic, boolean isFinal) {
        this.registrationId = Objects.requireNonNull(registrationId, "registrationId cannot be null");
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName cannot be null");
        this.registryFieldName = Objects.requireNonNull(registryFieldName, "registryFieldName cannot be null");
        this.blockReference = Objects.requireNonNull(blockReference, "blockReference cannot be null");
        this.properties = properties != null ? properties : new CirItemProperties();
        this.isStatic = isStatic;
        this.isFinal = isFinal;
    }

    @Override
    public String getSemanticType() {
        return "REGISTER_BLOCK_ITEM";
    }

    @Override
    public String getRegistrationId() {
        return registrationId;
    }

    @Override
    public String getFieldName() {
        return fieldName;
    }

    @Override
    public String getRegistryFieldName() {
        return registryFieldName;
    }

    @Override
    public String getImplementationType() {
        return "BlockItem";
    }

    public String getBlockReference() {
        return blockReference;
    }

    public CirItemProperties getProperties() {
        return properties;
    }

    @Override
    public boolean isStatic() {
        return isStatic;
    }

    @Override
    public boolean isFinal() {
        return isFinal;
    }

    @Override
    public String toString() {
        return String.format("CirBlockItemDefinition{id='%s', blockRef='%s', field='%s'}",
                registrationId, blockReference, fieldName);
    }
}
