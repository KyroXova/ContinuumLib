package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.FieldRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.MethodRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import com.kyroxova.continuumlib.shims.CapabilityShim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Verification Suite for Subsystem b:
 * Capabilities & Data Attachments across Forge, NeoForge, and Fabric (1.7.9 -> 26.3+).
 */
public class CapabilitiesAndAttachmentsVerificationTest {

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
        @DisplayName("1.1 LazyOptional proxy full method lifecycle (isPresent, ifPresent, orElse, map, lazyMap, invalidate)")
        public void testLazyOptionalLifecycle() throws Exception {
            String testValue = "ActiveFluidTank";
            Object lazyOpt = CapabilityShim.ofLazyOptional(testValue);
            assertNotNull(lazyOpt);

            if (lazyOpt instanceof Optional<?> rawOpt) {
                @SuppressWarnings("unchecked")
                Optional<Object> opt = (Optional<Object>) rawOpt;
                assertTrue(opt.isPresent());
                assertEquals(testValue, opt.get());
                assertEquals(testValue, opt.orElse("fallback"));
                assertEquals(testValue, opt.orElseGet(() -> "fallback_supplier"));

                AtomicReference<Object> consumed = new AtomicReference<>();
                opt.ifPresent(consumed::set);
                assertEquals(testValue, consumed.get());

                Optional<?> mapped = opt.map(obj -> obj.toString().length());
                assertTrue(mapped.isPresent());
                assertEquals(testValue.length(), mapped.get());

                Optional<?> kept = opt.filter(obj -> obj.toString().startsWith("Active"));
                assertTrue(kept.isPresent());

                Optional<?> filtered = opt.filter(obj -> obj.toString().startsWith("Inactive"));
                assertFalse(filtered.isPresent());
            } else {
                Method isPresentMethod = lazyOpt.getClass().getMethod("isPresent");
                assertTrue((boolean) isPresentMethod.invoke(lazyOpt));

                AtomicReference<Object> consumed = new AtomicReference<>();
                Method ifPresentMethod = lazyOpt.getClass().getMethod("ifPresent", Consumer.class);
                ifPresentMethod.invoke(lazyOpt, (Consumer<Object>) consumed::set);
                assertEquals(testValue, consumed.get());

                Method orElseMethod = lazyOpt.getClass().getMethod("orElse", Object.class);
                assertEquals(testValue, orElseMethod.invoke(lazyOpt, "fallback"));

                Method orElseGetMethod = lazyOpt.getClass().getMethod("orElseGet", Supplier.class);
                assertEquals(testValue, orElseGetMethod.invoke(lazyOpt, (Supplier<Object>) () -> "fallback_supplier"));

                Method resolveMethod = lazyOpt.getClass().getMethod("resolve");
                Optional<?> opt = (Optional<?>) resolveMethod.invoke(lazyOpt);
                assertTrue(opt.isPresent());
                assertEquals(testValue, opt.get());

                Method mapMethod = lazyOpt.getClass().getMethod("map", Function.class);
                Optional<?> mapped = (Optional<?>) mapMethod.invoke(lazyOpt, (Function<Object, Integer>) obj -> obj.toString().length());
                assertTrue(mapped.isPresent());
                assertEquals(testValue.length(), mapped.get());

                Method lazyMapMethod = lazyOpt.getClass().getMethod("lazyMap", Function.class);
                Object mappedLazyOpt = lazyMapMethod.invoke(lazyOpt, (Function<Object, String>) obj -> obj + "_mapped");
                assertNotNull(mappedLazyOpt);
                assertTrue((boolean) isPresentMethod.invoke(mappedLazyOpt));
                assertEquals(testValue + "_mapped", orElseMethod.invoke(mappedLazyOpt, (Object) null));

                Method filterMethod = lazyOpt.getClass().getMethod("filter", Predicate.class);
                Object kept = filterMethod.invoke(lazyOpt, (Predicate<Object>) obj -> obj.toString().startsWith("Active"));
                assertTrue((boolean) isPresentMethod.invoke(kept));

                Object filteredOut = filterMethod.invoke(lazyOpt, (Predicate<Object>) obj -> obj.toString().startsWith("Inactive"));
                assertFalse((boolean) isPresentMethod.invoke(filteredOut));

                Method invalidateMethod = lazyOpt.getClass().getMethod("invalidate");
                invalidateMethod.invoke(lazyOpt);

                assertFalse((boolean) isPresentMethod.invoke(lazyOpt), "Invalidated LazyOptional must report isPresent = false");
                assertEquals("fallback", orElseMethod.invoke(lazyOpt, "fallback"));
                Optional<?> resolvedAfterInvalidate = (Optional<?>) resolveMethod.invoke(lazyOpt);
                assertFalse(resolvedAfterInvalidate.isPresent());
            }
        }

        @Test
        @DisplayName("1.2 Empty LazyOptional semantics")
        public void testEmptyLazyOptional() throws Exception {
            Object empty = CapabilityShim.emptyLazyOptional();
            assertNotNull(empty);

            if (empty instanceof Optional<?> rawOpt) {
                @SuppressWarnings("unchecked")
                Optional<Object> opt = (Optional<Object>) rawOpt;
                assertFalse(opt.isPresent());
                assertEquals("default_val", opt.orElse("default_val"));
                AtomicBoolean called = new AtomicBoolean(false);
                opt.ifPresent(o -> called.set(true));
                assertFalse(called.get());
            } else {
                Method isPresentMethod = empty.getClass().getMethod("isPresent");
                assertFalse((boolean) isPresentMethod.invoke(empty));

                AtomicBoolean called = new AtomicBoolean(false);
                Method ifPresentMethod = empty.getClass().getMethod("ifPresent", Consumer.class);
                ifPresentMethod.invoke(empty, (Consumer<Object>) o -> called.set(true));
                assertFalse(called.get());

                Method orElseMethod = empty.getClass().getMethod("orElse", Object.class);
                assertEquals("default_val", orElseMethod.invoke(empty, "default_val"));

                Method resolveMethod = empty.getClass().getMethod("resolve");
                Optional<?> resolved = (Optional<?>) resolveMethod.invoke(empty);
                assertFalse(resolved.isPresent());
            }
        }

        @Test
        @DisplayName("1.3 NeoForge Data Attachments: getData, setData, hasData, removeData with fallback store")
        public void testNeoForgeDataAttachments() {
            Object mockHolder = new Object();
            Object attachmentType = "mymod:mana_data";

            assertFalse(CapabilityShim.hasDataAttachment(mockHolder, attachmentType));
            assertNull(CapabilityShim.getDataAttachment(mockHolder, attachmentType));

            // Set data
            CapabilityShim.setDataAttachment(mockHolder, attachmentType, 100);
            assertTrue(CapabilityShim.hasDataAttachment(mockHolder, attachmentType));
            assertEquals(100, CapabilityShim.getDataAttachment(mockHolder, attachmentType));

            // Overwrite data
            CapabilityShim.setDataAttachment(mockHolder, attachmentType, 250);
            assertEquals(250, CapabilityShim.getDataAttachment(mockHolder, attachmentType));

            // Remove data
            Object removed = CapabilityShim.removeDataAttachment(mockHolder, attachmentType);
            assertEquals(250, removed);
            assertFalse(CapabilityShim.hasDataAttachment(mockHolder, attachmentType));
            assertNull(CapabilityShim.getDataAttachment(mockHolder, attachmentType));
        }

        @Test
        @DisplayName("1.4 AttachmentType default value supplier fallback")
        public void testAttachmentTypeDefaultValueFallback() {
            Object mockHolder = new Object();
            AttachmentTypeWithDefault attachmentTypeWithDefault = new AttachmentTypeWithDefault();

            Object val = CapabilityShim.getDataAttachment(mockHolder, attachmentTypeWithDefault);
            assertEquals("default_mana_pool", val, "Should invoke getDefaultValue() and persist in fallback store");

            assertTrue(CapabilityShim.hasDataAttachment(mockHolder, attachmentTypeWithDefault));
        }

        @Test
        @DisplayName("1.5 Fabric Transfer API droplet to millibucket conversion (81,000 droplets = 1000 mB)")
        public void testFabricTransferApiDropletConversion() {
            // Conversion constants
            assertEquals(81L, CapabilityShim.DROPLETS_PER_MB);
            assertEquals(81_000L, CapabilityShim.DROPLETS_PER_BUCKET);
            assertEquals(1000, CapabilityShim.MB_PER_BUCKET);

            // 1 bucket = 1000 mB = 81,000 droplets
            assertEquals(81_000L, CapabilityShim.mbToDroplets(1000L));
            assertEquals(1000L, CapabilityShim.dropletsToMb(81_000L));

            // Arbitrary values
            assertEquals(81L, CapabilityShim.mbToDroplets(1L));
            assertEquals(1L, CapabilityShim.dropletsToMb(81L));

            assertEquals(40500L, CapabilityShim.mbToDroplets(500L));
            assertEquals(500L, CapabilityShim.dropletsToMb(40500L));

            // Zero
            assertEquals(0L, CapabilityShim.mbToDroplets(0L));
            assertEquals(0L, CapabilityShim.dropletsToMb(0L));
        }
    }

    // =========================================================================
    // Tier 2: Boundary & Edge Case Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 2: Boundary & Edge Cases")
    class Tier2Boundary {

        @Test
        @DisplayName("2.1 Null parameters across capability and attachment APIs")
        public void testNullParameters() {
            assertNotNull(CapabilityShim.getCapability(null, null, null));
            assertNull(CapabilityShim.getBlockCapability(null, null, null, null));
            assertNull(CapabilityShim.getEntityCapability(null, null, null));
            assertNull(CapabilityShim.getItemCapability(null, null, null));
            assertNull(CapabilityShim.getItemHandler(null, null, null));
            assertNull(CapabilityShim.getFluidHandler(null, null, null));

            assertNull(CapabilityShim.getDataAttachment(null, null));
            assertNull(CapabilityShim.setDataAttachment(null, null, null));
            assertFalse(CapabilityShim.hasDataAttachment(null, null));
            assertNull(CapabilityShim.removeDataAttachment(null, null));

            assertNull(CapabilityShim.resolveItemHandler(null, null));
            assertNull(CapabilityShim.resolveFluidHandler(null, null));
            assertNull(CapabilityShim.resolveEnergyStorage(null, null));
        }

        @Test
        @DisplayName("2.2 Native IAttachmentHolder getData / setData dispatch via reflection")
        public void testNativeAttachmentHolderReflection() {
            Object nativeHolder = new Object() {
                private final Map<Object, Object> store = new HashMap<>();

                public Object getData(Object type) {
                    return store.get(type);
                }

                public Object setData(Object type, Object value) {
                    store.put(type, value);
                    return value;
                }

                public boolean hasData(Object type) {
                    return store.containsKey(type);
                }

                public Object removeData(Object type) {
                    return store.remove(type);
                }
            };

            Object typeKey = "type_key";
            assertFalse(CapabilityShim.hasDataAttachment(nativeHolder, typeKey));

            CapabilityShim.setDataAttachment(nativeHolder, typeKey, "native_value");
            assertTrue(CapabilityShim.hasDataAttachment(nativeHolder, typeKey));
            assertEquals("native_value", CapabilityShim.getDataAttachment(nativeHolder, typeKey));

            Object removed = CapabilityShim.removeDataAttachment(nativeHolder, typeKey);
            assertEquals("native_value", removed);
            assertFalse(CapabilityShim.hasDataAttachment(nativeHolder, typeKey));
        }

        @Test
        @DisplayName("2.3 Provider dispatch: getCapability delegating to native getCapability method")
        public void testProviderNativeGetCapabilityMethod() throws Exception {
            Object mockResult = "NativeCapabilityResult";
            Object mockProvider = new ProviderWithGetCapability(mockResult);

            Object lazyOpt = CapabilityShim.getCapability(mockProvider, "test_cap", "UP");
            assertNotNull(lazyOpt);

            if (lazyOpt instanceof Optional<?> opt) {
                assertTrue(opt.isPresent());
                assertEquals(mockResult, opt.get());
            } else {
                Method resolve = lazyOpt.getClass().getMethod("resolve");
                Optional<?> opt = (Optional<?>) resolve.invoke(lazyOpt);
                assertTrue(opt.isPresent());
                assertEquals(mockResult, opt.get());
            }
        }
    }

    // =========================================================================
    // Tier 3: Cross-Feature & Catalog Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 3: Cross-Feature & Catalog Tests")
    class Tier3CrossFeature {

        @Test
        @DisplayName("3.1 CapabilityAndStorageCatalog contains Forge, NeoForge, and Fabric capability rules")
        public void testCapabilityCatalogCoverage() {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec targetNeo = TargetSpec.of("1.20.4", "neoforge");
            TargetSpec targetFabric = TargetSpec.of("1.20.4", "fabric");

            List<TransformationRule> neoRules = kb.getApplicableRules(base, targetNeo);
            List<TransformationRule> fabricRules = kb.getApplicableRules(base, targetFabric);

            // CapabilityShim redirects for ICapabilityProvider.getCapability
            boolean hasGetCapabilityPolyfill = neoRules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraftforge/common/capabilities/ICapabilityProvider".equals(pr.getSourceOwner()) &&
                    "getCapability".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/CapabilityShim".equals(pr.getShimOwner())
            );
            assertTrue(hasGetCapabilityPolyfill, "ICapabilityProvider.getCapability polyfill must be present for NeoForge");

            // CapabilityItemHandler / CapabilityFluidHandler polyfills
            boolean hasItemHandlerRule = neoRules.stream().anyMatch(r ->
                    (r instanceof PolyfillRule pr && pr.getShimOwner().contains("CapabilityShim") &&
                            ("resolveItemHandler".equals(pr.getShimName()) || "getItemHandler".equals(pr.getShimName()))) ||
                    (r instanceof FieldRedirectRule fr && fr.getTargetOwner().contains("ItemHandler")) ||
                    r.getDescription().contains("ItemHandler")
            );
            assertTrue(hasItemHandlerRule, "ItemHandler resolution rule must be registered");

            boolean hasFluidHandlerRule = neoRules.stream().anyMatch(r ->
                    (r instanceof PolyfillRule pr && pr.getShimOwner().contains("CapabilityShim") &&
                            ("resolveFluidHandler".equals(pr.getShimName()) || "getFluidHandler".equals(pr.getShimName()))) ||
                    (r instanceof FieldRedirectRule fr && fr.getTargetOwner().contains("FluidHandler")) ||
                    r.getDescription().contains("FluidHandler")
            );
            assertTrue(hasFluidHandlerRule, "FluidHandler resolution rule must be registered");

            // Fabric rules
            boolean hasFabricCapabilityRule = fabricRules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    pr.getShimOwner().contains("CapabilityShim")
            );
            assertTrue(hasFabricCapabilityRule, "CapabilityShim rules must be present for Fabric targets");
        }

        @Test
        @DisplayName("3.2 Full cross-platform scenario: BlockEntity with attached mana storage queried via getCapability")
        public void testBlockEntityDataAttachmentIntegration() throws Exception {
            Object blockEntity = new Object();
            Object manaAttachmentType = "magic:mana";

            // Attach 5000 mana
            CapabilityShim.setDataAttachment(blockEntity, manaAttachmentType, 5000);

            // Read attachment
            Object mana = CapabilityShim.getDataAttachment(blockEntity, manaAttachmentType);
            assertEquals(5000, mana);

            // Wrap in LazyOptional and verify consumers
            Object lazyOpt = CapabilityShim.ofLazyOptional(mana);
            if (lazyOpt instanceof Optional<?> opt) {
                assertTrue(opt.isPresent());
                assertEquals(5000, opt.get());
            } else {
                AtomicInteger retrievedMana = new AtomicInteger();
                Method ifPresent = lazyOpt.getClass().getMethod("ifPresent", Consumer.class);
                ifPresent.invoke(lazyOpt, (Consumer<Object>) val -> retrievedMana.set((Integer) val));
                assertEquals(5000, retrievedMana.get());
            }
        }
    }

    // =========================================================================
    // Tier 4: Workload & Concurrency Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 4: Workload & Concurrency Tests")
    class Tier4Workload {

        @Test
        @DisplayName("4.1 Concurrent reads, writes, and invalidations across multiple holders and attachments")
        public void testConcurrentDataAttachments() throws InterruptedException, ExecutionException {
            int threadCount = 12;
            int opsPerThread = 200;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            List<Future<Void>> futures = new ArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                futures.add(pool.submit(() -> {
                    Object holder = new Object();
                    for (int i = 0; i < opsPerThread; i++) {
                        String type = "thread_" + threadId + "_type_" + (i % 10);
                        CapabilityShim.setDataAttachment(holder, type, i);
                        assertEquals(i, CapabilityShim.getDataAttachment(holder, type));
                        assertTrue(CapabilityShim.hasDataAttachment(holder, type));

                        // Droplet conversion consistency
                        long droplets = CapabilityShim.mbToDroplets(i);
                        assertEquals(i, CapabilityShim.dropletsToMb(droplets));

                        // LazyOptional test
                        Object opt = CapabilityShim.ofLazyOptional(i);
                        if (opt instanceof Optional<?> rawOpt) {
                            @SuppressWarnings("unchecked")
                            Optional<Object> o = (Optional<Object>) rawOpt;
                            assertEquals(i, o.orElse(-1));
                        } else {
                            Method orElse = opt.getClass().getMethod("orElse", Object.class);
                            assertEquals(i, orElse.invoke(opt, -1));
                        }
                    }
                    return null;
                }));
            }

            for (Future<Void> f : futures) {
                f.get();
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    public static class AttachmentTypeWithDefault {
        public Object getDefaultValue() {
            return "default_mana_pool";
        }
    }

    public static class ProviderWithGetCapability {
        private final Object result;

        public ProviderWithGetCapability(Object result) {
            this.result = result;
        }

        public Object getCapability(Object cap, Object dir) {
            return CapabilityShim.ofLazyOptional(result);
        }
    }
}
