package com.kyroxova.continuumlib.knowledgebase;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ApiKnowledgeBaseTest {

    @Test
    public void testContemporaryNeoForgeRules() {
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec targetNeo = TargetSpec.of("1.20.4", "neoforge");

        List<TransformationRule> rules = kb.getApplicableRules(base, targetNeo);
        assertFalse(rules.isEmpty());

        boolean hasRegistryObjectRedirect = rules.stream().anyMatch(r ->
                r instanceof ClassRedirectRule cr &&
                cr.getSourceInternalName().equals("net/minecraftforge/registries/RegistryObject") &&
                cr.getTargetInternalName().equals("net/neoforged/neoforge/registries/DeferredHolder")
        );
        assertTrue(hasRegistryObjectRedirect, "Expected RegistryObject -> DeferredHolder rule");

        boolean hasMaterialPolyfill = rules.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                pr.getSourceOwner().equals("net/minecraft/world/level/block/state/BlockBehaviour$Properties") &&
                pr.getSourceName().equals("of")
        );
        assertTrue(hasMaterialPolyfill, "Expected Material removal polyfill rule");
    }

    @Test
    public void testFabricRules() {
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec targetFabric = TargetSpec.of("1.20.1", "fabric");

        List<TransformationRule> rules = kb.getApplicableRules(base, targetFabric);
        boolean hasFabricRegistryShim = rules.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                pr.getShimOwner().equals("com/kyroxova/continuumlib/shims/RegistryShim")
        );
        assertTrue(hasFabricRegistryShim, "Expected Fabric RegistryShim rule");
    }
}
