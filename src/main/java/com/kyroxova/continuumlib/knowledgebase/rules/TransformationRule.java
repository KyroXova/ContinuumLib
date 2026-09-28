package com.kyroxova.continuumlib.knowledgebase.rules;

import com.kyroxova.bootstrapper.config.TargetSpec;

/**
 * Base contract for all API transformation rules in ContinuumLib.
 */
public interface TransformationRule {

    /**
     * Determines whether this rule applies when transitioning from base to target.
     */
    boolean appliesTo(TargetSpec baseSpec, TargetSpec targetSpec);

    /**
     * Human-readable description of the API breaking change being resolved.
     */
    String getDescription();
}
