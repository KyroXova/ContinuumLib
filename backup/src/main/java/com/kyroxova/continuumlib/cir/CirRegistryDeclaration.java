package com.kyroxova.continuumlib.cir;

import java.util.Objects;

public final class CirRegistryDeclaration implements CirSemanticNode {

    private final CirRegistryType registryType;
    private final String modId;
    private final String rawModIdExpression;
    private final String fieldName;
    private final String enclosingClassName;
    private final boolean isStatic;
    private final boolean isFinal;

    public CirRegistryDeclaration(CirRegistryType registryType, String modId, String fieldName,
                                  String enclosingClassName, boolean isStatic, boolean isFinal) {
        this(registryType, modId, null, fieldName, enclosingClassName, isStatic, isFinal);
    }

    public CirRegistryDeclaration(CirRegistryType registryType, String modId, String rawModIdExpression,
                                  String fieldName, String enclosingClassName, boolean isStatic, boolean isFinal) {
        this.registryType = Objects.requireNonNull(registryType, "registryType cannot be null");
        this.modId = Objects.requireNonNull(modId, "modId cannot be null");
        this.rawModIdExpression = rawModIdExpression;
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName cannot be null");
        this.enclosingClassName = enclosingClassName != null ? enclosingClassName : "";
        this.isStatic = isStatic;
        this.isFinal = isFinal;
    }

    @Override
    public String getSemanticType() {
        return "REGISTER_REGISTRY";
    }

    public CirRegistryType getRegistryType() {
        return registryType;
    }

    public String getModId() {
        return modId;
    }

    public String getRawModIdExpression() {
        return rawModIdExpression;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getEnclosingClassName() {
        return enclosingClassName;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public boolean isFinal() {
        return isFinal;
    }

    @Override
    public String toString() {
        return String.format("CirRegistryDeclaration{%s, modId='%s', expr='%s', field='%s'}",
                registryType, modId, rawModIdExpression, fieldName);
    }
}
