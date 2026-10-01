package com.kyroxova.continuumlib.knowledgebase;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.catalogs.*;
import com.kyroxova.continuumlib.knowledgebase.rules.*;

import com.kyroxova.continuumlib.knowledgebase.mappings.MappingFormat;
import com.kyroxova.continuumlib.knowledgebase.mappings.MappingTranslationTable;

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
    private final MappingTranslationTable mappingTranslationTable;

    public ApiKnowledgeBase() {
        this(MappingTranslationTable.createDefault());
    }

    public ApiKnowledgeBase(MappingTranslationTable mappingTranslationTable) {
        this.mappingTranslationTable = mappingTranslationTable != null ? mappingTranslationTable : MappingTranslationTable.createDefault();
    }

    public MappingTranslationTable getMappingTranslationTable() {
        return mappingTranslationTable;
    }

    public String translateClass(String className, MappingFormat from, MappingFormat to) {
        return mappingTranslationTable.translateClass(className, from, to);
    }

    public String translateMethod(String owner, String methodName, String methodDesc, MappingFormat from, MappingFormat to) {
        return mappingTranslationTable.translateMethod(owner, methodName, methodDesc, from, to);
    }

    public String translateField(String owner, String fieldName, String fieldDesc, MappingFormat from, MappingFormat to) {
        return mappingTranslationTable.translateField(owner, fieldName, fieldDesc, from, to);
    }

    public String translateDescriptor(String descriptor, MappingFormat from, MappingFormat to) {
        return mappingTranslationTable.translateDescriptor(descriptor, from, to);
    }

    public static ApiKnowledgeBase createDefault() {
        ApiKnowledgeBase kb = new ApiKnowledgeBase();
        kb.registerDefaultRules();
        return kb;
    }

    public com.kyroxova.continuumlib.knowledgebase.profile.TargetProfile getTargetProfile(TargetSpec targetSpec) {
        return com.kyroxova.continuumlib.knowledgebase.profile.TargetProfile.forSpec(targetSpec);
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

    public List<TransformationRule> getActiveRules(TargetSpec baseSpec, TargetSpec targetSpec) {
        return getApplicableRules(baseSpec, targetSpec);
    }

    private void registerDefaultRules() {
        RegistryRulesCatalog.register(this);
        PreFlatteningCatalog.register(this);
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
        SoundAndMusicRulesCatalog.register(this);
        DataPackPathRulesCatalog.register(this);
        CommandAndMenuRulesCatalog.register(this);
        WorldAndDimensionRulesCatalog.register(this);
        ParticleRulesCatalog.register(this);
        VoxelShapeRulesCatalog.register(this);
        ColorHandlerRulesCatalog.register(this);
        AdvancementAndCriteriaRulesCatalog.register(this);
        ExplosionAndPhysicsRulesCatalog.register(this);
    }
}
