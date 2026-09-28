package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for Math, Vectors, and Quaternions.
 * Bridges com.mojang.math <-> org.joml and Axis rotations (1.19.3+).
 */
public final class MathAndVectorsCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_19_3 = MCVersion.of("1.19.3");

        // 1. Vector3f & Quaternion Class Redirects (1.19.3+)
        kb.registerRule(new ClassRedirectRule(
                "com/mojang/math/Vector3f",
                "org/joml/Vector3f",
                v1_19_3, null, null,
                "Vector3f -> org.joml.Vector3f (1.19.3+)"
        ));

        kb.registerRule(new ClassRedirectRule(
                "com/mojang/math/Quaternion",
                "org/joml/Quaternionf",
                v1_19_3, null, null,
                "Quaternion -> org.joml.Quaternionf (1.19.3+)"
        ));

        // 2. Vector3f.YP.rotationDegrees(float) -> MathShim.rotateYDegrees(float)
        kb.registerRule(new PolyfillRule(
                "com/mojang/math/Vector3f", "rotationDegrees",
                "(F)Lcom/mojang/math/Quaternion;",
                "com/kyroxova/continuumlib/shims/MathShim", "rotateYDegrees",
                "(F)Ljava/lang/Object;",
                v1_19_3, null, null,
                "Vector3f.rotationDegrees -> MathShim.rotateYDegrees (1.19.3+)"
        ));
    }
}
