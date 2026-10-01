package com.kyroxova.continuumlib.cir;

import java.util.Objects;

/**
 * Version-independent semantic resource identifier.
 * Represents RESOURCE_ID { namespace, path }.
 * Never contains ResourceLocation or Identifier directly.
 */
public final class CirResourceId implements CirSemanticNode {

    private final String namespace;
    private final String path;

    public CirResourceId(String namespace, String path) {
        this.namespace = Objects.requireNonNull(namespace, "namespace cannot be null");
        this.path = Objects.requireNonNull(path, "path cannot be null");
    }

    public static CirResourceId of(String fullId) {
        if (fullId == null) {
            return new CirResourceId("minecraft", "empty");
        }
        int idx = fullId.indexOf(':');
        if (idx >= 0) {
            return new CirResourceId(fullId.substring(0, idx), fullId.substring(idx + 1));
        }
        return new CirResourceId("minecraft", fullId);
    }

    public static CirResourceId of(String namespace, String path) {
        return new CirResourceId(namespace, path);
    }

    public String getNamespace() {
        return namespace;
    }

    public String getPath() {
        return path;
    }

    @Override
    public String getSemanticType() {
        return "RESOURCE_ID";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CirResourceId that = (CirResourceId) o;
        return Objects.equals(namespace, that.namespace) && Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, path);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
