package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import com.kyroxova.continuumlib.shims.ColorHandlerShim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Verification Suite for Wave 3 Subsystem:
 * ColorHandlerShim & Color Registration (BlockColor, ItemColor) across Mod Loaders.
 */
public class ColorHandlerVerificationTest {

    private ApiKnowledgeBase kb;

    @BeforeEach
    public void setup() {
        this.kb = ApiKnowledgeBase.createDefault();
        ColorHandlerShim.clearRegistrations();
    }

    // =========================================================================
    // Tier 1: Isolation Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 1: Isolation Tests")
    class Tier1Isolation {

        @Test
        @DisplayName("1.1 BlockColor registration, retrieval, and multi-block mapping")
        public void testBlockColorRegistration() {
            Object mockColor = new Object();
            Object blockA = "minecraft:grass_block";
            Object blockB = "minecraft:oak_leaves";

            Object res = ColorHandlerShim.registerBlockColor(mockColor, blockA, blockB);
            assertSame(mockColor, res);
            assertSame(mockColor, ColorHandlerShim.getBlockColor(blockA));
            assertSame(mockColor, ColorHandlerShim.getBlockColor(blockB));
            assertEquals(2, ColorHandlerShim.getRegisteredBlockColorCount());
        }

        @Test
        @DisplayName("1.2 ItemColor registration, retrieval, and multi-item mapping")
        public void testItemColorRegistration() {
            Object mockItemColor = new Object();
            Object itemA = "minecraft:potion";
            Object itemB = "minecraft:leather_chestplate";

            Object res = ColorHandlerShim.registerItemColor(mockItemColor, itemA, itemB);
            assertSame(mockItemColor, res);
            assertSame(mockItemColor, ColorHandlerShim.getItemColor(itemA));
            assertSame(mockItemColor, ColorHandlerShim.getItemColor(itemB));
            assertEquals(2, ColorHandlerShim.getRegisteredItemColorCount());
        }

        @Test
        @DisplayName("1.3 Event-based color registration from Forge/NeoForge RegisterColorHandlersEvent")
        public void testEventBasedColorRegistration() {
            AtomicInteger blockEventCalls = new AtomicInteger(0);
            AtomicInteger itemEventCalls = new AtomicInteger(0);

            Object mockBlockColor = new Object();
            Object mockItemColor = new Object();
            Object block = "minecraft:redstone_wire";
            Object item = "minecraft:redstone";

            Object mockBlockEvent = new Object() {
                public void register(Object color, Object... blocks) {
                    blockEventCalls.incrementAndGet();
                }
            };

            Object mockItemEvent = new Object() {
                public void register(Object color, Object... items) {
                    itemEventCalls.incrementAndGet();
                }
            };

            ColorHandlerShim.registerBlockColorFromEvent(mockBlockEvent, mockBlockColor, block);
            assertEquals(1, blockEventCalls.get());
            assertSame(mockBlockColor, ColorHandlerShim.getBlockColor(block));

            ColorHandlerShim.registerItemColorFromEvent(mockItemEvent, mockItemColor, item);
            assertEquals(1, itemEventCalls.get());
            assertSame(mockItemColor, ColorHandlerShim.getItemColor(item));
        }

        @Test
        @DisplayName("1.4 Boundary and null safety")
        public void testNullSafety() {
            assertNull(ColorHandlerShim.registerBlockColor(null, "minecraft:stone"));
            assertNull(ColorHandlerShim.registerBlockColor(new Object(), (Object[]) null));
            assertNull(ColorHandlerShim.registerItemColor(null, "minecraft:apple"));
            assertNull(ColorHandlerShim.registerItemColor(new Object(), (Object[]) null));
            assertNull(ColorHandlerShim.getBlockColor(null));
            assertNull(ColorHandlerShim.getItemColor(null));

            assertDoesNotThrow(() -> ColorHandlerShim.registerBlockColorFromEvent(null, null));
            assertDoesNotThrow(() -> ColorHandlerShim.registerItemColorFromEvent(null, null));
        }
    }

    // =========================================================================
    // Tier 2: Rules Catalog & Transformation Delta Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 2: Rules Catalog & Transformation Delta Tests")
    class Tier2RulesCatalog {

        @Test
        @DisplayName("2.1 ColorHandlerRulesCatalog contains BlockColors and ItemColors polyfill rules")
        public void testColorCatalogRules() {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target = TargetSpec.of("1.21.1", "neoforge");

            List<TransformationRule> activeRules = kb.getApplicableRules(base, target);

            boolean hasBlockColors = false;
            boolean hasItemColors = false;
            boolean hasBlockEvent = false;
            boolean hasItemEvent = false;

            for (TransformationRule r : activeRules) {
                if (r instanceof PolyfillRule pr) {
                    if ("registerBlockColor".equals(pr.getShimName())) hasBlockColors = true;
                    if ("registerItemColor".equals(pr.getShimName())) hasItemColors = true;
                    if ("registerBlockColorFromEvent".equals(pr.getShimName())) hasBlockEvent = true;
                    if ("registerItemColorFromEvent".equals(pr.getShimName())) hasItemEvent = true;
                }
            }

            assertTrue(hasBlockColors, "BlockColors.register rule should be active");
            assertTrue(hasItemColors, "ItemColors.register rule should be active");
            assertTrue(hasBlockEvent, "RegisterColorHandlersEvent$Block rule should be active");
            assertTrue(hasItemEvent, "RegisterColorHandlersEvent$Item rule should be active");
        }
    }

    // =========================================================================
    // Tier 3: Concurrency & Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 3: Concurrency & Stress Tests")
    class Tier3Concurrency {

        @Test
        @DisplayName("3.1 Highly concurrent block and item color registration")
        public void testConcurrentColorRegistration() throws Exception {
            int threadCount = 16;
            int iterationsPerThread = 500;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < iterationsPerThread; i++) {
                            String block = "block_" + threadId + "_" + i;
                            String item = "item_" + threadId + "_" + i;
                            String color = "color_" + threadId + "_" + i;

                            ColorHandlerShim.registerBlockColor(color, block);
                            ColorHandlerShim.registerItemColor(color, item);

                            if (color.equals(ColorHandlerShim.getBlockColor(block))
                                    && color.equals(ColorHandlerShim.getItemColor(item))) {
                                successCount.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent color registration timed out");
            executor.shutdown();

            assertEquals(threadCount * iterationsPerThread, successCount.get());
            assertEquals(threadCount * iterationsPerThread, ColorHandlerShim.getRegisteredBlockColorCount());
            assertEquals(threadCount * iterationsPerThread, ColorHandlerShim.getRegisteredItemColorCount());
        }
    }
}
