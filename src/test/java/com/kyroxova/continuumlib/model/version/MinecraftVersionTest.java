package com.kyroxova.continuumlib.model.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftVersionTest {
    @Test
    void ordersReleaseVersionsAcrossOldAndNewSchemes() {
        assertTrue(MinecraftVersion.parse("1.7.10").compareTo(MinecraftVersion.parse("1.18.2")) < 0);
        assertTrue(MinecraftVersion.parse("1.20.5").compareTo(MinecraftVersion.parse("1.21.1")) < 0);
        assertTrue(MinecraftVersion.parse("1.21.11").compareTo(MinecraftVersion.parse("26.1")) < 0);
        assertEquals(MinecraftVersion.parse("1.20.01"), MinecraftVersion.parse("1.20.1"));
    }

    @Test
    void rejectsSnapshotsUntilAQualifiedVersionParserIsRegistered() {
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersion.parse("24w14a"));
    }

    @Test
    void versionRangeUsesInclusiveStartAndExclusiveEnd() {
        VersionRange range = VersionRange.between("1.18.2", "1.20.5");

        assertTrue(range.contains(MinecraftVersion.parse("1.18.2")));
        assertTrue(range.contains(MinecraftVersion.parse("1.20.4")));
        assertFalse(range.contains(MinecraftVersion.parse("1.20.5")));
    }

    @Test
    void lifecycleReportsDeprecationAndRemovalBoundaries() {
        SymbolLifecycle lifecycle = new SymbolLifecycle(
                MinecraftVersion.parse("1.18.2"),
                MinecraftVersion.parse("1.20.5"),
                MinecraftVersion.parse("1.21.0"),
                "minecraft.item.custom_data_component");

        assertEquals(SymbolAvailability.AVAILABLE, lifecycle.availabilityAt(MinecraftVersion.parse("1.20.4")));
        assertEquals(SymbolAvailability.DEPRECATED, lifecycle.availabilityAt(MinecraftVersion.parse("1.20.5")));
        assertEquals(SymbolAvailability.REMOVED, lifecycle.availabilityAt(MinecraftVersion.parse("1.21.1")));
        assertEquals("minecraft.item.custom_data_component", lifecycle.replacementStableId());
    }
}
