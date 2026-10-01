package com.kyroxova.continuumlib.cir;

import java.util.Objects;

public final class CirEventListenerDefinition implements CirSemanticNode {

    private final String methodName;
    private final String rawEventType;
    private final String semanticEventCategory;
    private final boolean isStatic;

    public CirEventListenerDefinition(String methodName, String rawEventType,
                                      String semanticEventCategory, boolean isStatic) {
        this.methodName = Objects.requireNonNull(methodName, "methodName cannot be null");
        this.rawEventType = Objects.requireNonNull(rawEventType, "rawEventType cannot be null");
        this.semanticEventCategory = semanticEventCategory != null ? semanticEventCategory : "GENERIC_EVENT";
        this.isStatic = isStatic;
    }

    @Override
    public String getSemanticType() {
        return "LISTEN_EVENT";
    }

    public String getMethodName() {
        return methodName;
    }

    public String getRawEventType() {
        return rawEventType;
    }

    public String getSemanticEventCategory() {
        return semanticEventCategory;
    }

    public boolean isStatic() {
        return isStatic;
    }
}
