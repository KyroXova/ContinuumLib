package com.kyroxova.continuumlib.knowledgebase;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import com.kyroxova.continuumlib.shims.LegacyGameRegistryShim;
import com.kyroxova.continuumlib.shims.LegacyMetadataShim;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PreFlatteningCompatibilityTest {

    @Test
    @DisplayName("PreFlatteningCatalog: Verify transformation rules registered in ApiKnowledgeBase")
    public void testPreFlatteningCatalogRulesRegistered() {
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec base = TargetSpec.of("1.7.10", "forge");
        TargetSpec target120 = TargetSpec.of("1.20.1", "forge");

        List<TransformationRule> rules = kb.getApplicableRules(base, target120);

        // 1. Verify GameRegistry class redirect
        boolean hasGameRegistryRedirect = rules.stream().anyMatch(r ->
                r instanceof ClassRedirectRule cr &&
                "cpw/mods/fml/common/registry/GameRegistry".equals(cr.getSourceInternalName()) &&
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim".equals(cr.getTargetInternalName())
        );
        assertTrue(hasGameRegistryRedirect, "GameRegistry ClassRedirectRule must be present");

        // 2. Verify registerBlock polyfill
        boolean hasRegisterBlockPolyfill = rules.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                "cpw/mods/fml/common/registry/GameRegistry".equals(pr.getSourceOwner()) &&
                "registerBlock".equals(pr.getSourceName()) &&
                "LegacyGameRegistryShim".equals(pr.getShimOwner().substring(pr.getShimOwner().lastIndexOf('/') + 1))
        );
        assertTrue(hasRegisterBlockPolyfill, "registerBlock PolyfillRule must be present");

        // 3. Verify registerItem polyfill
        boolean hasRegisterItemPolyfill = rules.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                "cpw/mods/fml/common/registry/GameRegistry".equals(pr.getSourceOwner()) &&
                "registerItem".equals(pr.getSourceName())
        );
        assertTrue(hasRegisterItemPolyfill, "registerItem PolyfillRule must be present");

        // 4. Verify Block.getStateFromMeta polyfill
        boolean hasGetStateFromMeta = rules.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                pr.getSourceOwner().contains("Block") &&
                "getStateFromMeta".equals(pr.getSourceName()) &&
                "toBlockState".equals(pr.getShimName())
        );
        assertTrue(hasGetStateFromMeta, "Block.getStateFromMeta PolyfillRule must be present");

        // 5. Verify Block.getMetaFromState polyfill
        boolean hasGetMetaFromState = rules.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                pr.getSourceOwner().contains("Block") &&
                "getMetaFromState".equals(pr.getSourceName()) &&
                "toMetadata".equals(pr.getShimName())
        );
        assertTrue(hasGetMetaFromState, "Block.getMetaFromState PolyfillRule must be present");

        // 6. Verify Block.getIdFromBlock polyfill
        boolean hasGetIdFromBlock = rules.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                pr.getSourceOwner().contains("Block") &&
                ("getIdFromBlock".equals(pr.getSourceName()) || "getId".equals(pr.getSourceName())) &&
                "getIdFromBlock".equals(pr.getShimName())
        );
        assertTrue(hasGetIdFromBlock, "Block.getIdFromBlock PolyfillRule must be present");

        // 7. Verify World.getBlock and getBlockMetadata polyfills
        boolean hasWorldGetBlock = rules.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                "net/minecraft/world/World".equals(pr.getSourceOwner()) &&
                "getBlock".equals(pr.getSourceName())
        );
        assertTrue(hasWorldGetBlock, "World.getBlock PolyfillRule must be present");

        boolean hasWorldGetMetadata = rules.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                "net/minecraft/world/World".equals(pr.getSourceOwner()) &&
                "getBlockMetadata".equals(pr.getSourceName())
        );
        assertTrue(hasWorldGetMetadata, "World.getBlockMetadata PolyfillRule must be present");
    }

    @Test
    @DisplayName("LegacyMetadataShim: VirtualBlockState adaptation and property manipulation")
    public void testVirtualBlockState() {
        Object dummyBlock = new Object();
        LegacyMetadataShim.VirtualBlockState state = LegacyMetadataShim.createVirtualState(dummyBlock, 5);

        assertEquals(dummyBlock, state.getBlock());
        assertEquals(5, state.getMetadata());
        assertEquals(5, state.toMetadata());
        assertEquals(5, LegacyMetadataShim.toMetadata(state));

        // Test property mutation on VirtualBlockState
        LegacyMetadataShim.VirtualBlockState mutated = state.setValue("facing", "north");
        assertTrue(mutated.hasProperty("facing"));
        assertEquals("north", mutated.getValue("facing"));
        assertEquals("north", mutated.getValue("facing", String.class));
        assertEquals(5, mutated.getMetadata());

        // Test fallback adapter creation via toBlockState
        Object adapter = LegacyMetadataShim.toBlockState(dummyBlock, 9);
        assertNotNull(adapter);
        assertEquals(9, LegacyMetadataShim.toMetadata(adapter));
    }

    @Test
    @DisplayName("LegacyMetadataShim: Generic dynamic registration and sub-variant resolution")
    public void testGenericDynamicRegistrationAndSubVariants() {
        // Air is canonically 0
        assertEquals("minecraft:air", LegacyMetadataShim.getLegacyNameFromId(0));
        assertEquals(0, LegacyMetadataShim.getIdFromLegacyName("minecraft:air"));

        // Register custom mod block dynamically
        Object customBlock = new Object();
        LegacyMetadataShim.DynamicBlockEntry entry = LegacyMetadataShim.registerDynamicBlock("testmod", "plasma_conduit", customBlock);
        assertNotNull(entry);
        int virtualId = entry.getVirtualId();
        assertTrue(virtualId > 0);

        assertEquals("testmod:plasma_conduit", LegacyMetadataShim.getLegacyNameFromId(virtualId));
        assertEquals(virtualId, LegacyMetadataShim.getIdFromLegacyName("testmod:plasma_conduit"));
        assertSame(customBlock, LegacyMetadataShim.getBlockById(virtualId));

        // Sub-variant registration and resolution
        LegacyMetadataShim.registerSubVariant(virtualId, 1, "testmod:plasma_conduit_active");
        LegacyMetadataShim.registerSubVariant(virtualId, 2, "testmod:plasma_conduit_overclocked");

        assertEquals("testmod:plasma_conduit", LegacyMetadataShim.getFlattenedBlockName(virtualId, 0));
        assertEquals("testmod:plasma_conduit_active", LegacyMetadataShim.getFlattenedBlockName(virtualId, 1));
        assertEquals("testmod:plasma_conduit_overclocked", LegacyMetadataShim.getFlattenedBlockName(virtualId, 2));

        // Explicit dynamic legacy block registration
        LegacyMetadataShim.registerLegacyBlock(2500, "mymod:ancient_machine");
        assertEquals("mymod:ancient_machine", LegacyMetadataShim.getLegacyNameFromId(2500));
        assertEquals(2500, LegacyMetadataShim.getIdFromLegacyName("mymod:ancient_machine"));
    }

    @Test
    @DisplayName("LegacyMetadataShim: Mod-defined properties, dynamic state tables, and extended states")
    public void testModDefinedPropertiesAndStateVirtualization() {
        Object customEngineBlock = new Object();

        LegacyMetadataShim.DirectionProperty FACING = LegacyMetadataShim.DirectionProperty.create("facing");
        LegacyMetadataShim.BooleanProperty POWERED = LegacyMetadataShim.BooleanProperty.create("powered");
        LegacyMetadataShim.IntegerProperty TIER = LegacyMetadataShim.IntegerProperty.create("tier", 1, 4);

        LegacyMetadataShim.DynamicBlockEntry entry = LegacyMetadataShim.registerDynamicBlock(
                "energymod", "engine", customEngineBlock, FACING, POWERED, TIER
        );

        LegacyMetadataShim.VirtualBlockState defState = entry.getDefaultState();
        assertNotNull(defState);
        assertEquals(0, defState.getMetadata());

        // Mutate state with custom typed properties
        LegacyMetadataShim.VirtualBlockState state1 = defState
                .setValue(FACING, LegacyMetadataShim.DirectionProperty.Direction.SOUTH)
                .setValue(POWERED, true)
                .setValue(TIER, 2);

        assertEquals(LegacyMetadataShim.DirectionProperty.Direction.SOUTH, state1.getValue(FACING));
        assertTrue(state1.getValue(POWERED));
        assertEquals(2, state1.getValue(TIER));

        // Cycle property
        LegacyMetadataShim.VirtualBlockState cycled = state1.cycle(POWERED);
        assertFalse(cycled.getValue(POWERED));

        // Metadata serialization (0..15)
        int meta = LegacyMetadataShim.toMetadata(customEngineBlock, state1);
        assertTrue(meta >= 0 && meta <= 15);

        // State reconstruction from metadata
        Object reconstructedState = LegacyMetadataShim.toBlockState(customEngineBlock, meta);
        assertTrue(reconstructedState instanceof LegacyMetadataShim.VirtualBlockState);
        assertEquals(meta, LegacyMetadataShim.toMetadata(customEngineBlock, reconstructedState));
    }

    @Test
    @DisplayName("LegacyGameRegistryShim: Block, Item, TileEntity, and Recipe registration")
    public void testGameRegistryShimOperations() {
        Object dummyBlock = new Object();
        Object dummyItem = new Object();

        // Register block
        Object regBlock = LegacyGameRegistryShim.registerBlock(dummyBlock, "mymod:test_block");
        assertSame(dummyBlock, regBlock);
        assertSame(dummyBlock, LegacyGameRegistryShim.findBlock("mymod", "test_block"));
        assertSame(dummyBlock, LegacyGameRegistryShim.findBlock(null, "test_block"));

        // Register item
        Object regItem = LegacyGameRegistryShim.registerItem(dummyItem, "mymod:test_item");
        assertSame(dummyItem, regItem);
        assertSame(dummyItem, LegacyGameRegistryShim.findItem("mymod", "test_item"));
        assertSame(dummyItem, LegacyGameRegistryShim.findItem(null, "test_item"));

        // Verify dynamic virtual ID assigned to registered block and item
        int blockId = LegacyMetadataShim.getIdFromBlock(dummyBlock);
        assertTrue(blockId > 0);
        assertSame(dummyBlock, LegacyMetadataShim.getBlockById(blockId));

        int itemId = LegacyMetadataShim.getIdFromItem(dummyItem);
        assertTrue(itemId > 0);
        assertSame(dummyItem, LegacyMetadataShim.getItemById(itemId));

        // Register tile entity
        LegacyGameRegistryShim.registerTileEntity(PreFlatteningCompatibilityTest.class, "test_tile_entity");

        // Add recipes
        assertDoesNotThrow(() -> LegacyGameRegistryShim.addRecipe(dummyItem, "###", "#X#", "###"));
        assertDoesNotThrow(() -> LegacyGameRegistryShim.addShapedRecipe(dummyItem, "##", "##"));
        assertDoesNotThrow(() -> LegacyGameRegistryShim.addShapelessRecipe(dummyItem, dummyBlock, dummyBlock));
        assertDoesNotThrow(() -> LegacyGameRegistryShim.addSmelting(dummyBlock, dummyItem, 0.5f));
    }
}
