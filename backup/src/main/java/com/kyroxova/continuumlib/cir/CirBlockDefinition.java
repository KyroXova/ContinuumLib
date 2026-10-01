package com.kyroxova.continuumlib.cir;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Version-independent representation of a registered block.
 * Represents:
 * REGISTER_BLOCK {
 *     id = "example_block"
 *     implementation = Block (or MyMachineBlock)
 *     properties {
 *         material = STONE
 *         strength = 2.0
 *     }
 * }
 */
public final class CirBlockDefinition implements CirRegisteredEntry {

    private final String registrationId;
    private final String fieldName;
    private final String registryFieldName;
    private final String implementationType;
    private final CirBlockProperties properties;
    private final List<String> additionalConstructorArgs = new ArrayList<>();
    private final boolean isStatic;
    private final boolean isFinal;

    public CirBlockDefinition(String registrationId, String fieldName, String registryFieldName,
                              String implementationType, CirBlockProperties properties,
                              boolean isStatic, boolean isFinal) {
        this.registrationId = Objects.requireNonNull(registrationId, "registrationId cannot be null");
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName cannot be null");
        this.registryFieldName = Objects.requireNonNull(registryFieldName, "registryFieldName cannot be null");
        this.implementationType = (implementationType != null && !implementationType.isBlank()) ? implementationType : "Block";
        this.properties = properties != null ? properties : new CirBlockProperties();
        this.isStatic = isStatic;
        this.isFinal = isFinal;
    }

    @Override
    public String getSemanticType() {
        return "REGISTER_BLOCK";
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

    public CirBlockProperties getProperties() {
        return properties;
    }

    public List<String> getAdditionalConstructorArgs() {
        return additionalConstructorArgs;
    }

    public CirBlockDefinition addConstructorArg(String arg) {
        this.additionalConstructorArgs.add(arg);
        return this;
    }

    @Override
    public boolean isStatic() {
        return isStatic;
    }

    @Override
    public boolean isFinal() {
        return isFinal;
    }

    public boolean isStandardBlock() {
        return "Block".equals(implementationType) || "net.minecraft.world.level.block.Block".equals(implementationType);
    }

    @Override
    public String toString() {
        return String.format("CirBlockDefinition{id='%s', impl='%s', field='%s', properties=%s}",
                registrationId, implementationType, fieldName, properties);
    }
}
