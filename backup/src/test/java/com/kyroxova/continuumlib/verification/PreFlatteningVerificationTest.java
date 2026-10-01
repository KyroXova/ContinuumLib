package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import com.kyroxova.continuumlib.shims.LegacyGameRegistryShim;
import com.kyroxova.continuumlib.shims.LegacyMetadataShim;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Verification Suite for Generic Pre-Flattening & Metadata Virtualization:
 * Tests LegacyMetadataShim generic dynamic mod registration, mod-defined properties (BooleanProperty,
 * IntegerProperty, EnumProperty, DirectionProperty), dynamic state serialization to/from 4-bit metadata,
 * extended dynamic state table (>16 states), synthetic property wrapping, LegacyGameRegistryShim registration,
 * coordinate polyfills, and high concurrency.
 */
public class PreFlatteningVerificationTest {

    // =========================================================================
    // Tier 1: Isolation Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 1: Isolation Tests")
    class Tier1Isolation {

        @Test
        @DisplayName("1.1 Generic dynamic registration, virtual IDs, and sub-variant resolution")
        public void testGenericBlockIdAndNameResolution() {
            // Air is canonically 0
            assertEquals("minecraft:air", LegacyMetadataShim.getLegacyNameFromId(0));
            assertEquals(0, LegacyMetadataShim.getIdFromLegacyName("minecraft:air"));

            // Register custom mod blocks dynamically
            Object customBlock1 = new Object();
            LegacyMetadataShim.DynamicBlockEntry entry1 = LegacyMetadataShim.registerDynamicBlock("fluxtech", "cable", customBlock1);
            int id1 = entry1.getVirtualId();
            assertTrue(id1 >= 1000);

            assertEquals("fluxtech:cable", LegacyMetadataShim.getLegacyNameFromId(id1));
            assertEquals(id1, LegacyMetadataShim.getIdFromLegacyName("fluxtech:cable"));
            assertSame(customBlock1, LegacyMetadataShim.getBlockById(id1));

            // Dynamic sub-variant registration
            LegacyMetadataShim.registerSubVariant(id1, 1, "fluxtech:insulated_cable");
            LegacyMetadataShim.registerSubVariant(id1, 2, "fluxtech:dense_cable");

            assertEquals("fluxtech:cable", LegacyMetadataShim.getFlattenedBlockName(id1, 0));
            assertEquals("fluxtech:insulated_cable", LegacyMetadataShim.getFlattenedBlockName(id1, 1));
            assertEquals("fluxtech:dense_cable", LegacyMetadataShim.getFlattenedBlockName(id1, 2));

            // Legacy explicit block registration
            LegacyMetadataShim.registerLegacyBlock(4000, "ancient_mod:runic_altar");
            assertEquals("ancient_mod:runic_altar", LegacyMetadataShim.getLegacyNameFromId(4000));
            assertEquals(4000, LegacyMetadataShim.getIdFromLegacyName("ancient_mod:runic_altar"));
        }

        @Test
        @DisplayName("1.2 VirtualBlockState with mod-defined properties (Boolean, Integer, Enum, Direction)")
        public void testVirtualBlockStateWithModProperties() {
            Object fakeBlock = new Object();

            LegacyMetadataShim.DirectionProperty FACING = LegacyMetadataShim.DirectionProperty.create("facing");
            LegacyMetadataShim.BooleanProperty ACTIVE = LegacyMetadataShim.BooleanProperty.create("active");
            LegacyMetadataShim.IntegerProperty LEVEL = LegacyMetadataShim.IntegerProperty.create("level", 0, 7);

            enum MachineMode { IDLE, RUNNING, ERROR }
            LegacyMetadataShim.EnumProperty<MachineMode> MODE = LegacyMetadataShim.EnumProperty.create("mode", MachineMode.class);

            LegacyMetadataShim.DynamicBlockEntry entry = LegacyMetadataShim.registerDynamicBlock(
                    "techmod", "machine", fakeBlock, FACING, ACTIVE, LEVEL, MODE
            );

            LegacyMetadataShim.VirtualBlockState state = entry.getDefaultState();
            assertNotNull(state);
            assertSame(fakeBlock, state.getBlock());
            assertEquals(0, state.getMetadata());

            // Set mod-defined property values
            LegacyMetadataShim.VirtualBlockState state2 = state
                    .setValue(FACING, LegacyMetadataShim.DirectionProperty.Direction.EAST)
                    .setValue(ACTIVE, true)
                    .setValue(LEVEL, 5)
                    .setValue(MODE, MachineMode.RUNNING);

            assertEquals(LegacyMetadataShim.DirectionProperty.Direction.EAST, state2.getValue(FACING));
            assertTrue(state2.getValue(ACTIVE));
            assertEquals(5, state2.getValue(LEVEL));
            assertEquals(MachineMode.RUNNING, state2.getValue(MODE));

            // Property cycle
            LegacyMetadataShim.VirtualBlockState cycled = state2.cycle(ACTIVE);
            assertFalse(cycled.getValue(ACTIVE));

            // Equality and hashcode
            assertEquals(state2, state2);
            assertNotEquals(state, state2);

            // Serialization to 4-bit metadata
            int meta = LegacyMetadataShim.toMetadata(fakeBlock, state2);
            assertTrue(meta >= 0 && meta <= 15);

            // Deserialization from metadata back to VirtualBlockState
            Object restored = LegacyMetadataShim.toBlockState(fakeBlock, meta);
            assertTrue(restored instanceof LegacyMetadataShim.VirtualBlockState);
            assertEquals(meta, LegacyMetadataShim.toMetadata(fakeBlock, restored));
        }

        @Test
        @DisplayName("1.3 Extended dynamic state table for states > 16")
        public void testExtendedDynamicStateTable() {
            Object multiStateBlock = new Object();
            LegacyMetadataShim.IntegerProperty STAGE = LegacyMetadataShim.IntegerProperty.create("stage", 0, 31);
            LegacyMetadataShim.DynamicBlockEntry entry = LegacyMetadataShim.registerDynamicBlock(
                    "plantmod", "crop", multiStateBlock, STAGE
            );

            LegacyMetadataShim.VirtualBlockState baseState = entry.getDefaultState();

            // Create 32 distinct states
            for (int s = 0; s < 32; s++) {
                LegacyMetadataShim.VirtualBlockState st = baseState.setValue(STAGE, s);
                assertEquals(s, st.getValue(STAGE));
                int meta = st.getMetadata();
                assertTrue(meta >= 0 && meta < 16, "Lower 4-bit metadata must be in range 0..15");
            }
        }

        @Test
        @DisplayName("1.4 Block and Item ID extraction and dynamic resolution")
        public void testBlockAndItemIdExtraction() {
            Object fakeBlock = new Object();
            int blockId = LegacyMetadataShim.getIdFromBlock(fakeBlock);
            assertTrue(blockId >= 1000);
            assertSame(fakeBlock, LegacyMetadataShim.getBlockById(blockId));

            Object fakeItem = new Object();
            int itemId = LegacyMetadataShim.getIdFromItem(fakeItem);
            assertTrue(itemId >= 1000);
            assertSame(fakeItem, LegacyMetadataShim.getItemById(itemId));

            // VirtualBlockState ID matches block's ID
            LegacyMetadataShim.VirtualBlockState state = LegacyMetadataShim.createVirtualState(fakeBlock, 2);
            assertEquals(blockId, LegacyMetadataShim.getIdFromBlock(state));
        }

        public static class MockLegacyWorld {
            public final Object mockBlock = new Object();
            public int recordedMeta = 3;
            public boolean setBlockCalled = false;

            public Object getBlock(int x, int y, int z) {
                return mockBlock;
            }
            public int getBlockMetadata(int x, int y, int z) {
                return recordedMeta;
            }
            public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
                setBlockCalled = true;
                recordedMeta = meta;
                return true;
            }
        }

        @Test
        @DisplayName("1.5 Level coordinate manipulation polyfills: getBlockAt, getMetadataAt, setBlockAt")
        public void testLevelCoordinatePolyfills() {
            MockLegacyWorld legacyLevel = new MockLegacyWorld();

            // Read
            Object block = LegacyMetadataShim.getBlockAt(legacyLevel, 10, 64, -20);
            assertSame(legacyLevel.mockBlock, block);

            int meta = LegacyMetadataShim.getMetadataAt(legacyLevel, 10, 64, -20);
            assertEquals(3, meta);

            // Write
            boolean success = LegacyMetadataShim.setBlockAt(legacyLevel, 10, 64, -20, legacyLevel.mockBlock, 7, 3);
            assertTrue(success);
            assertTrue(legacyLevel.setBlockCalled);
            assertEquals(7, legacyLevel.recordedMeta);

            // Set metadata only
            boolean metaSuccess = LegacyMetadataShim.setMetadataAt(legacyLevel, 10, 64, -20, 11, 2);
            assertTrue(metaSuccess);
            assertEquals(11, legacyLevel.recordedMeta);

            // BlockPos creation does not throw in any environment
            assertDoesNotThrow(() -> LegacyMetadataShim.createBlockPos(15, 70, -35));
        }

        @Test
        @DisplayName("1.6 LegacyGameRegistryShim registration, query, and recipe handling")
        public void testLegacyGameRegistryShim() {
            Object testBlock = new Object() {
                @Override
                public String toString() {
                    return "CustomCopperBlock";
                }
            };
            Object testItem = new Object() {
                @Override
                public String toString() {
                    return "CustomWrenchItem";
                }
            };

            // Register block
            Object registeredBlock = LegacyGameRegistryShim.registerBlock(testBlock, "copper_block");
            assertSame(testBlock, registeredBlock);

            // Register item with explicit modId
            Object registeredItem = LegacyGameRegistryShim.registerItem(testItem, "wrench", "mymod");
            assertSame(testItem, registeredItem);

            // Find block and item
            assertSame(testBlock, LegacyGameRegistryShim.findBlock("minecraft", "copper_block"));
            assertSame(testItem, LegacyGameRegistryShim.findItem("mymod", "wrench"));

            // Register TileEntity
            class MockTileEntity {}
            LegacyGameRegistryShim.registerTileEntity(MockTileEntity.class, "mymod:mock_tile");
            LegacyGameRegistryShim.registerTileEntityWithAlternatives(MockTileEntity.class, "mymod:alt_tile", "mymod:old_alt_tile");

            // Register World Generator
            Object mockGen = new Object();
            LegacyGameRegistryShim.registerWorldGenerator(mockGen, 10);

            // Add recipes
            LegacyGameRegistryShim.addRecipe(testItem, "XX", "XX", 'X', testBlock);
            LegacyGameRegistryShim.addShapedRecipe(testItem, "X", 'X', testBlock);
            LegacyGameRegistryShim.addShapelessRecipe(testItem, testBlock);
            LegacyGameRegistryShim.addSmelting(testBlock, testItem, 0.5f);
        }
    }

    // =========================================================================
    // Tier 2: Rules Catalog & Transformation Delta Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 2: PreFlattening Catalog Rules")
    class Tier2CatalogRules {

        @Test
        @DisplayName("2.1 PreFlattening rules activate when modernizing legacy 1.7.10 to 1.18.2")
        public void testPreFlatteningRulesActivation() {
            ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
            TargetSpec legacySpec = new TargetSpec(MCVersion.of("1.7.10"), LoaderType.FORGE, "srg", 0);
            TargetSpec modernSpec = new TargetSpec(MCVersion.of("1.18.2"), LoaderType.FORGE, "mojmap", 0);

            List<TransformationRule> rules = kb.getApplicableRules(legacySpec, modernSpec);
            assertFalse(rules.isEmpty());

            boolean hasGameRegistryRedirect = false;
            boolean hasMetadataShimRule = false;

            for (TransformationRule rule : rules) {
                if (rule.getDescription() != null) {
                    if (rule.getDescription().contains("LegacyGameRegistryShim")) {
                        hasGameRegistryRedirect = true;
                    }
                    if (rule.getDescription().contains("LegacyMetadataShim")) {
                        hasMetadataShimRule = true;
                    }
                }
            }

            assertTrue(hasGameRegistryRedirect, "PreFlattening catalog should contain GameRegistry redirect");
            assertTrue(hasMetadataShimRule, "PreFlattening catalog should contain LegacyMetadataShim rules");
        }
    }

    // =========================================================================
    // Tier 3: Concurrency & Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 3: Concurrency & Stress Tests")
    class Tier3Concurrency {

        @Test
        @DisplayName("3.1 Highly concurrent generic block registration, metadata conversion, and registry access")
        public void testConcurrentPreFlatteningAccess() throws Exception {
            final int threadCount = 16;
            final int iterationsPerThread = 2500;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successCounter = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                pool.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        for (int i = 0; i < iterationsPerThread; i++) {
                            int meta = i % 16;

                            // 1. VirtualBlockState creation and query
                            Object fakeBlock = "Block#" + threadId + "_" + (i % 50);
                            LegacyMetadataShim.VirtualBlockState state = LegacyMetadataShim.createVirtualState(fakeBlock, meta);
                            assertEquals(meta, LegacyMetadataShim.toMetadata(state));

                            // 2. Dynamic block registration every 100 iterations
                            if (i % 100 == 0) {
                                String blockName = "stress_block_" + threadId + "_" + i;
                                LegacyGameRegistryShim.registerBlock(fakeBlock, blockName);
                                assertSame(fakeBlock, LegacyGameRegistryShim.findBlock("minecraft", blockName));

                                int blockId = LegacyMetadataShim.getIdFromBlock(fakeBlock);
                                assertTrue(blockId > 0);
                            }

                            successCounter.incrementAndGet();
                        }
                    } catch (Exception e) {
                        fail("Exception in thread " + threadId + ": " + e.getMessage());
                    }
                });
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
            assertEquals(threadCount * iterationsPerThread, successCounter.get());
        }
    }
}
