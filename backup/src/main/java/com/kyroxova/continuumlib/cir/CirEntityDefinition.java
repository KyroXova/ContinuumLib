package com.kyroxova.continuumlib.cir;

import java.util.Objects;

public final class CirEntityDefinition implements CirRegisteredEntry {

    private final String registrationId;
    private final String fieldName;
    private final String registryFieldName;
    private final String entityClass;
    private final String mobCategory;
    private final Float width;
    private final Float height;
    private final Integer clientTrackingRange;
    private final Integer updateInterval;
    private final boolean isStatic;
    private final boolean isFinal;

    public CirEntityDefinition(String registrationId, String fieldName, String registryFieldName,
                               String entityClass, String mobCategory, Float width, Float height,
                               Integer clientTrackingRange, Integer updateInterval,
                               boolean isStatic, boolean isFinal) {
        this.registrationId = Objects.requireNonNull(registrationId, "registrationId cannot be null");
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName cannot be null");
        this.registryFieldName = Objects.requireNonNull(registryFieldName, "registryFieldName cannot be null");
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass cannot be null");
        this.mobCategory = mobCategory != null ? mobCategory : "MISC";
        this.width = width;
        this.height = height;
        this.clientTrackingRange = clientTrackingRange;
        this.updateInterval = updateInterval;
        this.isStatic = isStatic;
        this.isFinal = isFinal;
    }

    @Override
    public String getSemanticType() {
        return "REGISTER_ENTITY";
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
        return "EntityType<" + entityClass + ">";
    }

    public String getEntityClass() {
        return entityClass;
    }

    public String getMobCategory() {
        return mobCategory;
    }

    public Float getWidth() {
        return width;
    }

    public Float getHeight() {
        return height;
    }

    public Integer getClientTrackingRange() {
        return clientTrackingRange;
    }

    public Integer getUpdateInterval() {
        return updateInterval;
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
        return String.format("CirEntityDefinition{id='%s', class='%s', field='%s'}",
                registrationId, entityClass, fieldName);
    }
}
