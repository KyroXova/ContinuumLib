package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for Explosions, Block Interactions, and Sound Playback.
 * Bridges:
 * 1. Level.explode across void (1.20+ / 26.3+) vs Explosion (<= 1.19.4).
 * 2. Level.ExplosionInteraction (>= 1.19.3) vs Explosion.BlockInteraction (<= 1.19.2).
 * 3. SoundCategory (<= 1.16.5) vs SoundSource (1.17+ / 26.3+).
 * 4. Level.playSound dispatch with Entity/Player and BlockPos/coordinates.
 */
public final class ExplosionAndPhysicsRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_17_0 = MCVersion.of("1.17");
        MCVersion v1_16_5 = MCVersion.of("1.16.5");
        MCVersion v1_19_2 = MCVersion.of("1.19.2");
        MCVersion v1_19_3 = MCVersion.of("1.19.3");

        // 1. SoundCategory <-> SoundSource class redirects
        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/util/SoundCategory",
                "net/minecraft/sounds/SoundSource",
                v1_17_0, null, null,
                "SoundCategory -> SoundSource (1.17+)"
        ));

        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/sounds/SoundSource",
                "net/minecraft/util/SoundCategory",
                null, v1_16_5, null,
                "SoundSource -> SoundCategory (<= 1.16.5)"
        ));

        // 2. Explosion$BlockInteraction <-> Level$ExplosionInteraction class redirects
        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/world/level/Explosion$BlockInteraction",
                "net/minecraft/world/level/Level$ExplosionInteraction",
                v1_19_3, null, null,
                "Explosion.BlockInteraction -> Level.ExplosionInteraction (1.19.3+)"
        ));

        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/world/level/Level$ExplosionInteraction",
                "net/minecraft/world/level/Explosion$BlockInteraction",
                null, v1_19_2, null,
                "Level.ExplosionInteraction -> Explosion.BlockInteraction (<= 1.19.2)"
        ));

        // 3. Level.explode polyfill rules
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/level/Level", "explode",
                null,
                "com/kyroxova/continuumlib/shims/ExplosionShim", "explode",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;DDDDFZLjava/lang/Object;)Ljava/lang/Object;",
                null, null, null,
                "Level.explode -> ExplosionShim.explode"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/World", "explode",
                null,
                "com/kyroxova/continuumlib/shims/ExplosionShim", "explode",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;DDDDFZLjava/lang/Object;)Ljava/lang/Object;",
                null, null, null,
                "World.explode -> ExplosionShim.explode"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/World", "createExplosion",
                null,
                "com/kyroxova/continuumlib/shims/ExplosionShim", "explode",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;DDDDFZLjava/lang/Object;)Ljava/lang/Object;",
                null, null, null,
                "World.createExplosion -> ExplosionShim.explode"
        ));

        // 4. Level.playSound polyfill rules
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/level/Level", "playSound",
                null,
                "com/kyroxova/continuumlib/shims/SoundPlaybackShim", "playSound",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;FF)Z",
                null, null, null,
                "Level.playSound -> SoundPlaybackShim.playSound"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/World", "playSound",
                null,
                "com/kyroxova/continuumlib/shims/SoundPlaybackShim", "playSound",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;FF)Z",
                null, null, null,
                "World.playSound -> SoundPlaybackShim.playSound"
        ));
    }
}
