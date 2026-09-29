package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import com.kyroxova.continuumlib.shims.ParticleShim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Verification Suite for Subsystem:
 * Particle & ParticleOptions Management (1.7.9 -> 26.3+).
 * Covers ParticleType creation, registration, level.addParticle client shims, and serverLevel.sendParticles.
 */
public class ParticleVerificationTest {

    private ApiKnowledgeBase kb;

    @BeforeEach
    public void setup() {
        this.kb = ApiKnowledgeBase.createDefault();
        ParticleShim.resetSpawnedParticleCount();
    }

    // =========================================================================
    // Tier 1: Isolation Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 1: Isolation Tests")
    class Tier1Isolation {

        @Test
        @DisplayName("1.1 ParticleType creation, registration, and virtual representation")
        public void testParticleTypeCreationAndRegistration() {
            String particleId = "mymod:magic_spark";
            Object particleType = ParticleShim.createParticleType(particleId, true);
            assertNotNull(particleType);

            assertSame(particleType, ParticleShim.getParticleType(particleId));

            if (particleType instanceof ParticleShim.VirtualParticleType vpt) {
                assertEquals(particleId, vpt.getId());
                assertTrue(vpt.isOverrideLimiter());
                assertTrue(vpt.toString().contains(particleId));
            }
        }

        @Test
        @DisplayName("1.2 addParticle client-side emission reflection dispatch")
        public void testAddParticleEmission() {
            AtomicInteger calls = new AtomicInteger(0);
            Object mockParticle = new Object();
            Object mockLevel = new Object() {
                public void addParticle(Object options, boolean overrideLimiter, double x, double y, double z, double xs, double ys, double zs) {
                    calls.incrementAndGet();
                }
            };

            ParticleShim.addParticle(mockLevel, mockParticle, 1.0, 2.0, 3.0, 0.1, 0.2, 0.3);
            assertEquals(1, calls.get());
            assertEquals(1, ParticleShim.getSpawnedParticleCount());

            // Edge cases
            assertDoesNotThrow(() -> ParticleShim.addParticle(null, mockParticle, 0, 0, 0, 0, 0, 0));
            assertDoesNotThrow(() -> ParticleShim.addParticle(mockLevel, null, 0, 0, 0, 0, 0, 0));
        }

        @Test
        @DisplayName("1.3 sendParticles server-side emission reflection dispatch")
        public void testSendParticlesEmission() {
            AtomicInteger sentCount = new AtomicInteger(0);
            Object mockParticle = new Object();
            Object mockServerLevel = new Object() {
                public int sendParticles(Object options, double x, double y, double z, int count, double xo, double yo, double zo, double spd) {
                    sentCount.addAndGet(count);
                    return count;
                }
            };

            int count = ParticleShim.sendParticles(mockServerLevel, mockParticle, 10.0, 64.0, 10.0, 25, 0.5, 0.5, 0.5, 0.1);
            assertEquals(25, count);
            assertEquals(25, sentCount.get());
            assertEquals(25, ParticleShim.getSpawnedParticleCount());
        }
    }

    // =========================================================================
    // Tier 2: Rules Catalog & Transformation Delta Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 2: Rules Catalog & Transformation Delta Tests")
    class Tier2RulesCatalog {

        @Test
        @DisplayName("2.1 Modernizing 1.16.5 -> 1.18.2 maps IParticleData -> ParticleOptions and Level.addParticle polyfill")
        public void testModernizeParticleRules() {
            TargetSpec base = TargetSpec.of("1.16.5", "forge");
            TargetSpec target = TargetSpec.of("1.18.2", "forge");

            List<TransformationRule> rules = kb.getApplicableRules(base, target);

            boolean hasParticleOptionsRedirect = rules.stream().anyMatch(r ->
                    r instanceof ClassRedirectRule cr &&
                    "net/minecraft/particles/IParticleData".equals(cr.getSourceInternalName()) &&
                    "net/minecraft/core/particles/ParticleOptions".equals(cr.getTargetInternalName())
            );
            assertTrue(hasParticleOptionsRedirect, "IParticleData must redirect to ParticleOptions on 1.17+");

            boolean hasParticleTypeRedirect = rules.stream().anyMatch(r ->
                    r instanceof ClassRedirectRule cr &&
                    "net/minecraft/particles/ParticleType".equals(cr.getSourceInternalName()) &&
                    "net/minecraft/core/particles/ParticleType".equals(cr.getTargetInternalName())
            );
            assertTrue(hasParticleTypeRedirect, "ParticleType must redirect to core.particles.ParticleType on 1.17+");

            boolean hasAddParticlePolyfill = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/world/level/Level".equals(pr.getSourceOwner()) &&
                    "addParticle".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/ParticleShim".equals(pr.getShimOwner())
            );
            assertTrue(hasAddParticlePolyfill, "Level.addParticle must polyfill to ParticleShim");
        }

        @Test
        @DisplayName("2.2 Downgrading 1.18.2 -> 1.16.5 maps ParticleOptions -> IParticleData")
        public void testDowngradeParticleRules() {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target = TargetSpec.of("1.16.5", "forge");

            List<TransformationRule> rules = kb.getApplicableRules(base, target);

            boolean hasParticleOptionsToLegacy = rules.stream().anyMatch(r ->
                    r instanceof ClassRedirectRule cr &&
                    "net/minecraft/core/particles/ParticleOptions".equals(cr.getSourceInternalName()) &&
                    "net/minecraft/particles/IParticleData".equals(cr.getTargetInternalName())
            );
            assertTrue(hasParticleOptionsToLegacy, "ParticleOptions must redirect to IParticleData on <= 1.16.5");
        }
    }

    // =========================================================================
    // Tier 3: Concurrency & Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 3: Concurrency & Stress Tests")
    class Tier3Concurrency {

        @Test
        @DisplayName("3.1 High-throughput concurrent particle spawning and count consistency")
        public void testConcurrentParticleEmissions() throws InterruptedException {
            int threadCount = 16;
            int emissionsPerThread = 500;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            Object mockParticle = new Object();
            Object mockLevel = new Object() {
                public void addParticle(Object opt, double x, double y, double z, double xs, double ys, double zs) {}
            };

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < emissionsPerThread; i++) {
                            ParticleShim.addParticle(mockLevel, mockParticle, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent particle emissions timed out");
            executor.shutdown();
            assertEquals((long) threadCount * emissionsPerThread, ParticleShim.getSpawnedParticleCount());
        }
    }
}
