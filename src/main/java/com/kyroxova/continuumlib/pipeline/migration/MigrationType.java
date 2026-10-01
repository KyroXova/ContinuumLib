package com.kyroxova.continuumlib.pipeline.migration;

public enum MigrationType {
    CLASS_RENAME,
    MEMBER_RENAME,
    FIELD_RENAME,
    CONSTRUCTOR_TO_FACTORY,
    FACTORY_TO_CONSTRUCTOR,
    FIELD_TO_ACCESSOR,
    CALL_BRIDGE
}
