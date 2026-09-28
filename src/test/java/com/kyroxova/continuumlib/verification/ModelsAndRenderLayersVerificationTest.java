package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import com.kyroxova.continuumlib.shims.RenderTypeShim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Verification Suite for Subsystem e:
 * Block & Item Models / Render Layers across Forge, NeoForge, and Fabric (1.7.9 -> 26.3+).
 */
public class ModelsAndRenderLayersVerificationTest {

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
        @DisplayName("1.1 setRenderLayer fluent execution and modern no-op behavior")
        public void testSetRenderLayerNoOp() {
            Object mockBlock = new Object();
            Object mockRenderType = "CUTOUT_MIPPED";

            // Must execute without exception even on standalone environment
            assertDoesNotThrow(() -> RenderTypeShim.setRenderLayer(mockBlock, mockRenderType));
        }

        @Test
        @DisplayName("1.2 setFluidRenderLayer fluent execution")
        public void testSetFluidRenderLayerNoOp() {
            Object mockFluid = new Object();
            Object mockRenderType = "TRANSLUCENT";

            assertDoesNotThrow(() -> RenderTypeShim.setFluidRenderLayer(mockFluid, mockRenderType));
        }

        @Test
        @DisplayName("1.3 Model loader registration and lookup: registerModelLoader / registerGeometryLoader")
        public void testModelLoaderRegistration() {
            String loaderId = "mymod:custom_obj_loader";
            Object mockLoader = new Object() {
                public String getLoaderType() {
                    return "wavefront_obj";
                }
            };

            Object registered = RenderTypeShim.registerModelLoader(loaderId, mockLoader);
            assertSame(mockLoader, registered);

            Object retrieved = RenderTypeShim.getModelLoader(loaderId);
            assertSame(mockLoader, retrieved);

            // registerGeometryLoader alias
            String geoLoaderId = "mymod:custom_geo_loader";
            RenderTypeShim.registerGeometryLoader(geoLoaderId, mockLoader);
            assertSame(mockLoader, RenderTypeShim.getModelLoader(geoLoaderId));
        }
    }

    // =========================================================================
    // Tier 2: Boundary & Edge Case Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 2: Boundary & Edge Cases")
    class Tier2Boundary {

        @Test
        @DisplayName("2.1 Null parameters across RenderTypeShim APIs")
        public void testNullParameters() {
            assertDoesNotThrow(() -> RenderTypeShim.setRenderLayer(null, null));
            assertDoesNotThrow(() -> RenderTypeShim.setRenderLayer(new Object(), null));
            assertDoesNotThrow(() -> RenderTypeShim.setRenderLayer(null, "SOLID"));

            assertDoesNotThrow(() -> RenderTypeShim.setFluidRenderLayer(null, null));
            assertDoesNotThrow(() -> RenderTypeShim.setFluidRenderLayer(new Object(), null));

            assertNull(RenderTypeShim.registerModelLoader(null, null));
            assertNull(RenderTypeShim.registerGeometryLoader(null, null));
            assertNull(RenderTypeShim.getModelLoader(null));
            assertNull(RenderTypeShim.getModelLoader("non_existent_loader"));
        }

        @Test
        @DisplayName("2.2 Overwriting model loaders preserves the latest registration")
        public void testModelLoaderOverwrite() {
            String id = "mymod:replaceable_loader";
            Object loader1 = new Object();
            Object loader2 = new Object();

            RenderTypeShim.registerModelLoader(id, loader1);
            assertSame(loader1, RenderTypeShim.getModelLoader(id));

            RenderTypeShim.registerModelLoader(id, loader2);
            assertSame(loader2, RenderTypeShim.getModelLoader(id));
        }
    }

    // =========================================================================
    // Tier 3: Cross-Feature & Catalog Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 3: Cross-Feature & Catalog Tests")
    class Tier3CrossFeature {

        @Test
        @DisplayName("3.1 ClientRenderingAndGuiCatalog contains setRenderLayer and model loader rules")
        public void testClientRenderingCatalogCoverage() {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target1204 = TargetSpec.of("1.20.4", "neoforge");
            TargetSpec targetFabric = TargetSpec.of("1.20.4", "fabric");

            List<TransformationRule> rules = kb.getApplicableRules(base, target1204);
            List<TransformationRule> fabricRules = kb.getApplicableRules(base, targetFabric);

            // setRenderLayer rule
            boolean hasSetRenderLayer = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/client/renderer/ItemBlockRenderTypes".equals(pr.getSourceOwner()) &&
                    "setRenderLayer".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/RenderTypeShim".equals(pr.getShimOwner())
            );
            assertTrue(hasSetRenderLayer, "ItemBlockRenderTypes.setRenderLayer polyfill must be registered");

            // ModelLoaderRegistry rule (Fabric target)
            boolean hasModelLoader = fabricRules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    pr.getSourceOwner().contains("ModelLoader") &&
                    pr.getShimOwner().contains("RenderTypeShim")
            );
            assertTrue(hasModelLoader, "Model loader polyfill must be registered in ClientRenderingAndGuiCatalog for Fabric");
        }
    }

    // =========================================================================
    // Tier 4: Workload & Concurrency Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 4: Workload & Concurrency Tests")
    class Tier4Workload {

        @Test
        @DisplayName("4.1 Concurrent model loader registrations and setRenderLayer invocations")
        public void testConcurrentModelAndRenderOperations() throws InterruptedException, ExecutionException {
            int threadCount = 10;
            int opsPerThread = 200;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            List<Future<Void>> futures = new ArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < opsPerThread; i++) {
                        String id = "loader_" + threadId + "_" + i;
                        Object loader = new Object();
                        RenderTypeShim.registerModelLoader(id, loader);
                        assertSame(loader, RenderTypeShim.getModelLoader(id));

                        RenderTypeShim.setRenderLayer(loader, "CUTOUT");
                        RenderTypeShim.setFluidRenderLayer(loader, "TRANSLUCENT");
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
}
