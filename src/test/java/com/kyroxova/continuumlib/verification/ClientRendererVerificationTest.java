package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import com.kyroxova.continuumlib.shims.ClientRendererShim;
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
 * Comprehensive Verification Suite for Wave 3 Subsystem:
 * ClientRendererShim & Client Rendering Registration across Mod Loaders (Forge, NeoForge, Fabric).
 */
public class ClientRendererVerificationTest {

    private ApiKnowledgeBase kb;

    @BeforeEach
    public void setup() {
        this.kb = ApiKnowledgeBase.createDefault();
        ClientRendererShim.clearRegistrations();
    }

    // =========================================================================
    // Tier 1: Isolation Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 1: Isolation Tests")
    class Tier1Isolation {

        @Test
        @DisplayName("1.1 BlockEntityRenderer registration, retrieval, and tracking")
        public void testBlockEntityRendererRegistration() {
            Object mockType = new Object();
            Object mockProvider = new Object();

            Object res = ClientRendererShim.registerBlockEntityRenderer(mockType, mockProvider);
            assertSame(mockProvider, res);
            assertSame(mockProvider, ClientRendererShim.getBlockEntityRenderer(mockType));
            assertEquals(1, ClientRendererShim.getRegisteredBlockEntityRendererCount());
            assertTrue(ClientRendererShim.getRegisteredBlockEntityRenderers().containsKey(mockType));
        }

        @Test
        @DisplayName("1.2 EntityRenderer registration, retrieval, and tracking")
        public void testEntityRendererRegistration() {
            Object mockEntityType = new Object();
            Object mockProvider = new Object();

            Object res = ClientRendererShim.registerEntityRenderer(mockEntityType, mockProvider);
            assertSame(mockProvider, res);
            assertSame(mockProvider, ClientRendererShim.getEntityRenderer(mockEntityType));
            assertEquals(1, ClientRendererShim.getRegisteredEntityRendererCount());
            assertTrue(ClientRendererShim.getRegisteredEntityRenderers().containsKey(mockEntityType));
        }

        @Test
        @DisplayName("1.3 Event-based registration via registerFromEvent for Forge/NeoForge EntityRenderersEvent")
        public void testEventBasedRegistration() {
            AtomicInteger berCalls = new AtomicInteger(0);
            AtomicInteger erCalls = new AtomicInteger(0);

            Object mockType = new Object();
            Object mockProvider = new Object();

            // Mock EntityRenderersEvent.RegisterRenderers
            Object mockEvent = new Object() {
                public void registerBlockEntityRenderer(Object type, Object provider) {
                    berCalls.incrementAndGet();
                }

                public void registerEntityRenderer(Object type, Object provider) {
                    erCalls.incrementAndGet();
                }
            };

            ClientRendererShim.registerFromEvent(mockEvent, mockType, mockProvider);
            assertEquals(1, berCalls.get());
            assertSame(mockProvider, ClientRendererShim.getBlockEntityRenderer(mockType));

            // Test fallback when event has no matching methods
            Object emptyEvent = new Object();
            Object mockEntity = new Object();
            ClientRendererShim.registerFromEvent(emptyEvent, mockEntity, mockProvider);
            assertSame(mockProvider, ClientRendererShim.getBlockEntityRenderer(mockEntity));
        }

        @Test
        @DisplayName("1.4 Boundary and null parameter safety")
        public void testNullParameters() {
            assertNull(ClientRendererShim.registerBlockEntityRenderer(null, new Object()));
            assertNull(ClientRendererShim.registerBlockEntityRenderer(new Object(), null));
            assertNull(ClientRendererShim.registerEntityRenderer(null, new Object()));
            assertNull(ClientRendererShim.registerEntityRenderer(new Object(), null));
            assertNull(ClientRendererShim.getBlockEntityRenderer(null));
            assertNull(ClientRendererShim.getEntityRenderer(null));

            assertDoesNotThrow(() -> ClientRendererShim.registerFromEvent(null, null, null));
            assertDoesNotThrow(() -> ClientRendererShim.registerFromEvent(new Object(), null, null));
        }
    }

    // =========================================================================
    // Tier 2: Rules Catalog & Transformation Delta Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 2: Rules Catalog & Transformation Delta Tests")
    class Tier2RulesCatalog {

        @Test
        @DisplayName("2.1 ClientRenderingAndGuiCatalog contains BlockEntityRenderer polyfill rules")
        public void testBlockEntityRendererCatalogRules() {
            TargetSpec base = TargetSpec.of("1.16.5", "forge");
            TargetSpec target = TargetSpec.of("1.21.1", "neoforge");

            List<TransformationRule> activeRules = kb.getApplicableRules(base, target);

            boolean hasVanillaBER = false;
            boolean hasForgeBER = false;
            boolean hasEventBER = false;

            for (TransformationRule r : activeRules) {
                if (r instanceof PolyfillRule pr) {
                    if ("registerBlockEntityRenderer".equals(pr.getShimName())) {
                        if (pr.getSourceOwner().contains("BlockEntityRenderers")) hasVanillaBER = true;
                        if (pr.getSourceOwner().contains("ClientRegistry")) hasForgeBER = true;
                    }
                    if ("registerFromEvent".equals(pr.getShimName()) && pr.getSourceOwner().contains("EntityRenderersEvent")) {
                        hasEventBER = true;
                    }
                }
            }

            assertTrue(hasVanillaBER, "Vanilla BlockEntityRenderers rule should be active");
            assertTrue(hasForgeBER, "Forge ClientRegistry rule should be active");
            assertTrue(hasEventBER, "EntityRenderersEvent rule should be active");
        }

        @Test
        @DisplayName("2.2 ClientRenderingAndGuiCatalog contains EntityRenderer polyfill rules")
        public void testEntityRendererCatalogRules() {
            TargetSpec base = TargetSpec.of("1.16.5", "forge");
            TargetSpec target = TargetSpec.of("1.21.1", "neoforge");

            List<TransformationRule> activeRules = kb.getApplicableRules(base, target);

            boolean hasVanillaER = false;
            boolean hasForgeER = false;
            boolean hasEventER = false;

            for (TransformationRule r : activeRules) {
                if (r instanceof PolyfillRule pr) {
                    if ("registerEntityRenderer".equals(pr.getShimName())) {
                        if (pr.getSourceOwner().contains("EntityRenderers")) hasVanillaER = true;
                        if (pr.getSourceOwner().contains("RenderingRegistry")) hasForgeER = true;
                    }
                    if ("registerFromEvent".equals(pr.getShimName()) && pr.getSourceOwner().contains("EntityRenderersEvent")) {
                        hasEventER = true;
                    }
                }
            }

            assertTrue(hasVanillaER, "Vanilla EntityRenderers rule should be active");
            assertTrue(hasForgeER, "Forge RenderingRegistry rule should be active");
            assertTrue(hasEventER, "EntityRenderersEvent rule should be active");
        }
    }

    // =========================================================================
    // Tier 3: Concurrency & Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 3: Concurrency & Stress Tests")
    class Tier3Concurrency {

        @Test
        @DisplayName("3.1 Concurrent multi-threaded registration and lookup of renderers")
        public void testConcurrentRendererRegistration() throws Exception {
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
                            String type = "block_entity_" + threadId + "_" + i;
                            String provider = "provider_" + threadId + "_" + i;
                            ClientRendererShim.registerBlockEntityRenderer(type, provider);

                            String eType = "entity_" + threadId + "_" + i;
                            String eProvider = "eprovider_" + threadId + "_" + i;
                            ClientRendererShim.registerEntityRenderer(eType, eProvider);

                            if (provider.equals(ClientRendererShim.getBlockEntityRenderer(type))
                                    && eProvider.equals(ClientRendererShim.getEntityRenderer(eType))) {
                                successCount.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent renderer registration timed out");
            executor.shutdown();

            assertEquals(threadCount * iterationsPerThread, successCount.get());
            assertEquals(threadCount * iterationsPerThread, ClientRendererShim.getRegisteredBlockEntityRendererCount());
            assertEquals(threadCount * iterationsPerThread, ClientRendererShim.getRegisteredEntityRendererCount());
        }
    }
}
