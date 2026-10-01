package com.kyroxova.continuumlib.cir;

/**
 * Resolution status classification for resolved semantic operations.
 */
public enum CirResolutionStatus {
    /**
     * Target provides directly equivalent semantics.
     */
    EXACT,

    /**
     * API differs but behavior can be safely translated/adapted.
     */
    ADAPTED,

    /**
     * ContinuumLib runtime must reproduce missing behavior.
     */
    EMULATED,

    /**
     * Only partial equivalent is possible.
     */
    DEGRADED,

    /**
     * No safe equivalent exists in target version.
     */
    UNSUPPORTED
}
