package com.kyroxova.continuumlib.cir;

import java.util.Objects;

public final class CirSoundDefinition implements CirRegisteredEntry {

    private final String registrationId;
    private final String fieldName;
    private final String registryFieldName;
    private final String soundResourcePath;
    private final boolean isStatic;
    private final boolean isFinal;

    public CirSoundDefinition(String registrationId, String fieldName, String registryFieldName,
                              String soundResourcePath, boolean isStatic, boolean isFinal) {
        this.registrationId = Objects.requireNonNull(registrationId, "registrationId cannot be null");
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName cannot be null");
        this.registryFieldName = Objects.requireNonNull(registryFieldName, "registryFieldName cannot be null");
        this.soundResourcePath = soundResourcePath != null ? soundResourcePath : registrationId;
        this.isStatic = isStatic;
        this.isFinal = isFinal;
    }

    @Override
    public String getSemanticType() {
        return "REGISTER_SOUND";
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
        return "SoundEvent";
    }

    public String getSoundResourcePath() {
        return soundResourcePath;
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
        return String.format("CirSoundDefinition{id='%s', path='%s', field='%s'}",
                registrationId, soundResourcePath, fieldName);
    }
}
