package com.kyroxova.continuumlib.cir;

import java.util.Objects;

public final class CirParticleDefinition implements CirRegisteredEntry {

    private final String registrationId;
    private final String fieldName;
    private final String registryFieldName;
    private final String particleClass;
    private final boolean alwaysShow;
    private final boolean isStatic;
    private final boolean isFinal;

    public CirParticleDefinition(String registrationId, String fieldName, String registryFieldName,
                                 String particleClass, boolean alwaysShow, boolean isStatic, boolean isFinal) {
        this.registrationId = Objects.requireNonNull(registrationId, "registrationId cannot be null");
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName cannot be null");
        this.registryFieldName = Objects.requireNonNull(registryFieldName, "registryFieldName cannot be null");
        this.particleClass = particleClass != null ? particleClass : "SimpleParticleType";
        this.alwaysShow = alwaysShow;
        this.isStatic = isStatic;
        this.isFinal = isFinal;
    }

    @Override
    public String getSemanticType() {
        return "REGISTER_PARTICLE";
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
        return particleClass;
    }

    public boolean isAlwaysShow() {
        return alwaysShow;
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
        return String.format("CirParticleDefinition{id='%s', class='%s', field='%s'}",
                registrationId, particleClass, fieldName);
    }
}
