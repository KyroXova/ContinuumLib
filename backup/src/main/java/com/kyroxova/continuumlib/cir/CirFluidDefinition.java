package com.kyroxova.continuumlib.cir;

import java.util.Objects;

public final class CirFluidDefinition implements CirRegisteredEntry {

    private final String registrationId;
    private final String fieldName;
    private final String registryFieldName;
    private final String fluidClass;
    private final boolean isSource;
    private final boolean isStatic;
    private final boolean isFinal;

    public CirFluidDefinition(String registrationId, String fieldName, String registryFieldName,
                              String fluidClass, boolean isSource, boolean isStatic, boolean isFinal) {
        this.registrationId = Objects.requireNonNull(registrationId, "registrationId cannot be null");
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName cannot be null");
        this.registryFieldName = Objects.requireNonNull(registryFieldName, "registryFieldName cannot be null");
        this.fluidClass = fluidClass != null ? fluidClass : "ForgeFlowingFluid";
        this.isSource = isSource;
        this.isStatic = isStatic;
        this.isFinal = isFinal;
    }

    @Override
    public String getSemanticType() {
        return "REGISTER_FLUID";
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
        return fluidClass;
    }

    public boolean isSource() {
        return isSource;
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
        return String.format("CirFluidDefinition{id='%s', fluidClass='%s', field='%s'}",
                registrationId, fluidClass, fieldName);
    }
}
