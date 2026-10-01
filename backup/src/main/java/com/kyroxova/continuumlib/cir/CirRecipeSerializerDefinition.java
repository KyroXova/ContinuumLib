package com.kyroxova.continuumlib.cir;

import java.util.Objects;

public final class CirRecipeSerializerDefinition implements CirRegisteredEntry {

    private final String registrationId;
    private final String fieldName;
    private final String registryFieldName;
    private final String recipeClass;
    private final boolean isSimple;
    private final String serializerReference;
    private final boolean isStatic;
    private final boolean isFinal;

    public CirRecipeSerializerDefinition(String registrationId, String fieldName, String registryFieldName,
                                         String recipeClass, boolean isSimple, String serializerReference,
                                         boolean isStatic, boolean isFinal) {
        this.registrationId = Objects.requireNonNull(registrationId, "registrationId cannot be null");
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName cannot be null");
        this.registryFieldName = Objects.requireNonNull(registryFieldName, "registryFieldName cannot be null");
        this.recipeClass = recipeClass != null ? recipeClass : "Recipe";
        this.isSimple = isSimple;
        this.serializerReference = serializerReference;
        this.isStatic = isStatic;
        this.isFinal = isFinal;
    }

    @Override
    public String getSemanticType() {
        return "REGISTER_RECIPE_SERIALIZER";
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
        return "RecipeSerializer<" + recipeClass + ">";
    }

    public String getRecipeClass() {
        return recipeClass;
    }

    public boolean isSimple() {
        return isSimple;
    }

    public String getSerializerReference() {
        return serializerReference;
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
        return String.format("CirRecipeSerializerDefinition{id='%s', recipe='%s', field='%s'}",
                registrationId, recipeClass, fieldName);
    }
}
