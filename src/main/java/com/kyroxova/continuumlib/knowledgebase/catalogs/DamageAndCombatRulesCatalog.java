package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for DamageSources, Combat, and DamageTypes.
 * Bridges legacy DamageSource static accessors to 1.20+ DamageSources.
 */
public final class DamageAndCombatRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_20_0 = MCVersion.of("1.20");

        // 1. DamageSource.GENERIC -> DamageSourceShim.generic()
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/damagesource/DamageSource", "GENERIC",
                "Lnet/minecraft/world/damagesource/DamageSource;",
                "com/kyroxova/continuumlib/shims/DamageSourceShim", "generic",
                "()Ljava/lang/Object;",
                v1_20_0, null, null,
                "DamageSource.GENERIC -> DamageSourceShim.generic()"
        ));

        // 2. DamageSource.MAGIC -> DamageSourceShim.magic()
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/damagesource/DamageSource", "MAGIC",
                "Lnet/minecraft/world/damagesource/DamageSource;",
                "com/kyroxova/continuumlib/shims/DamageSourceShim", "magic",
                "()Ljava/lang/Object;",
                v1_20_0, null, null,
                "DamageSource.MAGIC -> DamageSourceShim.magic()"
        ));

        // 3. DamageSource.FALL -> DamageSourceShim.fall()
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/damagesource/DamageSource", "FALL",
                "Lnet/minecraft/world/damagesource/DamageSource;",
                "com/kyroxova/continuumlib/shims/DamageSourceShim", "fall",
                "()Ljava/lang/Object;",
                v1_20_0, null, null,
                "DamageSource.FALL -> DamageSourceShim.fall()"
        ));

        // 4. DamageSource.OUT_OF_WORLD -> DamageSourceShim.outOfWorld()
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/damagesource/DamageSource", "OUT_OF_WORLD",
                "Lnet/minecraft/world/damagesource/DamageSource;",
                "com/kyroxova/continuumlib/shims/DamageSourceShim", "outOfWorld",
                "()Ljava/lang/Object;",
                v1_20_0, null, null,
                "DamageSource.OUT_OF_WORLD -> DamageSourceShim.outOfWorld()"
        ));

        // 5. DamageSource.IN_FIRE -> DamageSourceShim.inFire()
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/damagesource/DamageSource", "IN_FIRE",
                "Lnet/minecraft/world/damagesource/DamageSource;",
                "com/kyroxova/continuumlib/shims/DamageSourceShim", "inFire",
                "()Ljava/lang/Object;",
                v1_20_0, null, null,
                "DamageSource.IN_FIRE -> DamageSourceShim.inFire()"
        ));

        // 6. DamageSource.ON_FIRE -> DamageSourceShim.onFire()
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/damagesource/DamageSource", "ON_FIRE",
                "Lnet/minecraft/world/damagesource/DamageSource;",
                "com/kyroxova/continuumlib/shims/DamageSourceShim", "onFire",
                "()Ljava/lang/Object;",
                v1_20_0, null, null,
                "DamageSource.ON_FIRE -> DamageSourceShim.onFire()"
        ));

        // 7. DamageSource.LAVA -> DamageSourceShim.lava()
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/damagesource/DamageSource", "LAVA",
                "Lnet/minecraft/world/damagesource/DamageSource;",
                "com/kyroxova/continuumlib/shims/DamageSourceShim", "lava",
                "()Ljava/lang/Object;",
                v1_20_0, null, null,
                "DamageSource.LAVA -> DamageSourceShim.lava()"
        ));

        // 8. DamageSource.DROWN -> DamageSourceShim.drown()
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/damagesource/DamageSource", "DROWN",
                "Lnet/minecraft/world/damagesource/DamageSource;",
                "com/kyroxova/continuumlib/shims/DamageSourceShim", "drown",
                "()Ljava/lang/Object;",
                v1_20_0, null, null,
                "DamageSource.DROWN -> DamageSourceShim.drown()"
        ));

        // 9. DamageSource.STARVE -> DamageSourceShim.starve()
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/damagesource/DamageSource", "STARVE",
                "Lnet/minecraft/world/damagesource/DamageSource;",
                "com/kyroxova/continuumlib/shims/DamageSourceShim", "starve",
                "()Ljava/lang/Object;",
                v1_20_0, null, null,
                "DamageSource.STARVE -> DamageSourceShim.starve()"
        ));

        // 10. DamageSource.WITHER -> DamageSourceShim.wither()
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/damagesource/DamageSource", "WITHER",
                "Lnet/minecraft/world/damagesource/DamageSource;",
                "com/kyroxova/continuumlib/shims/DamageSourceShim", "wither",
                "()Ljava/lang/Object;",
                v1_20_0, null, null,
                "DamageSource.WITHER -> DamageSourceShim.wither()"
        ));

        // 11. DamageSource.playerAttack(Player) -> DamageSourceShim.playerAttack(Player)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/damagesource/DamageSource", "playerAttack",
                "(Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/damagesource/DamageSource;",
                "com/kyroxova/continuumlib/shims/DamageSourceShim", "playerAttack",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                v1_20_0, null, null,
                "DamageSource.playerAttack -> DamageSourceShim.playerAttack"
        ));
    }
}
