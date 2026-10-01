package com.kyroxova.continuumlib.cir;

import java.util.ArrayList;
import java.util.List;

/**
 * Version-independent representation of item behavior properties.
 */
public final class CirItemProperties implements CirSemanticNode {

    private String creativeTab;
    private Integer maxStackSize;
    private Integer maxDamage;
    private final List<String> additionalChainedCalls = new ArrayList<>();

    public CirItemProperties() {
    }

    @Override
    public String getSemanticType() {
        return "ITEM_PROPERTIES";
    }

    public String getCreativeTab() {
        return creativeTab;
    }

    public CirItemProperties setCreativeTab(String creativeTab) {
        this.creativeTab = creativeTab;
        return this;
    }

    public Integer getMaxStackSize() {
        return maxStackSize;
    }

    public CirItemProperties setMaxStackSize(Integer maxStackSize) {
        this.maxStackSize = maxStackSize;
        return this;
    }

    public Integer getMaxDamage() {
        return maxDamage;
    }

    public CirItemProperties setMaxDamage(Integer maxDamage) {
        this.maxDamage = maxDamage;
        return this;
    }

    public List<String> getAdditionalChainedCalls() {
        return additionalChainedCalls;
    }

    public CirItemProperties addChainedCall(String call) {
        if (call != null && !call.isBlank()) {
            this.additionalChainedCalls.add(call);
        }
        return this;
    }

    @Override
    public String toString() {
        return String.format("CirItemProperties{tab='%s', maxStack=%s, maxDamage=%s}",
                creativeTab, maxStackSize, maxDamage);
    }
}
