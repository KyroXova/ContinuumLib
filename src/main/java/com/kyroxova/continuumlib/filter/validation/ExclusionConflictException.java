package com.kyroxova.continuumlib.filter.validation;

/**
 * Thrown when an excluded project element is still referenced by target-enabled code.
 */
public final class ExclusionConflictException extends RuntimeException {
    private final String targetEnvironment;
    private final String excludedElement;
    private final String declaration;
    private final String remainingReference;

    public ExclusionConflictException(String targetEnvironment, String excludedElement,
                                      String declaration, String remainingReference) {
        super(formatMessage(targetEnvironment, excludedElement, declaration, remainingReference));
        this.targetEnvironment = targetEnvironment;
        this.excludedElement = excludedElement;
        this.declaration = declaration;
        this.remainingReference = remainingReference;
    }

    private static String formatMessage(String target, String element, String decl, String ref) {
        return """
                CONTINUUM EXCLUSION CONFLICT

                Target:
                %s

                Excluded element:
                %s

                Declaration:
                %s

                Remaining reference:
                %s

                The excluded registry element is still referenced by target-enabled source.
                """.formatted(target, element, decl, ref).trim();
    }

    public String targetEnvironment() { return targetEnvironment; }
    public String excludedElement() { return excludedElement; }
    public String declaration() { return declaration; }
    public String remainingReference() { return remainingReference; }
}
