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
    void parsesJava8JavacVersionOutput() {
        assertEquals(8, SourceCompiler.parseJavacVersion("javac 1.8.0_402"));
    }

    @Test
    void parsesModernJavacVersionOutput() {
        assertEquals(17, SourceCompiler.parseJavacVersion("javac 17.0.15"));
        assertEquals(21, SourceCompiler.parseJavacVersion("javac 21"));
    }

    @Test
    void rejectsUnrecognizedJavacVersionOutput() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SourceCompiler.parseJavacVersion("not-a-javac-version")
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
