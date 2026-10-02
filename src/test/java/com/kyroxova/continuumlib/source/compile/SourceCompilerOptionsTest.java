package com.kyroxova.continuumlib.source.compile;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SourceCompilerOptionsTest {
    @Test
    void java8ExternalCompilerUsesSourceAndTargetFlags() {
        assertEquals(
                List.of("-source", "8", "-target", "8"),
                SourceCompiler.externalLanguageLevelOptions(8)
        );
    }

    @Test
    void java9PlusExternalCompilerUsesReleaseFlag() {
        assertEquals(
                List.of("--release", "17"),
                SourceCompiler.externalLanguageLevelOptions(17)
        );
    }

    @Test
    void rejectsUnsupportedPreJava8Targets() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SourceCompiler.externalLanguageLevelOptions(7)
        );
    }
}
