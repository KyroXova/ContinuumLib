package com.kyroxova.continuumlib.cir;

import java.util.Objects;

public final class CirMenuDefinition implements CirRegisteredEntry {

    private final String registrationId;
    private final String fieldName;
    private final String registryFieldName;
    private final String menuClass;
    private final boolean isStatic;
    private final boolean isFinal;

    public CirMenuDefinition(String registrationId, String fieldName, String registryFieldName,
                             String menuClass, boolean isStatic, boolean isFinal) {
        this.registrationId = Objects.requireNonNull(registrationId, "registrationId cannot be null");
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName cannot be null");
        this.registryFieldName = Objects.requireNonNull(registryFieldName, "registryFieldName cannot be null");
        this.menuClass = Objects.requireNonNull(menuClass, "menuClass cannot be null");
        this.isStatic = isStatic;
        this.isFinal = isFinal;
    }

    @Override
    public String getSemanticType() {
        return "REGISTER_MENU";
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
        return "MenuType<" + menuClass + ">";
    }

    public String getMenuClass() {
        return menuClass;
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
        return String.format("CirMenuDefinition{id='%s', menuClass='%s', field='%s'}",
                registrationId, menuClass, fieldName);
    }
}
