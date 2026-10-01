package com.kyroxova.continuumlib.cir;

public final class CirAliasDefinition implements CirRegisteredEntry {

    private final String fieldName;
    private final String targetFieldName;
    private final String implementationType;
    private final String registryFieldName;
    private final boolean isStatic;
    private final boolean isFinal;

    public CirAliasDefinition(String fieldName, String targetFieldName, String implementationType,
                              String registryFieldName, boolean isStatic, boolean isFinal) {
        this.fieldName = fieldName;
        this.targetFieldName = targetFieldName;
        this.implementationType = implementationType;
        this.registryFieldName = registryFieldName;
        this.isStatic = isStatic;
        this.isFinal = isFinal;
    }

    @Override
    public String getSemanticType() {
        return "ALIAS";
    }

    @Override
    public String getRegistrationId() {
        return fieldName;
    }

    @Override
    public String getFieldName() {
        return fieldName;
    }

    public String getTargetFieldName() {
        return targetFieldName;
    }

    @Override
    public String getRegistryFieldName() {
        return registryFieldName;
    }

    @Override
    public String getImplementationType() {
        return implementationType;
    }

    @Override
    public boolean isStatic() {
        return isStatic;
    }

    @Override
    public boolean isFinal() {
        return isFinal;
    }
}
