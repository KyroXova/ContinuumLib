package com.kyroxova.continuumlib.cir;

import java.util.*;

/**
 * Version-independent representation of block behavior properties.
 * Captures semantic property values without binding to Material or BlockBehaviour.Properties.
 */
public final class CirBlockProperties implements CirSemanticNode {

    private String material; // e.g. "STONE", "WOOD", "METAL" (or null if modern)
    private Float destroyTime; // hardness
    private Float explosionResistance; // resistance
    private Boolean requiresCorrectToolForDrops;
    private Boolean noOcclusion;
    private String soundType; // e.g. "STONE", "WOOD"
    private Integer lightEmission;
    private final List<String> additionalChainedCalls = new ArrayList<>();

    public CirBlockProperties() {
    }

    @Override
    public String getSemanticType() {
        return "BLOCK_PROPERTIES";
    }

    public String getMaterial() {
        return material;
    }

    public CirBlockProperties setMaterial(String material) {
        this.material = material;
        return this;
    }

    public Float getDestroyTime() {
        return destroyTime;
    }

    public CirBlockProperties setDestroyTime(Float destroyTime) {
        this.destroyTime = destroyTime;
        return this;
    }

    public Float getExplosionResistance() {
        return explosionResistance;
    }

    public CirBlockProperties setExplosionResistance(Float explosionResistance) {
        this.explosionResistance = explosionResistance;
        return this;
    }

    public CirBlockProperties setStrength(float strength) {
        this.destroyTime = strength;
        this.explosionResistance = strength;
        return this;
    }

    public CirBlockProperties setStrength(float destroyTime, float explosionResistance) {
        this.destroyTime = destroyTime;
        this.explosionResistance = explosionResistance;
        return this;
    }

    public Boolean getRequiresCorrectToolForDrops() {
        return requiresCorrectToolForDrops;
    }

    public CirBlockProperties setRequiresCorrectToolForDrops(Boolean requiresCorrectToolForDrops) {
        this.requiresCorrectToolForDrops = requiresCorrectToolForDrops;
        return this;
    }

    public Boolean getNoOcclusion() {
        return noOcclusion;
    }

    public CirBlockProperties setNoOcclusion(Boolean noOcclusion) {
        this.noOcclusion = noOcclusion;
        return this;
    }

    public String getSoundType() {
        return soundType;
    }

    public CirBlockProperties setSoundType(String soundType) {
        this.soundType = soundType;
        return this;
    }

    public Integer getLightEmission() {
        return lightEmission;
    }

    public CirBlockProperties setLightEmission(Integer lightEmission) {
        this.lightEmission = lightEmission;
        return this;
    }

    public List<String> getAdditionalChainedCalls() {
        return additionalChainedCalls;
    }

    public CirBlockProperties addChainedCall(String call) {
        if (call != null && !call.isBlank()) {
            this.additionalChainedCalls.add(call);
        }
        return this;
    }

    @Override
    public String toString() {
        return String.format("CirBlockProperties{material='%s', destroyTime=%s, resistance=%s}",
                material, destroyTime, explosionResistance);
    }
}
