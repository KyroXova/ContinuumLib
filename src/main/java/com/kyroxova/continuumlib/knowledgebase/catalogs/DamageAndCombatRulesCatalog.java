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

        // 3. DamageSource.playerAttack(Player) -> DamageSourceShim.playerAttack(Player)
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
