package com.kyroxova.continuumlib.cir;

/**
 * Base marker interface for all ContinuumLib Semantic Intermediate Representation (CIR) nodes.
 * STRICT RULE: CIR nodes represent WHAT code does, never HOW a particular Minecraft/loader
 * version implements it. CIR must NOT contain loader/version-specific types.
 */
public interface CirSemanticNode {
    String getSemanticType();
}
