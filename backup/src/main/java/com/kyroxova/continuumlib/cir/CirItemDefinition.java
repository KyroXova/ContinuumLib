package com.kyroxova.continuumlib.cir;

import java.util.Objects;

/**
 * Version-independent representation of a registered item.
 * Represents:
 * REGISTER_ITEM {
 *     id = "example_item"
 *     implementation = Item (or MyCustomItem)
 *     properties { ... }
 * }
 */
public final class CirItemDefinition implements CirRegisteredEntry {

    private final String registrationId;
    private final String fieldName;
    private final String registryFieldName;
    private final String implementationType;
    private final CirItemProperties properties;
    private final boolean isStatic;
    private final boolean isFinal;

    public CirItemDefinition(String registrationId, String fieldName, String registryFieldName,
                             String implementationType, CirItemProperties properties,
                             boolean isStatic, boolean isFinal) {
        this.registrationId = Objects.requireNonNull(registrationId, "registrationId cannot be null");
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName cannot be null");
        this.registryFieldName = Objects.requireNonNull(registryFieldName, "registryFieldName cannot be null");
        this.implementationType = (implementationType != null && !implementationType.isBlank()) ? implementationType : "Item";
        this.properties = properties != null ? properties : new CirItemProperties();
        this.isStatic = isStatic;
        this.isFinal = isFinal;
    }

    @Override
    public String getSemanticType() {
        return "REGISTER_ITEM";
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
        return implementationType;
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

    public boolean isStandardItem() {
        return "Item".equals(implementationType) || "net.minecraft.world.item.Item".equals(implementationType);
    }

    @Override
    public String toString() {
        return String.format("CirItemDefinition{id='%s', impl='%s', field='%s', properties=%s}",
                registrationId, implementationType, fieldName, properties);
    }
}
