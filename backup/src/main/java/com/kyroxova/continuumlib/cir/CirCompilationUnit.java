package com.kyroxova.continuumlib.cir;

import java.util.*;

/**
 * Top-level ContinuumLib Semantic IR representation of a parsed compilation unit.
 * Retains user code, custom classes, and semantic registry declarations without any
 * loader/version-specific types.
 */
public final class CirCompilationUnit implements CirSemanticNode {

    private String packageName;
    private final List<String> rawImports = new ArrayList<>();
    private String primaryClassName;
    private String superClass;
    private final List<String> implementedInterfaces = new ArrayList<>();
    private final List<CirRegistryDeclaration> registries = new ArrayList<>();
    private final List<CirRegisteredEntry> registeredEntries = new ArrayList<>();
    private final List<CirNetworkPacketDefinition> networkPackets = new ArrayList<>();
    private final List<CirEventListenerDefinition> eventListeners = new ArrayList<>();
    private final List<String> unmanagedClassBodyNodes = new ArrayList<>();

    public CirCompilationUnit() {
    }

    @Override
    public String getSemanticType() {
        return "COMPILATION_UNIT";
    }

    public String getPackageName() {
        return packageName;
    }

    public CirCompilationUnit setPackageName(String packageName) {
        this.packageName = packageName;
        return this;
    }

    public List<String> getRawImports() {
        return rawImports;
    }

    public CirCompilationUnit addImport(String importStatement) {
        this.rawImports.add(importStatement);
        return this;
    }

    public String getPrimaryClassName() {
        return primaryClassName;
    }

    public CirCompilationUnit setPrimaryClassName(String primaryClassName) {
        this.primaryClassName = primaryClassName;
        return this;
    }

    public String getSuperClass() {
        return superClass;
    }

    public CirCompilationUnit setSuperClass(String superClass) {
        this.superClass = superClass;
        return this;
    }

    public List<String> getImplementedInterfaces() {
        return implementedInterfaces;
    }

    public CirCompilationUnit addImplementedInterface(String iface) {
        this.implementedInterfaces.add(iface);
        return this;
    }

    public List<CirRegistryDeclaration> getRegistries() {
        return registries;
    }

    public CirCompilationUnit addRegistry(CirRegistryDeclaration registry) {
        this.registries.add(registry);
        return this;
    }

    public List<CirRegisteredEntry> getRegisteredEntries() {
        return registeredEntries;
    }

    public CirCompilationUnit addRegisteredEntry(CirRegisteredEntry entry) {
        this.registeredEntries.add(entry);
        return this;
    }

    public List<CirNetworkPacketDefinition> getNetworkPackets() {
        return networkPackets;
    }

    public CirCompilationUnit addNetworkPacket(CirNetworkPacketDefinition packet) {
        this.networkPackets.add(packet);
        return this;
    }

    public List<CirEventListenerDefinition> getEventListeners() {
        return eventListeners;
    }

    public CirCompilationUnit addEventListener(CirEventListenerDefinition listener) {
        this.eventListeners.add(listener);
        return this;
    }

    private final List<String> unmanagedFields = new ArrayList<>();
    private final List<String> unmanagedMethods = new ArrayList<>();

    public List<String> getUnmanagedFields() {
        return unmanagedFields;
    }

    public CirCompilationUnit addUnmanagedField(String fieldSource) {
        this.unmanagedFields.add(fieldSource);
        return this;
    }

    public List<String> getUnmanagedMethods() {
        return unmanagedMethods;
    }

    public CirCompilationUnit addUnmanagedMethod(String methodSource) {
        this.unmanagedMethods.add(methodSource);
        return this;
    }

    public List<String> getUnmanagedClassBodyNodes() {
        List<String> combined = new ArrayList<>(unmanagedFields);
        combined.addAll(unmanagedMethods);
        return combined;
    }

    public CirCompilationUnit addUnmanagedClassBodyNode(String nodeSource) {
        this.unmanagedMethods.add(nodeSource);
        return this;
    }

    public Optional<CirRegistryDeclaration> findRegistry(CirRegistryType type) {
        return registries.stream().filter(r -> r.getRegistryType() == type).findFirst();
    }

    public Optional<CirRegistryDeclaration> findRegistryByField(String fieldName) {
        return registries.stream().filter(r -> r.getFieldName().equals(fieldName)).findFirst();
    }

    @Override
    public String toString() {
        return String.format("CirCompilationUnit{%s.%s, registries=%d, entries=%d}",
                packageName, primaryClassName, registries.size(), registeredEntries.size());
    }
}
