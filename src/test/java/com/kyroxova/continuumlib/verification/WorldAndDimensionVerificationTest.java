package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import com.kyroxova.continuumlib.shims.WorldShim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Verification Suite for Subsystem:
 * World & Dimension Management (1.7.9 -> 26.3+).
 * Covers Level/World, ServerLevel/ServerWorld, dimension keys, block/tile entity lookup, and side queries.
 */
public class WorldAndDimensionVerificationTest {

    private ApiKnowledgeBase kb;

    @BeforeEach
    public void setup() {
        this.kb = ApiKnowledgeBase.createDefault();
    }

    // =========================================================================
    // Tier 1: Isolation Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 1: Isolation Tests")
    class Tier1Isolation {

        @Test
        @DisplayName("1.1 BlockEntity / TileEntity lookup across Level and World reflection targets")
        public void testBlockEntityLookup() {
            Object mockPos = new Object();
            Object mockTileEntity = new Object();

            // Mock Level with modern getBlockEntity
            Object modernLevel = new Object() {
                public Object getBlockEntity(Object pos) {
                    return pos == mockPos ? mockTileEntity : null;
                }
            };
            assertSame(mockTileEntity, WorldShim.getBlockEntity(modernLevel, mockPos));

            // Mock World with legacy getTileEntity
            Object legacyWorld = new Object() {
                public Object getTileEntity(Object pos) {
                    return pos == mockPos ? mockTileEntity : null;
                }
            };
            assertSame(mockTileEntity, WorldShim.getBlockEntity(legacyWorld, mockPos));
            assertSame(mockTileEntity, WorldShim.getTileEntity(legacyWorld, mockPos));

            // Edge cases
            assertNull(WorldShim.getBlockEntity(null, mockPos));
            assertNull(WorldShim.getBlockEntity(modernLevel, null));
        }

        @Test
        @DisplayName("1.2 BlockState lookup across getBlockState and legacy getBlock")
        public void testBlockStateLookup() {
            Object mockPos = new Object();
            Object mockBlockState = new Object();

            Object modernLevel = new Object() {
                public Object getBlockState(Object pos) {
                    return pos == mockPos ? mockBlockState : null;
                }
            };
            assertSame(mockBlockState, WorldShim.getBlockState(modernLevel, mockPos));

            Object legacyWorld = new Object() {
                public Object getBlock(Object pos) {
                    return pos == mockPos ? mockBlockState : null;
                }
            };
            assertSame(mockBlockState, WorldShim.getBlockState(legacyWorld, mockPos));

            assertNull(WorldShim.getBlockState(null, mockPos));
            assertNull(WorldShim.getBlockState(modernLevel, null));
        }

        @Test
        @DisplayName("1.3 isClientSide and isRemote method & field reflection detection")
        public void testClientSideQueries() {
            // Method queries
            Object clientMethodLevel = new Object() {
                public boolean isClientSide() { return true; }
            };
            assertTrue(WorldShim.isClientSide(clientMethodLevel));
            assertTrue(WorldShim.isRemote(clientMethodLevel));

            Object serverMethodLevel = new Object() {
                public boolean isRemote() { return false; }
            };
            assertFalse(WorldShim.isClientSide(serverMethodLevel));
            assertFalse(WorldShim.isRemote(serverMethodLevel));

            // Public field fallback
            class FieldClientWorld {
                public final boolean isClientSide = true;
            }
            assertTrue(WorldShim.isClientSide(new FieldClientWorld()));

            class FieldRemoteWorld {
                public final boolean isRemote = true;
            }
            assertTrue(WorldShim.isRemote(new FieldRemoteWorld()));

            assertFalse(WorldShim.isClientSide(null));
        }

        @Test
        @DisplayName("1.4 Dimension key lookup and server overworld / dimension retrieval")
        public void testDimensionKeysAndLookup() {
            String dimKey = "minecraft:overworld";
            Object mockLevel = new Object() {
                public Object dimension() { return dimKey; }
            };
            assertEquals(dimKey, WorldShim.getDimensionKey(mockLevel));

            Object mockServer = new Object() {
                public Object overworld() { return mockLevel; }
                public Object getLevel(Object key) {
                    return "minecraft:overworld".equals(String.valueOf(key)) ? mockLevel : null;
                }
            };
            assertSame(mockLevel, WorldShim.getOverworld(mockServer));
            assertSame(mockLevel, WorldShim.getDimension(mockServer, "minecraft:overworld"));
            assertNull(WorldShim.getDimension(mockServer, "minecraft:the_nether"));

            assertNull(WorldShim.getDimensionKey(null));
            assertNull(WorldShim.getOverworld(null));
            assertNull(WorldShim.getDimension(null, "overworld"));
        }

        @Test
        @DisplayName("1.5 Biome resolution across raw Biome and modern Holder<Biome>")
        public void testBiomeLookup() {
            Object mockPos = new Object();
            Object rawBiome = "minecraft:plains";
            Object mockHolder = new Object() {
                public Object value() {
                    return rawBiome;
                }
            };

            // Modern level returning Holder<Biome>
            Object modernLevel = new Object() {
                public Object getBiome(Object pos) {
                    return pos == mockPos ? mockHolder : null;
                }
            };
            assertSame(mockHolder, WorldShim.getBiome(modernLevel, mockPos));
            assertSame(rawBiome, WorldShim.getBiomeInstance(modernLevel, mockPos));

            // Legacy level returning raw Biome
            Object legacyLevel = new Object() {
                public Object getBiome(Object pos) {
                    return pos == mockPos ? rawBiome : null;
                }
            };
            assertSame(rawBiome, WorldShim.getBiome(legacyLevel, mockPos));
            assertSame(rawBiome, WorldShim.getBiomeInstance(legacyLevel, mockPos));

            // Null safety
            assertNull(WorldShim.getBiome(null, mockPos));
            assertNull(WorldShim.getBiome(modernLevel, null));
            assertNull(WorldShim.getBiomeInstance(null, mockPos));
            assertNull(WorldShim.getBiomeInstance(modernLevel, null));
            assertNull(WorldShim.unwrapHolder(null));
            assertEquals("test", WorldShim.unwrapHolder("test"));
        }
    }

    // =========================================================================
    // Tier 2: Rules Catalog & Transformation Delta Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 2: Rules Catalog & Transformation Delta Tests")
    class Tier2RulesCatalog {

        @Test
        @DisplayName("2.1 Modernizing 1.16.5 -> 1.18.2 maps World -> Level and getTileEntity -> getBlockEntity")
        public void testModernizeWorldRules() {
            TargetSpec base = TargetSpec.of("1.16.5", "forge");
            TargetSpec target = TargetSpec.of("1.18.2", "forge");

            List<TransformationRule> rules = kb.getApplicableRules(base, target);

            boolean hasWorldToLevel = rules.stream().anyMatch(r ->
                    r instanceof ClassRedirectRule cr &&
                    "net/minecraft/world/World".equals(cr.getSourceInternalName()) &&
                    "net/minecraft/world/level/Level".equals(cr.getTargetInternalName())
            );
            assertTrue(hasWorldToLevel, "World must redirect to Level on 1.17+");

            boolean hasServerWorldToServerLevel = rules.stream().anyMatch(r ->
                    r instanceof ClassRedirectRule cr &&
                    "net/minecraft/world/server/ServerWorld".equals(cr.getSourceInternalName()) &&
                    "net/minecraft/server/level/ServerLevel".equals(cr.getTargetInternalName())
            );
            assertTrue(hasServerWorldToServerLevel, "ServerWorld must redirect to ServerLevel on 1.17+");

            boolean hasTileEntityPolyfill = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/world/World".equals(pr.getSourceOwner()) &&
                    "getTileEntity".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/WorldShim".equals(pr.getShimOwner())
            );
            assertTrue(hasTileEntityPolyfill, "World.getTileEntity must polyfill through WorldShim");

            boolean hasIsRemotePolyfill = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/world/World".equals(pr.getSourceOwner()) &&
                    "isRemote".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/WorldShim".equals(pr.getShimOwner())
            );
            assertTrue(hasIsRemotePolyfill, "World.isRemote must polyfill through WorldShim");
        }

        @Test
        @DisplayName("2.2 Downgrading 1.18.2 -> 1.16.5 maps Level -> World and ServerLevel -> ServerWorld")
        public void testDowngradeWorldRules() {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target = TargetSpec.of("1.16.5", "forge");

            List<TransformationRule> rules = kb.getApplicableRules(base, target);

            boolean hasLevelToWorld = rules.stream().anyMatch(r ->
                    r instanceof ClassRedirectRule cr &&
                    "net/minecraft/world/level/Level".equals(cr.getSourceInternalName()) &&
                    "net/minecraft/world/World".equals(cr.getTargetInternalName())
            );
            assertTrue(hasLevelToWorld, "Level must redirect to World on <= 1.16.5");

            boolean hasServerLevelToServerWorld = rules.stream().anyMatch(r ->
                    r instanceof ClassRedirectRule cr &&
                    "net/minecraft/server/level/ServerLevel".equals(cr.getSourceInternalName()) &&
                    "net/minecraft/world/server/ServerWorld".equals(cr.getTargetInternalName())
            );
            assertTrue(hasServerLevelToServerWorld, "ServerLevel must redirect to ServerWorld on <= 1.16.5");

            boolean hasOverworldPolyfill = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/server/MinecraftServer".equals(pr.getSourceOwner()) &&
                    "overworld".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/WorldShim".equals(pr.getShimOwner())
            );
            assertTrue(hasOverworldPolyfill, "MinecraftServer.overworld must polyfill through WorldShim on legacy targets");
        }

        @Test
        @DisplayName("2.3 Level.getBiome polyfill rules active across version boundaries")
        public void testGetBiomeRules() {
            TargetSpec base = TargetSpec.of("1.16.5", "forge");
            TargetSpec target1182 = TargetSpec.of("1.18.2", "forge");

            List<TransformationRule> rules = kb.getApplicableRules(base, target1182);

            boolean hasBiomeInstanceRule = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/world/level/Level".equals(pr.getSourceOwner()) &&
                    "getBiome".equals(pr.getSourceName()) &&
                    "getBiomeInstance".equals(pr.getTargetMethod())
            );
            assertTrue(hasBiomeInstanceRule, "Level.getBiome() -> WorldShim.getBiomeInstance must be registered for 1.18.2+");
        }
    }

    // =========================================================================
    // Tier 3: Concurrency & Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 3: Concurrency & Stress Tests")
    class Tier3Concurrency {

        @Test
        @DisplayName("3.1 Highly concurrent block entity queries across simulated world threads")
        public void testConcurrentWorldQueries() throws InterruptedException {
            int threadCount = 16;
            int iterationsPerThread = 500;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            Object mockTile = new Object();
            Object mockLevel = new Object() {
                public Object getBlockEntity(Object pos) {
                    return mockTile;
                }
                public boolean isClientSide() {
                    return false;
                }
            };

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < iterationsPerThread; i++) {
                            Object pos = new Object();
                            Object be = WorldShim.getBlockEntity(mockLevel, pos);
                            boolean isClient = WorldShim.isClientSide(mockLevel);
                            if (be == mockTile && !isClient) {
                                successCount.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent world queries timed out");
            executor.shutdown();
            assertEquals(threadCount * iterationsPerThread, successCount.get());
        }
    }
}
