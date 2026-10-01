package com.kyroxova.continuumlib.knowledge.discovery;

public enum CandidateType {
    UNCHANGED,
    REMOVED,
    ADDED,
    POSSIBLE_RENAME,
    POSSIBLE_MOVE,
    POSSIBLE_DESCRIPTOR_CHANGE,
    POSSIBLE_CONSTRUCTOR_TO_FACTORY,
    POSSIBLE_FIELD_TO_ACCESSOR,
    POSSIBLE_OWNER_CHANGE,
    AMBIGUOUS,
    UNKNOWN
}
