package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import com.kyroxova.continuumlib.shims.SavedDataShim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Verification Suite for Wave 3 Subsystem:
 * SavedDataShim & Multi-Version SavedData Lifecycle across 1.7.9 -> 26.3+.
 */
public class SavedDataVerificationTest {

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
        @DisplayName("1.1 SavedData creation, retrieval, and caching via getOrCreate")
        public void testGetOrCreate() {
            String dataId = "test_custom_data";
            SavedDataShim.VirtualSavedData initial = (SavedDataShim.VirtualSavedData)
                    SavedDataShim.getOrCreate(null, dataId, () -> new SavedDataShim.VirtualSavedData(dataId));

            assertNotNull(initial);
            assertEquals(dataId, initial.getId());
            assertFalse(initial.isDirty());

            // Query again to verify same cached instance returned
            Object second = SavedDataShim.getOrCreate(null, dataId, () -> new SavedDataShim.VirtualSavedData(dataId));
            assertSame(initial, second);
        }

        @Test
        @DisplayName("1.2 setDirty marking on VirtualSavedData and reflective SavedData instances")
        public void testSetDirty() {
            SavedDataShim.VirtualSavedData data = new SavedDataShim.VirtualSavedData("dirty_test");
            assertFalse(data.isDirty());

            SavedDataShim.setDirty(data);
            assertTrue(data.isDirty());

            // Test reflection target
            AtomicBoolean reflectiveDirty = new AtomicBoolean(false);
            Object reflectiveData = new Object() {
                public void setDirty() {
                    reflectiveDirty.set(true);
                }
            };

            SavedDataShim.setDirty(reflectiveData);
            assertTrue(reflectiveDirty.get());
        }

        @Test
        @DisplayName("1.3 save cross-version dispatch (with and without HolderLookup.Provider)")
        public void testSaveDispatch() {
            Map<String, Object> mockNbt = new HashMap<>();
            Object mockProvider = new Object();

            // Modern save(CompoundTag, HolderLookup.Provider)
            Object modernData = new Object() {
                public Object save(Object tag, Object provider) {
                    ((Map<String, Object>) tag).put("modern", true);
                    return tag;
                }
            };

            Object res1 = SavedDataShim.save(modernData, mockNbt, mockProvider);
            assertSame(mockNbt, res1);
            assertEquals(true, mockNbt.get("modern"));

            // Legacy save(CompoundTag)
            Map<String, Object> legacyNbt = new HashMap<>();
            Object legacyData = new Object() {
                public Object save(Object tag) {
                    ((Map<String, Object>) tag).put("legacy", true);
                    return tag;
                }
            };

            Object res2 = SavedDataShim.save(legacyData, legacyNbt, null);
            assertSame(legacyNbt, res2);
            assertEquals(true, legacyNbt.get("legacy"));
        }

        @Test
        @DisplayName("1.4 Boundary and null safety")
        public void testNullSafety() {
            assertNull(SavedDataShim.getOrCreate(null, null, null));
            assertDoesNotThrow(() -> SavedDataShim.setDirty(null));

            Object tag = new Object();
            assertSame(tag, SavedDataShim.save(null, tag, null));
            assertNull(SavedDataShim.save(new Object(), null, null));
        }
    }

    // =========================================================================
    // Tier 2: Rules Catalog & Transformation Delta Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 2: Rules Catalog & Transformation Delta Tests")
    class Tier2RulesCatalog {

        @Test
        @DisplayName("2.1 Modernizing 1.12.2 -> 1.16.5 maps WorldSavedData to SavedData")
        public void testModernizeSavedData() {
            TargetSpec base = TargetSpec.of("1.12.2", "forge");
            TargetSpec target = TargetSpec.of("1.16.5", "forge");

            List<TransformationRule> activeRules = kb.getApplicableRules(base, target);

            boolean hasWorldSavedDataRedirect = false;
            for (TransformationRule r : activeRules) {
                if (r instanceof ClassRedirectRule cr) {
                    if ("net/minecraft/world/storage/WorldSavedData".equals(cr.getSourceInternalName())
                            && "net/minecraft/world/level/saveddata/SavedData".equals(cr.getTargetInternalName())) {
                        hasWorldSavedDataRedirect = true;
                    }
                }
            }

            assertTrue(hasWorldSavedDataRedirect, "WorldSavedData -> SavedData class redirect should be active");
        }

        @Test
        @DisplayName("2.2 Downgrading 1.16.5 -> 1.12.2 maps SavedData to WorldSavedData")
        public void testDowngradeSavedData() {
            TargetSpec base = TargetSpec.of("1.16.5", "forge");
            TargetSpec target = TargetSpec.of("1.12.2", "forge");

            List<TransformationRule> activeRules = kb.getApplicableRules(base, target);

            boolean hasSavedDataRedirect = false;
            for (TransformationRule r : activeRules) {
                if (r instanceof ClassRedirectRule cr) {
                    if ("net/minecraft/world/level/saveddata/SavedData".equals(cr.getSourceInternalName())
                            && "net/minecraft/world/storage/WorldSavedData".equals(cr.getTargetInternalName())) {
                        hasSavedDataRedirect = true;
                    }
                }
            }

            assertTrue(hasSavedDataRedirect, "SavedData -> WorldSavedData class redirect should be active");
        }

        @Test
        @DisplayName("2.3 DimensionDataStorage.computeIfAbsent and setDirty polyfills active")
        public void testSavedDataPolyfills() {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target = TargetSpec.of("1.21.1", "neoforge");

            List<TransformationRule> activeRules = kb.getApplicableRules(base, target);

            boolean hasComputeIfAbsent = false;
            boolean hasSetDirty = false;

            for (TransformationRule r : activeRules) {
                if (r instanceof PolyfillRule pr) {
                    if ("getOrCreate".equals(pr.getShimName())) hasComputeIfAbsent = true;
                    if ("setDirty".equals(pr.getShimName())) hasSetDirty = true;
                }
            }

            assertTrue(hasComputeIfAbsent, "DimensionDataStorage.computeIfAbsent polyfill should be active");
            assertTrue(hasSetDirty, "SavedData.setDirty polyfill should be active");
        }
    }

    // =========================================================================
    // Tier 3: Concurrency & Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 3: Concurrency & Stress Tests")
    class Tier3Concurrency {

        @Test
        @DisplayName("3.1 Highly concurrent getOrCreate and setDirty operations")
        public void testConcurrentSavedDataOps() throws Exception {
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
                            String id = "data_" + threadId + "_" + (i % 20);
                            Object data = SavedDataShim.getOrCreate(null, id, () -> new SavedDataShim.VirtualSavedData(id));
                            if (data instanceof SavedDataShim.VirtualSavedData vsd) {
                                SavedDataShim.setDirty(vsd);
                                if (vsd.isDirty() && id.equals(vsd.getId())) {
                                    successCount.incrementAndGet();
                                }
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent SavedData operations timed out");
            executor.shutdown();

            assertEquals(threadCount * iterationsPerThread, successCount.get());
        }
    }
}
