package com.kyroxova.continuumlib.knowledgebase;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.rules.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Master catalog containing all version-to-version and loader-to-loader API transformation rules.
 */
public final class ApiKnowledgeBase {

    private static final Logger LOGGER = Logger.getLogger(ApiKnowledgeBase.class.getName());

    private final List<TransformationRule> rules = new ArrayList<>();

    public ApiKnowledgeBase() {}

    public static ApiKnowledgeBase createDefault() {
        ApiKnowledgeBase kb = new ApiKnowledgeBase();
        kb.registerDefaultRules();
        return kb;
    }

    public void registerRule(TransformationRule rule) {
        if (rule != null) {
            rules.add(rule);
        }
    }

    public List<TransformationRule> getApplicableRules(TargetSpec baseSpec, TargetSpec targetSpec) {
        List<TransformationRule> active = new ArrayList<>();
        for (TransformationRule rule : rules) {
            if (rule.appliesTo(baseSpec, targetSpec)) {
                active.add(rule);
            }
        }
        return Collections.unmodifiableList(active);
    }

    private void registerDefaultRules() {
        registerContemporaryNeoForgeRules();
        registerMaterialRemovalRules();
        registerCreativeTabRules();
        registerFabricAdaptationRules();
    }

    /**
     * Rules when moving to NeoForge 1.20.4+
     */
    private void registerContemporaryNeoForgeRules() {
        MCVersion v1_20_4 = MCVersion.of("1.20.4");

        // Class Redirects: Forge -> NeoForge
        rules.add(new ClassRedirectRule(
                "net/minecraftforge/registries/RegistryObject",
                "net/neoforged/neoforge/registries/DeferredHolder",
                v1_20_4, null, LoaderType.NEOFORGE,
                "Redirect RegistryObject to DeferredHolder in NeoForge 1.20.4+"
        ));

        rules.add(new ClassRedirectRule(
                "net/minecraftforge/registries/DeferredRegister",
                "net/neoforged/neoforge/registries/DeferredRegister",
                v1_20_4, null, LoaderType.NEOFORGE,
                "Redirect DeferredRegister to NeoForge DeferredRegister"
        ));

        rules.add(new ClassRedirectRule(
                "net/minecraftforge/fml/common/Mod",
                "net/neoforged/fml/common/Mod",
                v1_20_4, null, LoaderType.NEOFORGE,
                "Redirect @Mod to NeoForge @Mod"
        ));

        rules.add(new ClassRedirectRule(
                "net/minecraftforge/eventbus/api/SubscribeEvent",
                "net/neoforged/bus/api/SubscribeEvent",
                v1_20_4, null, LoaderType.NEOFORGE,
                "Redirect @SubscribeEvent to NeoForge event bus"
        ));

        rules.add(new ClassRedirectRule(
                "net/minecraftforge/eventbus/api/IEventBus",
                "net/neoforged/bus/api/IEventBus",
                v1_20_4, null, LoaderType.NEOFORGE,
                "Redirect IEventBus to NeoForge IEventBus"
        ));
    }

    /**
     * Rules for Minecraft 1.20+ Material removal
     */
    private void registerMaterialRemovalRules() {
        MCVersion v1_20_0 = MCVersion.of("1.20");

        // Polyfill Properties.of(Material) -> BlockPropertiesShim.ofLegacyMaterial(Object)
        rules.add(new PolyfillRule(
                "net/minecraft/world/level/block/state/BlockBehaviour$Properties",
                "of",
                "(Lnet/minecraft/world/level/material/Material;)Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;",
                "com/kyroxova/continuumlib/shims/BlockPropertiesShim",
                "ofLegacyMaterial",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                v1_20_0, null, null,
                "Polyfill removed BlockProperties.of(Material) for MC 1.20+"
        ));

        // Polyfill Properties.of(Material, MaterialColor) -> BlockPropertiesShim.ofLegacyMaterialAndColor(Object, Object)
        rules.add(new PolyfillRule(
                "net/minecraft/world/level/block/state/BlockBehaviour$Properties",
                "of",
                "(Lnet/minecraft/world/level/material/Material;Lnet/minecraft/world/level/material/MaterialColor;)Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;",
                "com/kyroxova/continuumlib/shims/BlockPropertiesShim",
                "ofLegacyMaterialAndColor",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                v1_20_0, null, null,
                "Polyfill removed BlockProperties.of(Material, MaterialColor) for MC 1.20+"
        ));
    }

    /**
     * Rules for Creative Mode Tab overhaul in 1.19.3+ and 1.20+
     */
    private void registerCreativeTabRules() {
        MCVersion v1_19_3 = MCVersion.of("1.19.3");

        // Polyfill Item.Properties.tab(CreativeModeTab) -> CreativeTabShim.tab(Properties, CreativeModeTab)
        rules.add(new PolyfillRule(
                "net/minecraft/world/item/Item$Properties",
                "tab",
                "(Lnet/minecraft/world/item/CreativeModeTab;)Lnet/minecraft/world/item/Item$Properties;",
                "com/kyroxova/continuumlib/shims/CreativeTabShim",
                "tab",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                v1_19_3, null, null,
                "Polyfill removed Item.Properties.tab() in MC 1.19.3+"
        ));
    }

    /**
     * Rules when targeting Fabric
     */
    private void registerFabricAdaptationRules() {
        // Redirect DeferredRegister.register(...) to RegistryShim.registerFabric(...)
        rules.add(new PolyfillRule(
                "net/minecraftforge/registries/DeferredRegister",
                "register",
                "(Ljava/lang/String;Ljava/util/function/Supplier;)Lnet/minecraftforge/registries/RegistryObject;",
                "com/kyroxova/continuumlib/shims/RegistryShim",
                "registerFabric",
                "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/util/function/Supplier;)Ljava/lang/Object;",
                null, null, LoaderType.FABRIC,
                "Bridge Forge DeferredRegister to Fabric Registry"
        ));
    }
}
