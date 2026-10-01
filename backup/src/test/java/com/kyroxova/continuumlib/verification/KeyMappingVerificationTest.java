package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import com.kyroxova.continuumlib.shims.KeyMappingShim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Verification Suite for Subsystem:
 * KeyMapping & KeyBinding Input Management (1.7.9 -> 26.3+).
 * Covers KeyMapping registration, input down state, click consumption, and input matching.
 */
public class KeyMappingVerificationTest {

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
        @DisplayName("1.1 KeyMapping registration and retrieval tracking")
        public void testKeyMappingRegistration() {
            Object mockKey = new Object();
            Object registered = KeyMappingShim.registerKeyMapping(mockKey);
            assertSame(mockKey, registered);
            assertTrue(KeyMappingShim.getRegisteredKeys().contains(mockKey));

            Object mockKey2 = new Object();
            assertSame(mockKey2, KeyMappingShim.register(mockKey2));
            assertTrue(KeyMappingShim.getRegisteredKeys().contains(mockKey2));

            assertNull(KeyMappingShim.registerKeyMapping(null));
        }

        @Test
        @DisplayName("1.2 isDown inspection across isDown/isKeyDown methods and public field fallbacks")
        public void testIsDownState() {
            // Modern isDown() method
            Object modernDownKey = new Object() {
                public boolean isDown() { return true; }
            };
            assertTrue(KeyMappingShim.isDown(modernDownKey));

            // Legacy isKeyDown() method
            Object legacyDownKey = new Object() {
                public boolean isKeyDown() { return true; }
            };
            assertTrue(KeyMappingShim.isDown(legacyDownKey));

            // Inactive key
            Object inactiveKey = new Object() {
                public boolean isDown() { return false; }
            };
            assertFalse(KeyMappingShim.isDown(inactiveKey));

            // Public field fallback
            class FieldKey {
                public final boolean isDown = true;
            }
            assertTrue(KeyMappingShim.isDown(new FieldKey()));

            assertFalse(KeyMappingShim.isDown(null));
        }

        @Test
        @DisplayName("1.3 consumeClick inspection across consumeClick and isPressed methods")
        public void testConsumeClick() {
            AtomicBoolean clicked = new AtomicBoolean(true);
            Object modernKey = new Object() {
                public boolean consumeClick() {
                    return clicked.getAndSet(false);
                }
            };
            assertTrue(KeyMappingShim.consumeClick(modernKey));
            assertFalse(KeyMappingShim.consumeClick(modernKey));

            AtomicBoolean legacyClicked = new AtomicBoolean(true);
            Object legacyKey = new Object() {
                public boolean isPressed() {
                    return legacyClicked.getAndSet(false);
                }
            };
            assertTrue(KeyMappingShim.consumeClick(legacyKey));
            assertFalse(KeyMappingShim.consumeClick(legacyKey));

            assertFalse(KeyMappingShim.consumeClick(null));
        }

        @Test
        @DisplayName("1.4 matches keyCode and scanCode reflection evaluation")
        public void testMatchesKeyCode() {
            Object mockKey = new Object() {
                public boolean matches(int keyCode, int scanCode) {
                    return keyCode == 65 && scanCode == 30; // 'A' key
                }
            };

            assertTrue(KeyMappingShim.matches(mockKey, 65, 30));
            assertFalse(KeyMappingShim.matches(mockKey, 66, 31));
            assertFalse(KeyMappingShim.matches(null, 65, 30));
        }
    }

    // =========================================================================
    // Tier 2: Rules Catalog & Transformation Delta Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 2: Rules Catalog & Transformation Delta Tests")
    class Tier2RulesCatalog {

        @Test
        @DisplayName("2.1 Modernizing 1.16.5 -> 1.18.2 maps KeyBinding -> KeyMapping and ClientRegistry polyfills")
        public void testModernizeKeyMappingRules() {
            TargetSpec base = TargetSpec.of("1.16.5", "forge");
            TargetSpec target = TargetSpec.of("1.18.2", "forge");

            List<TransformationRule> rules = kb.getApplicableRules(base, target);

            boolean hasKeyBindingToKeyMapping = rules.stream().anyMatch(r ->
                    r instanceof ClassRedirectRule cr &&
                    "net/minecraft/client/settings/KeyBinding".equals(cr.getSourceInternalName()) &&
                    "net/minecraft/client/KeyMapping".equals(cr.getTargetInternalName())
            );
            assertTrue(hasKeyBindingToKeyMapping, "KeyBinding must redirect to KeyMapping on 1.17+");

            boolean hasClientRegistryPolyfill = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraftforge/client/ClientRegistry".equals(pr.getSourceOwner()) &&
                    "registerKeyBinding".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/KeyMappingShim".equals(pr.getShimOwner())
            );
            assertTrue(hasClientRegistryPolyfill, "ClientRegistry.registerKeyBinding must polyfill to KeyMappingShim");
        }

        @Test
        @DisplayName("2.2 Downgrading 1.18.2 -> 1.16.5 maps KeyMapping -> KeyBinding")
        public void testDowngradeKeyMappingRules() {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target = TargetSpec.of("1.16.5", "forge");

            List<TransformationRule> rules = kb.getApplicableRules(base, target);

            boolean hasKeyMappingToKeyBinding = rules.stream().anyMatch(r ->
                    r instanceof ClassRedirectRule cr &&
                    "net/minecraft/client/KeyMapping".equals(cr.getSourceInternalName()) &&
                    "net/minecraft/client/settings/KeyBinding".equals(cr.getTargetInternalName())
            );
            assertTrue(hasKeyMappingToKeyBinding, "KeyMapping must redirect to KeyBinding on <= 1.16.5");
        }
    }

    // =========================================================================
    // Tier 3: Concurrency & Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 3: Concurrency & Stress Tests")
    class Tier3Concurrency {

        @Test
        @DisplayName("3.1 Concurrent registration and input inspection across worker threads")
        public void testConcurrentKeyRegistrations() throws InterruptedException {
            int threadCount = 16;
            int iterationsPerThread = 200;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger activeKeysCount = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < iterationsPerThread; i++) {
                            Object k = new Object() {
                                public boolean isDown() { return true; }
                            };
                            KeyMappingShim.registerKeyMapping(k);
                            if (KeyMappingShim.isDown(k)) {
                                activeKeysCount.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent key registration timed out");
            executor.shutdown();
            assertEquals(threadCount * iterationsPerThread, activeKeysCount.get());
        }
    }
}
