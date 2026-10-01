package com.kyroxova.continuumlib.knowledge.evidence;

public enum VerificationStatus {
    DISCOVERED,
    STRUCTURALLY_MATCHED,
    REVIEW_REQUIRED,
    VERIFIED_TRANSFORMATION,
    VERIFIED_LINKAGE,
    VERIFIED_RUNTIME,
    VERIFIED_BEHAVIOR,
    REJECTED;

    public boolean isVerified() {
        return this == VERIFIED_TRANSFORMATION
                || this == VERIFIED_LINKAGE
                || this == VERIFIED_RUNTIME
                || this == VERIFIED_BEHAVIOR;
    }
}
