package com.kyroxova.continuumlib.cir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Version-independent representation of a registered block entity type.
 * Represents:
 * REGISTER_BLOCK_ENTITY {
 *     id = "example_block_entity"
 *     entityClass = ExampleBlockEntity
 *     validBlocks = [ EXAMPLE_BLOCK ]
 * }
 */
public final class CirBlockEntityDefinition implements CirRegisteredEntry {

    private final String registrationId;
    private final String fieldName;
    private final String registryFieldName;
    private final String blockEntityClass;
    private final List<String> validBlockReferences;
    private final boolean isStatic;
    private final boolean isFinal;

    public CirBlockEntityDefinition(String registrationId, String fieldName, String registryFieldName,
                                    String blockEntityClass, List<String> validBlockReferences,
                                    boolean isStatic, boolean isFinal) {
        this.registrationId = Objects.requireNonNull(registrationId, "registrationId cannot be null");
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName cannot be null");
        this.registryFieldName = Objects.requireNonNull(registryFieldName, "registryFieldName cannot be null");
        this.blockEntityClass = Objects.requireNonNull(blockEntityClass, "blockEntityClass cannot be null");
        this.validBlockReferences = validBlockReferences != null ? new ArrayList<>(validBlockReferences) : new ArrayList<>();
        this.isStatic = isStatic;
        this.isFinal = isFinal;
    }

    @Override
    public String getSemanticType() {
        return "REGISTER_BLOCK_ENTITY";
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
        return "BlockEntityType<" + blockEntityClass + ">";
    }

    public String getBlockEntityClass() {
        return blockEntityClass;
    }

    public List<String> getValidBlockReferences() {
        return Collections.unmodifiableList(validBlockReferences);
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
        return String.format("CirBlockEntityDefinition{id='%s', entityClass='%s', blocks=%s}",
                registrationId, blockEntityClass, validBlockReferences);
    }
}
