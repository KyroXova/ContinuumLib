package com.kyroxova.continuumlib.cir;

import java.util.Objects;

public final class CirCreativeTabDefinition implements CirRegisteredEntry {

    private final String registrationId;
    private final String fieldName;
    private final String registryFieldName;
    private final String iconItemExpression;
    private final String titleKey;
    private final boolean isStatic;
    private final boolean isFinal;

    public CirCreativeTabDefinition(String registrationId, String fieldName, String registryFieldName,
                                    String iconItemExpression, String titleKey,
                                    boolean isStatic, boolean isFinal) {
        this.registrationId = Objects.requireNonNull(registrationId, "registrationId cannot be null");
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName cannot be null");
        this.registryFieldName = registryFieldName != null ? registryFieldName : "CREATIVE_MODE_TABS";
        this.iconItemExpression = iconItemExpression != null ? iconItemExpression : "Items.AIR";
        this.titleKey = titleKey != null ? titleKey : ("itemGroup." + registrationId);
        this.isStatic = isStatic;
        this.isFinal = isFinal;
    }

    @Override
    public String getSemanticType() {
        return "REGISTER_CREATIVE_TAB";
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
        return "CreativeModeTab";
    }

    public String getIconItemExpression() {
        return iconItemExpression;
    }

    public String getTitleKey() {
        return titleKey;
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
