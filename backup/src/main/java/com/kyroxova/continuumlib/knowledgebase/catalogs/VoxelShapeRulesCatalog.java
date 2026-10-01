package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for VoxelShape, Shapes, and AxisAlignedBB / AABB.
 */
public final class VoxelShapeRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_17_0 = MCVersion.of("1.17");
        MCVersion v1_16_5 = MCVersion.of("1.16.5");

        // 1. VoxelShape class redirects
        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/util/math/shapes/VoxelShape",
                "net/minecraft/world/phys/shapes/VoxelShape",
                v1_17_0, null, null,
                "util.math.shapes.VoxelShape -> world.phys.shapes.VoxelShape (1.17+)"
        ));

        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/world/phys/shapes/VoxelShape",
                "net/minecraft/util/math/shapes/VoxelShape",
                null, v1_16_5, null,
                "world.phys.shapes.VoxelShape -> util.math.shapes.VoxelShape (<= 1.16.5)"
        ));

        // 2. Shapes / VoxelShapes class redirects
        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/util/math/shapes/VoxelShapes",
                "net/minecraft/world/phys/shapes/Shapes",
                v1_17_0, null, null,
                "VoxelShapes -> Shapes (1.17+)"
        ));

        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/world/phys/shapes/Shapes",
                "net/minecraft/util/math/shapes/VoxelShapes",
                null, v1_16_5, null,
                "Shapes -> VoxelShapes (<= 1.16.5)"
        ));

        // 3. AABB / AxisAlignedBB class redirects
        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/util/math/AxisAlignedBB",
                "net/minecraft/world/phys/AABB",
                v1_17_0, null, null,
                "AxisAlignedBB -> AABB (1.17+)"
        ));

        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/world/phys/AABB",
                "net/minecraft/util/math/AxisAlignedBB",
                null, v1_16_5, null,
                "AABB -> AxisAlignedBB (<= 1.16.5)"
        ));

        // 4. Block.box polyfill
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/level/block/Block", "box",
                "(DDDDDD)Lnet/minecraft/world/phys/shapes/VoxelShape;",
                "com/kyroxova/continuumlib/shims/VoxelShapeShim", "box",
                "(DDDDDD)Ljava/lang/Object;",
                null, null, null,
                "Block.box -> VoxelShapeShim.box"
        ));
    }
}
