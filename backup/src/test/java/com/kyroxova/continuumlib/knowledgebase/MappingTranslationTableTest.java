package com.kyroxova.continuumlib.knowledgebase;

import com.kyroxova.continuumlib.knowledgebase.mappings.MappingFormat;
import com.kyroxova.continuumlib.knowledgebase.mappings.MappingTranslationTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MappingTranslationTableTest {

    @Test
    @DisplayName("Verify bidirectional class translation across formats")
    public void testClassTranslation() {
        MappingTranslationTable table = MappingTranslationTable.createDefault();

        // Block
        assertEquals("net/minecraft/class_2248", table.translateClass("net/minecraft/world/level/block/Block", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
        assertEquals("net/minecraft/world/level/block/Block", table.translateClass("net/minecraft/class_2248", MappingFormat.INTERMEDIARY, MappingFormat.MOJMAP));
        assertEquals("net/minecraft/block/Block", table.translateClass("net/minecraft/class_2248", MappingFormat.INTERMEDIARY, MappingFormat.SRG));
        assertEquals("net/minecraft/class_2248", table.translateClass("net/minecraft/block/Block", MappingFormat.SRG, MappingFormat.INTERMEDIARY));

        // Dot notation preservation
        assertEquals("net.minecraft.class_2248", table.translateClass("net.minecraft.world.level.block.Block", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
        assertEquals("net.minecraft.world.level.block.Block", table.translateClass("net.minecraft.class_2248", MappingFormat.INTERMEDIARY, MappingFormat.MOJMAP));

        // ItemStack
        assertEquals("net/minecraft/class_1799", table.translateClass("net/minecraft/world/item/ItemStack", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
        assertEquals("net/minecraft/world/item/ItemStack", table.translateClass("net/minecraft/class_1799", MappingFormat.INTERMEDIARY, MappingFormat.MOJMAP));

        // Item
        assertEquals("net/minecraft/class_1792", table.translateClass("net/minecraft/world/item/Item", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
        assertEquals("net/minecraft/world/item/Item", table.translateClass("net/minecraft/class_1792", MappingFormat.INTERMEDIARY, MappingFormat.MOJMAP));

        // Entity
        assertEquals("net/minecraft/class_1297", table.translateClass("net/minecraft/world/entity/Entity", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
        assertEquals("net/minecraft/world/entity/Entity", table.translateClass("net/minecraft/class_1297", MappingFormat.INTERMEDIARY, MappingFormat.MOJMAP));

        // Level / World
        assertEquals("net/minecraft/class_1937", table.translateClass("net/minecraft/world/level/Level", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
        assertEquals("net/minecraft/world/level/Level", table.translateClass("net/minecraft/class_1937", MappingFormat.INTERMEDIARY, MappingFormat.MOJMAP));
        assertEquals("net/minecraft/world/World", table.translateClass("net/minecraft/class_1937", MappingFormat.INTERMEDIARY, MappingFormat.SRG));

        // Unknown class returns identity
        assertEquals("com/example/MyCustomClass", table.translateClass("com/example/MyCustomClass", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
    }

    @Test
    @DisplayName("Verify method translation across formats")
    public void testMethodTranslation() {
        MappingTranslationTable table = MappingTranslationTable.createDefault();

        // ItemStack.getItem()
        assertEquals("method_7909", table.translateMethod("net/minecraft/world/item/ItemStack", "getItem", "()Lnet/minecraft/world/item/Item;", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
        assertEquals("getItem", table.translateMethod("net/minecraft/class_1799", "method_7909", null, MappingFormat.INTERMEDIARY, MappingFormat.MOJMAP));
        assertEquals("func_77973_b", table.translateMethod("getItem", MappingFormat.MOJMAP, MappingFormat.SRG));
        assertEquals("getItem", table.translateMethod("func_77973_b", MappingFormat.SRG, MappingFormat.MOJMAP));
        assertEquals("getItem", table.translateMethod("m_41720_", MappingFormat.SRG, MappingFormat.MOJMAP));

        // ItemStack.getCount()
        assertEquals("method_7947", table.translateMethod("getCount", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
        assertEquals("getCount", table.translateMethod("method_7947", MappingFormat.INTERMEDIARY, MappingFormat.MOJMAP));

        // Block.defaultBlockState()
        assertEquals("method_9564", table.translateMethod("defaultBlockState", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
        assertEquals("defaultBlockState", table.translateMethod("method_9564", MappingFormat.INTERMEDIARY, MappingFormat.MOJMAP));
        assertEquals("getDefaultState", table.translateMethod("defaultBlockState", MappingFormat.MOJMAP, MappingFormat.YARN));

        // Entity.getX()
        assertEquals("method_23317", table.translateMethod("getX", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
        assertEquals("getX", table.translateMethod("method_23317", MappingFormat.INTERMEDIARY, MappingFormat.MOJMAP));
    }

    @Test
    @DisplayName("Verify field translation across formats")
    public void testFieldTranslation() {
        MappingTranslationTable table = MappingTranslationTable.createDefault();

        // ItemStack.EMPTY
        assertEquals("field_8037", table.translateField("net/minecraft/world/item/ItemStack", "EMPTY", "Lnet/minecraft/world/item/ItemStack;", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
        assertEquals("EMPTY", table.translateField("field_8037", MappingFormat.INTERMEDIARY, MappingFormat.MOJMAP));
        assertEquals("field_190927_a", table.translateField("EMPTY", MappingFormat.MOJMAP, MappingFormat.SRG));
        assertEquals("EMPTY", table.translateField("field_190927_a", MappingFormat.SRG, MappingFormat.MOJMAP));
        assertEquals("EMPTY", table.translateField("f_41583_", MappingFormat.SRG, MappingFormat.MOJMAP));

        // Entity.level
        assertEquals("field_6002", table.translateField("level", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
        assertEquals("level", table.translateField("field_6002", MappingFormat.INTERMEDIARY, MappingFormat.MOJMAP));
        assertEquals("world", table.translateField("level", MappingFormat.MOJMAP, MappingFormat.YARN));

        // Level.isClientSide
        assertEquals("field_9236", table.translateField("isClientSide", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
        assertEquals("isClientSide", table.translateField("field_9236", MappingFormat.INTERMEDIARY, MappingFormat.MOJMAP));
    }

    @Test
    @DisplayName("Verify descriptor translation across formats")
    public void testDescriptorTranslation() {
        MappingTranslationTable table = MappingTranslationTable.createDefault();

        // Method descriptor: (ItemStack, Level) -> Block
        String mojmapDesc = "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/level/block/Block;";
        String expectedInterDesc = "(Lnet/minecraft/class_1799;Lnet/minecraft/class_1937;)Lnet/minecraft/class_2248;";

        String interDesc = table.translateDescriptor(mojmapDesc, MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY);
        assertEquals(expectedInterDesc, interDesc);

        // Bidirectional descriptor translation back to Mojmap
        String backToMojmap = table.translateDescriptor(interDesc, MappingFormat.INTERMEDIARY, MappingFormat.MOJMAP);
        assertEquals(mojmapDesc, backToMojmap);

        // Array descriptor
        String arrayDesc = "[Lnet/minecraft/world/item/ItemStack;";
        assertEquals("[Lnet/minecraft/class_1799;", table.translateDescriptor(arrayDesc, MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
    }

    @Test
    @DisplayName("Verify ApiKnowledgeBase wiring and delegation")
    public void testApiKnowledgeBaseIntegration() {
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        assertNotNull(kb.getMappingTranslationTable());

        assertEquals("net/minecraft/class_1799", kb.translateClass("net/minecraft/world/item/ItemStack", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
        assertEquals("method_7909", kb.translateMethod("net/minecraft/world/item/ItemStack", "getItem", null, MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
        assertEquals("field_8037", kb.translateField("net/minecraft/world/item/ItemStack", "EMPTY", null, MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
        assertEquals("(Lnet/minecraft/class_1799;)V", kb.translateDescriptor("(Lnet/minecraft/world/item/ItemStack;)V", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
    }
}
