package com.kyroxova.continuumlib.knowledgebase.mappings;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MappingFormatTest {

    @Test
    @DisplayName("Verify enum constants and attributes")
    public void testEnumConstants() {
        assertEquals("mojmap", MappingFormat.MOJMAP.getId());
        assertEquals("intermediary", MappingFormat.INTERMEDIARY.getId());
        assertEquals("srg", MappingFormat.SRG.getId());
        assertEquals("yarn", MappingFormat.YARN.getId());

        assertTrue(MappingFormat.MOJMAP.isDeobfuscated());
        assertTrue(MappingFormat.MOJMAP.isNamed());
        assertFalse(MappingFormat.INTERMEDIARY.isDeobfuscated());
        assertFalse(MappingFormat.SRG.isDeobfuscated());
        assertTrue(MappingFormat.YARN.isDeobfuscated());
    }

    @Test
    @DisplayName("Verify fromString resolution with aliases and case insensitivity")
    public void testFromString() {
        assertEquals(MappingFormat.MOJMAP, MappingFormat.fromString("mojmap"));
        assertEquals(MappingFormat.MOJMAP, MappingFormat.fromString("MOJMAP"));
        assertEquals(MappingFormat.MOJMAP, MappingFormat.fromString("mojang"));
        assertEquals(MappingFormat.MOJMAP, MappingFormat.fromString("official"));
        assertEquals(MappingFormat.MOJMAP, MappingFormat.fromString("moj"));

        assertEquals(MappingFormat.INTERMEDIARY, MappingFormat.fromString("intermediary"));
        assertEquals(MappingFormat.INTERMEDIARY, MappingFormat.fromString("INTERMEDIARY"));
        assertEquals(MappingFormat.INTERMEDIARY, MappingFormat.fromString("fabric"));
        assertEquals(MappingFormat.INTERMEDIARY, MappingFormat.fromString("inter"));

        assertEquals(MappingFormat.SRG, MappingFormat.fromString("srg"));
        assertEquals(MappingFormat.SRG, MappingFormat.fromString("SRG"));
        assertEquals(MappingFormat.SRG, MappingFormat.fromString("searge"));
        assertEquals(MappingFormat.SRG, MappingFormat.fromString("forge"));
        assertEquals(MappingFormat.SRG, MappingFormat.fromString("mcp"));

        assertEquals(MappingFormat.YARN, MappingFormat.fromString("yarn"));
        assertEquals(MappingFormat.YARN, MappingFormat.fromString("quilt"));

        // Fallbacks
        assertEquals(MappingFormat.MOJMAP, MappingFormat.fromString(null));
        assertEquals(MappingFormat.MOJMAP, MappingFormat.fromString("   "));
        assertEquals(MappingFormat.MOJMAP, MappingFormat.fromString("unknown_format"));
        assertEquals(MappingFormat.SRG, MappingFormat.fromString("unknown", MappingFormat.SRG));
    }

    @Test
    @DisplayName("Verify resolution from TargetSpec")
    public void testFromTargetSpec() {
        TargetSpec mojmapSpec = new TargetSpec(MCVersion.V1_18_2, LoaderType.FORGE, "mojmap", 17);
        assertEquals(MappingFormat.MOJMAP, MappingFormat.fromTargetSpec(mojmapSpec));

        TargetSpec interSpec = new TargetSpec(MCVersion.V1_18_2, LoaderType.FABRIC, "intermediary", 17);
        assertEquals(MappingFormat.INTERMEDIARY, MappingFormat.fromTargetSpec(interSpec));

        TargetSpec srgSpec = new TargetSpec(MCVersion.V1_18_2, LoaderType.FORGE, "srg", 17);
        assertEquals(MappingFormat.SRG, MappingFormat.fromTargetSpec(srgSpec));

        assertEquals(MappingFormat.MOJMAP, MappingFormat.fromTargetSpec(null));
    }

    @Test
    @DisplayName("Verify detection from identifiers")
    public void testDetectFromIdentifier() {
        assertEquals(MappingFormat.INTERMEDIARY, MappingFormat.detectFromIdentifier("net/minecraft/class_2248"));
        assertEquals(MappingFormat.INTERMEDIARY, MappingFormat.detectFromIdentifier("method_7909"));
        assertEquals(MappingFormat.INTERMEDIARY, MappingFormat.detectFromIdentifier("field_8037"));

        assertEquals(MappingFormat.SRG, MappingFormat.detectFromIdentifier("func_77973_b"));
        assertEquals(MappingFormat.SRG, MappingFormat.detectFromIdentifier("field_190927_a"));
        assertEquals(MappingFormat.SRG, MappingFormat.detectFromIdentifier("m_41720_"));
        assertEquals(MappingFormat.SRG, MappingFormat.detectFromIdentifier("f_41583_"));
        assertEquals(MappingFormat.SRG, MappingFormat.detectFromIdentifier("net/minecraft/src/C_1234_"));

        assertEquals(MappingFormat.MOJMAP, MappingFormat.detectFromIdentifier("net/minecraft/world/item/ItemStack"));
        assertEquals(MappingFormat.MOJMAP, MappingFormat.detectFromIdentifier("getItem"));
    }

    @Test
    @DisplayName("Verify detection from class and member names")
    public void testDetectClassAndMember() {
        assertEquals(MappingFormat.INTERMEDIARY, MappingFormat.detectFromClassName("net/minecraft/class_1799"));
        assertEquals(MappingFormat.SRG, MappingFormat.detectFromClassName("net.minecraft.src.C_1234_"));
        assertEquals(MappingFormat.MOJMAP, MappingFormat.detectFromClassName("net.minecraft.world.level.Level"));

        assertEquals(MappingFormat.INTERMEDIARY, MappingFormat.detectFromMemberName("method_1234"));
        assertEquals(MappingFormat.INTERMEDIARY, MappingFormat.detectFromMemberName("field_1234"));
        assertEquals(MappingFormat.SRG, MappingFormat.detectFromMemberName("func_12345_a"));
        assertEquals(MappingFormat.SRG, MappingFormat.detectFromMemberName("field_12345_a"));
        assertEquals(MappingFormat.SRG, MappingFormat.detectFromMemberName("m_1234_"));
        assertEquals(MappingFormat.SRG, MappingFormat.detectFromMemberName("f_1234_"));
        assertEquals(MappingFormat.MOJMAP, MappingFormat.detectFromMemberName("getCount"));
    }
}
