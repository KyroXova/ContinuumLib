package com.kyroxova.continuumlib.model.operation;

public record BlockProperties(
        SourceExpression material,
        SourceExpression mapColor,
        Double strength,
        SourceExpression sound
) {
}
