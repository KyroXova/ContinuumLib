package com.kyroxova.continuumlib.api.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OutputTargetsTest {
    @TempDir Path dir;
    @Test void readsExplicitTargetListAndOutputModes() throws Exception {
        var file = Files.writeString(dir.resolve("targets.properties"), "targets=forge-1.19.2,fabric-1.20.1\nperVersion=true\nuniversal=false\n");
        var targets = OutputTargets.read(file);
        assertEquals(List.of("forge-1.19.2", "fabric-1.20.1"), targets.targets());
        assertTrue(targets.perVersion()); assertFalse(targets.universal());
    }
    @Test void rejectsUnsafeDuplicateAmbiguousAndUnknownSettings() throws Exception {
        Path file = dir.resolve("targets.properties");
        for (String target : List.of("../escape", "ONE,one", "CON", "good,", "")) {
            Files.writeString(file, "targets=" + target + "\nperVersion=true\nuniversal=false\n");
            assertThrows(IOException.class, () -> OutputTargets.read(file));
        }
        for (String settings : List.of("perVersion=yes\nuniversal=false", "perVersion=false\nuniversal=false", "perVersion=true\nuniversal=false\nunknown=true", "perVersion=true\nperVersion=false\nuniversal=false")) {
            Files.writeString(file, "targets=one\n" + settings);
            assertThrows(IOException.class, () -> OutputTargets.read(file));
        }
    }
}
