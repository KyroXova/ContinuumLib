package com.kyroxova.continuumlib.knowledgebase;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.FieldRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class UniversalCatalogsTest {

    @Test
    public void testUniversalCatalogsCoverage() {
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec targetNeo20_5 = TargetSpec.of("1.20.5", "neoforge");

        List<TransformationRule> rules = kb.getApplicableRules(base, targetNeo20_5);
        assertFalse(rules.isEmpty());

        // 1. Check Text & Chat: TranslatableComponent
        boolean hasTranslatablePolyfill = rules.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                pr.getSourceOwner().equals("net/minecraft/network/chat/TranslatableComponent") &&
                pr.getShimOwner().equals("com/kyroxova/continuumlib/shims/ComponentShim")
        );
        assertTrue(hasTranslatablePolyfill, "Expected TranslatableComponent polyfill");

        // 2. Check UI & Screen: Font.draw
        boolean hasFontDrawPolyfill = rules.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                pr.getSourceOwner().equals("net/minecraft/client/gui/Font") &&
                pr.getShimOwner().equals("com/kyroxova/continuumlib/shims/ScreenRenderingShim")
        );
        assertTrue(hasFontDrawPolyfill, "Expected Font.draw polyfill");

        // 3. Check ItemStack NBT -> Data Components (1.20.5+)
        boolean hasItemStackNbtPolyfill = rules.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                pr.getSourceOwner().equals("net/minecraft/world/item/ItemStack") &&
                pr.getShimOwner().equals("com/kyroxova/continuumlib/shims/ItemStackShim")
        );
        assertTrue(hasItemStackNbtPolyfill, "Expected ItemStack NBT polyfill for 1.20.5+");

        // 4. Check Reflection: ObfuscationReflectionHelper
        boolean hasReflectionPolyfill = rules.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                pr.getSourceOwner().equals("net/minecraftforge/fml/util/ObfuscationReflectionHelper") &&
                pr.getShimOwner().equals("com/kyroxova/continuumlib/shims/ReflectionHelperShim")
        );
        assertTrue(hasReflectionPolyfill, "Expected ObfuscationReflectionHelper polyfill");

        // 5. Check Lifecycle: EVENT_BUS
        boolean hasEventBusRedirect = rules.stream().anyMatch(r ->
                r instanceof FieldRedirectRule fr &&
                fr.getSourceOwner().equals("net/minecraftforge/common/MinecraftForge") &&
                fr.getTargetOwner().equals("net/neoforged/neoforge/common/NeoForge")
        );
        assertTrue(hasEventBusRedirect, "Expected EVENT_BUS redirect to NeoForge");

        // 6. Check Registry: ResourceLocation
        boolean hasResourceLocationPolyfill = rules.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                pr.getSourceOwner().equals("net/minecraft/resources/ResourceLocation") &&
                pr.getShimOwner().equals("com/kyroxova/continuumlib/shims/ResourceLocationShim")
        );
        assertTrue(hasResourceLocationPolyfill, "Expected ResourceLocation polyfill");
    }
}
