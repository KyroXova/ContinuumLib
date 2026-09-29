package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Transformation Rules for Particles and ParticleOptions.
 */
public final class ParticleRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_17_0 = MCVersion.of("1.17");
        MCVersion v1_16_5 = MCVersion.of("1.16.5");

        // 1. ParticleOptions <-> IParticleData class redirects
        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/particles/IParticleData",
                "net/minecraft/core/particles/ParticleOptions",
                v1_17_0, null, null,
                "IParticleData -> ParticleOptions (1.17+)"
        ));

        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/core/particles/ParticleOptions",
                "net/minecraft/particles/IParticleData",
                null, v1_16_5, null,
                "ParticleOptions -> IParticleData (<= 1.16.5)"
        ));

        // 2. ParticleType class redirects
        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/particles/ParticleType",
                "net/minecraft/core/particles/ParticleType",
                v1_17_0, null, null,
                "ParticleType -> core.particles.ParticleType (1.17+)"
        ));

        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/core/particles/ParticleType",
                "net/minecraft/particles/ParticleType",
                null, v1_16_5, null,
                "core.particles.ParticleType -> ParticleType (<= 1.16.5)"
        ));

        // 3. Level.addParticle polyfill
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/level/Level", "addParticle",
                "(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V",
                "com/kyroxova/continuumlib/shims/ParticleShim", "addParticle",
                "(Ljava/lang/Object;Ljava/lang/Object;DDDDDD)V",
                null, null, null,
                "Level.addParticle -> ParticleShim.addParticle"
        ));
    }
}
