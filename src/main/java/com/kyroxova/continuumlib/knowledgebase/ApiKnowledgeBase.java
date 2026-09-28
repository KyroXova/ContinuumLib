package com.kyroxova.continuumlib.knowledgebase;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.catalogs.*;
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
        RegistryRulesCatalog.register(this);
        BlockAndEntityRulesCatalog.register(this);
        ItemAndComponentRulesCatalog.register(this);
        ScreenAndUIRulesCatalog.register(this);
        TextAndChatRulesCatalog.register(this);
        NetworkRulesCatalog.register(this);
        LifecycleAndEventRulesCatalog.register(this);
        ReflectionAndDistRulesCatalog.register(this);
        ClientRenderingAndGuiCatalog.register(this);
        MathAndVectorsCatalog.register(this);
        CapabilityAndStorageCatalog.register(this);
        DamageAndCombatRulesCatalog.register(this);
        FluidAndAttributesRulesCatalog.register(this);
        EnchantmentAndLootRulesCatalog.register(this);
        TagAndResourceRulesCatalog.register(this);
    }
}
