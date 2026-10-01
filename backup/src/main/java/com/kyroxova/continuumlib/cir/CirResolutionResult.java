package com.kyroxova.continuumlib.cir;

import java.util.Objects;

/**
 * Diagnostic result record for a resolved semantic operation.
 */
public final class CirResolutionResult {

    private final CirResolutionStatus status;
    private final String sourceOperation;
    private final String targetOperation;
    private final String diagnosticMessage;
    private final String sourceLocation;
    private final String suggestedAction;

    public CirResolutionResult(CirResolutionStatus status, String sourceOperation, String targetOperation,
                               String diagnosticMessage, String sourceLocation, String suggestedAction) {
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.sourceOperation = sourceOperation != null ? sourceOperation : "";
        this.targetOperation = targetOperation != null ? targetOperation : "";
        this.diagnosticMessage = diagnosticMessage != null ? diagnosticMessage : "";
        this.sourceLocation = sourceLocation != null ? sourceLocation : "";
        this.suggestedAction = suggestedAction != null ? suggestedAction : "";
    }

    public static CirResolutionResult exact(String operation, String location) {
        return new CirResolutionResult(CirResolutionStatus.EXACT, operation, operation, "Direct equivalent", location, "");
    }

    public static CirResolutionResult adapted(String sourceOp, String targetOp, String message, String location) {
        return new CirResolutionResult(CirResolutionStatus.ADAPTED, sourceOp, targetOp, message, location, "");
    }

    public static CirResolutionResult emulated(String sourceOp, String targetOp, String message, String location) {
        return new CirResolutionResult(CirResolutionStatus.EMULATED, sourceOp, targetOp, message, location, "Uses ContinuumLib runtime shim");
    }

    public static CirResolutionResult degraded(String sourceOp, String targetOp, String message, String location, String suggestion) {
        return new CirResolutionResult(CirResolutionStatus.DEGRADED, sourceOp, targetOp, message, location, suggestion);
    }

    public static CirResolutionResult unsupported(String sourceOp, String message, String location, String suggestion) {
        return new CirResolutionResult(CirResolutionStatus.UNSUPPORTED, sourceOp, "", message, location, suggestion);
    }

    public CirResolutionStatus getStatus() {
        return status;
    }

    public String getSourceOperation() {
        return sourceOperation;
    }

    public String getTargetOperation() {
        return targetOperation;
    }

    public String getDiagnosticMessage() {
        return diagnosticMessage;
    }

    public String getSourceLocation() {
        return sourceLocation;
    }

    public String getSuggestedAction() {
        return suggestedAction;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s -> %s (%s)", status, sourceOperation, targetOperation, diagnosticMessage);
    }
}
