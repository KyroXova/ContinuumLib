package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import com.kyroxova.continuumlib.shims.VoxelShapeShim;
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
 * VoxelShape & Bounding Box (AABB) Geometry (1.7.9 -> 26.3+).
 * Covers box creation, union (or), bounding box conversions, and collision checks.
 */
public class VoxelShapeVerificationTest {

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
        @DisplayName("1.1 Box creation via VoxelShapeShim.box and geometry validation")
        public void testBoxCreation() {
            Object shapeObj = VoxelShapeShim.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);
            assertNotNull(shapeObj);

            VoxelShapeShim.VirtualAABB aabb = VoxelShapeShim.toAABB(shapeObj);
            assertEquals(2.0, aabb.minX);
            assertEquals(0.0, aabb.minY);
            assertEquals(2.0, aabb.minZ);
            assertEquals(14.0, aabb.maxX);
            assertEquals(16.0, aabb.maxY);
            assertEquals(14.0, aabb.maxZ);

            if (shapeObj instanceof VoxelShapeShim.VirtualVoxelShape vvs) {
                assertFalse(vvs.isEmpty());
                assertFalse(vvs.isFullBlock());
                assertTrue(vvs.toString().contains("2.0"));
            }
        }

        @Test
        @DisplayName("1.2 Union (or) of two shapes merges bounding regions correctly")
        public void testUnionOrShapes() {
            Object shape1 = VoxelShapeShim.box(0, 0, 0, 8, 8, 8);
            Object shape2 = VoxelShapeShim.box(8, 8, 8, 16, 16, 16);

            Object union = VoxelShapeShim.or(shape1, shape2);
            assertNotNull(union);

            VoxelShapeShim.VirtualAABB unionAABB = VoxelShapeShim.toAABB(union);
            assertEquals(0.0, unionAABB.minX);
            assertEquals(0.0, unionAABB.minY);
            assertEquals(0.0, unionAABB.minZ);
            assertEquals(16.0, unionAABB.maxX);
            assertEquals(16.0, unionAABB.maxY);
            assertEquals(16.0, unionAABB.maxZ);

            // Null inputs return opposing shape
            assertSame(shape1, VoxelShapeShim.or(shape1, null));
            assertSame(shape2, VoxelShapeShim.or(null, shape2));
        }

        @Test
        @DisplayName("1.3 Bounding box conversions: toAABB, fromAABB, empty, and full block")
        public void testBoundingBoxConversions() {
            VoxelShapeShim.VirtualVoxelShape empty = VoxelShapeShim.empty();
            assertTrue(empty.isEmpty());

            VoxelShapeShim.VirtualVoxelShape full = VoxelShapeShim.block();
            assertTrue(full.isFullBlock());

            VoxelShapeShim.VirtualAABB aabb = new VoxelShapeShim.VirtualAABB(1.0, 2.0, 3.0, 4.0, 5.0, 6.0);
            Object shapeFromAABB = VoxelShapeShim.fromAABB(aabb);
            VoxelShapeShim.VirtualAABB converted = VoxelShapeShim.toAABB(shapeFromAABB);
            assertEquals(aabb, converted);

            // Intersection and containment
            VoxelShapeShim.VirtualAABB overlapping = new VoxelShapeShim.VirtualAABB(2.0, 2.0, 2.0, 5.0, 5.0, 5.0);
            assertTrue(aabb.intersects(overlapping));
            assertTrue(aabb.contains(2.0, 3.0, 4.0));
            assertFalse(aabb.contains(0.0, 0.0, 0.0));
        }
    }

    // =========================================================================
    // Tier 2: Rules Catalog & Transformation Delta Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 2: Rules Catalog & Transformation Delta Tests")
    class Tier2RulesCatalog {

        @Test
        @DisplayName("2.1 Modernizing 1.16.5 -> 1.18.2 maps VoxelShapes -> Shapes and AxisAlignedBB -> AABB")
        public void testModernizeVoxelShapeRules() {
            TargetSpec base = TargetSpec.of("1.16.5", "forge");
            TargetSpec target = TargetSpec.of("1.18.2", "forge");

            List<TransformationRule> rules = kb.getApplicableRules(base, target);

            boolean hasVoxelShapeRedirect = rules.stream().anyMatch(r ->
                    r instanceof ClassRedirectRule cr &&
                    "net/minecraft/util/math/shapes/VoxelShape".equals(cr.getSourceInternalName()) &&
                    "net/minecraft/world/phys/shapes/VoxelShape".equals(cr.getTargetInternalName())
            );
            assertTrue(hasVoxelShapeRedirect, "util.math.shapes.VoxelShape must redirect to world.phys.shapes.VoxelShape on 1.17+");

            boolean hasVoxelShapesRedirect = rules.stream().anyMatch(r ->
                    r instanceof ClassRedirectRule cr &&
                    "net/minecraft/util/math/shapes/VoxelShapes".equals(cr.getSourceInternalName()) &&
                    "net/minecraft/world/phys/shapes/Shapes".equals(cr.getTargetInternalName())
            );
            assertTrue(hasVoxelShapesRedirect, "VoxelShapes must redirect to Shapes on 1.17+");

            boolean hasAABBRedirect = rules.stream().anyMatch(r ->
                    r instanceof ClassRedirectRule cr &&
                    "net/minecraft/util/math/AxisAlignedBB".equals(cr.getSourceInternalName()) &&
                    "net/minecraft/world/phys/AABB".equals(cr.getTargetInternalName())
            );
            assertTrue(hasAABBRedirect, "AxisAlignedBB must redirect to AABB on 1.17+");

            boolean hasBlockBoxPolyfill = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/world/level/block/Block".equals(pr.getSourceOwner()) &&
                    "box".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/VoxelShapeShim".equals(pr.getShimOwner())
            );
            assertTrue(hasBlockBoxPolyfill, "Block.box must polyfill through VoxelShapeShim");
        }

        @Test
        @DisplayName("2.2 Downgrading 1.18.2 -> 1.16.5 maps Shapes -> VoxelShapes and AABB -> AxisAlignedBB")
        public void testDowngradeVoxelShapeRules() {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target = TargetSpec.of("1.16.5", "forge");

            List<TransformationRule> rules = kb.getApplicableRules(base, target);

            boolean hasShapesToLegacy = rules.stream().anyMatch(r ->
                    r instanceof ClassRedirectRule cr &&
                    "net/minecraft/world/phys/shapes/Shapes".equals(cr.getSourceInternalName()) &&
                    "net/minecraft/util/math/shapes/VoxelShapes".equals(cr.getTargetInternalName())
            );
            assertTrue(hasShapesToLegacy, "Shapes must redirect to VoxelShapes on <= 1.16.5");

            boolean hasAABBToLegacy = rules.stream().anyMatch(r ->
                    r instanceof ClassRedirectRule cr &&
                    "net/minecraft/world/phys/AABB".equals(cr.getSourceInternalName()) &&
                    "net/minecraft/util/math/AxisAlignedBB".equals(cr.getTargetInternalName())
            );
            assertTrue(hasAABBToLegacy, "AABB must redirect to AxisAlignedBB on <= 1.16.5");
        }
    }

    // =========================================================================
    // Tier 3: Concurrency & Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 3: Concurrency & Stress Tests")
    class Tier3Concurrency {

        @Test
        @DisplayName("3.1 Concurrent box generation, union operations, and bounds immutability")
        public void testConcurrentShapeGeometry() throws InterruptedException {
            int threadCount = 16;
            int opsPerThread = 250;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger verifiedUnions = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                final double offset = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < opsPerThread; i++) {
                            Object s1 = VoxelShapeShim.box(offset, 0, offset, offset + 2, 4, offset + 2);
                            Object s2 = VoxelShapeShim.box(offset + 1, 1, offset + 1, offset + 3, 5, offset + 3);
                            Object union = VoxelShapeShim.or(s1, s2);
                            VoxelShapeShim.VirtualAABB aabb = VoxelShapeShim.toAABB(union);
                            if (aabb.minX == offset && aabb.maxX == offset + 3 && aabb.maxY == 5) {
                                verifiedUnions.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent voxel shape geometry timed out");
            executor.shutdown();
            assertEquals(threadCount * opsPerThread, verifiedUnions.get());
        }
    }
}
