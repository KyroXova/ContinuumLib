package com.kyroxova.continuumlib.source.compile;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SourceCompilerOptionsTest {
    @Test
    void java8CompilerUsesSourceAndTargetFlagsForJava8Target() {
        assertEquals(
                List.of("-source", "8", "-target", "8"),
                SourceCompiler.externalLanguageLevelOptions(8, 8)
        );
    }

    @Test
    void modernCompilerUsesReleaseForOlderTargets() {
        assertEquals(
                List.of("--release", "8"),
                SourceCompiler.externalLanguageLevelOptions(8, 17)
        );
    }

    @Test
    void modernCompilerUsesReleaseForMatchingTarget() {
        assertEquals(
                List.of("--release", "17"),
                SourceCompiler.externalLanguageLevelOptions(17, 17)
        );
    }

    @Test
    void rejectsCompilerOlderThanTarget() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SourceCompiler.externalLanguageLevelOptions(17, 8)
        );
    }

    @Test
    void rejectsUnsupportedPreJava8Targets() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SourceCompiler.externalLanguageLevelOptions(7, 17)
        );
    }
}
