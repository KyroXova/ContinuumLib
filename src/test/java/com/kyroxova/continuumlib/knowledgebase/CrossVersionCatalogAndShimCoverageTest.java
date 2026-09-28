package com.kyroxova.continuumlib.knowledgebase;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.MethodRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import com.kyroxova.continuumlib.shims.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class CrossVersionCatalogAndShimCoverageTest {

    @Test
    @DisplayName("1. TagAndResourceRulesCatalog: ResourceLocation 1.21+ constructor polyfills")
    public void testTagAndResourceCatalogResourceLocation() {
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec target121 = TargetSpec.of("1.21.1", "neoforge");

        List<TransformationRule> rules = kb.getApplicableRules(base, target121);

        // Verify ResourceLocation.<init>(String, String) -> fromNamespaceAndPath
        boolean hasFromNamespaceAndPath = rules.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                "net/minecraft/resources/ResourceLocation".equals(pr.getSourceOwner()) &&
                "<init>".equals(pr.getSourceName()) &&
                "(Ljava/lang/String;Ljava/lang/String;)V".equals(pr.getSourceDesc()) &&
                "fromNamespaceAndPath".equals(pr.getShimName())
        );
        assertTrue(hasFromNamespaceAndPath, "ResourceLocation(String, String) constructor polyfill must be registered for 1.21+");

        // Verify ResourceLocation.<init>(String) -> parse
        boolean hasParse = rules.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                "net/minecraft/resources/ResourceLocation".equals(pr.getSourceOwner()) &&
                "<init>".equals(pr.getSourceName()) &&
                "(Ljava/lang/String;)V".equals(pr.getSourceDesc()) &&
                "parse".equals(pr.getShimName())
        );
        assertTrue(hasParse, "ResourceLocation(String) constructor polyfill must be registered for 1.21+");

        // Verify Fluid and EntityType tags
        boolean hasFluidTag = rules.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                "net/minecraft/tags/FluidTags".equals(pr.getSourceOwner())
        );
        assertTrue(hasFluidTag, "FluidTags rule must be registered");

        boolean hasEntityTypeTag = rules.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                "net/minecraft/tags/EntityTypeTags".equals(pr.getSourceOwner())
        );
        assertTrue(hasEntityTypeTag, "EntityTypeTags rule must be registered");
    }

    @Test
    @DisplayName("2. DamageAndCombatRulesCatalog: GENERIC, MAGIC, FALL, OUT_OF_WORLD field rules")
    public void testDamageAndCombatRulesCatalog() {
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec target120 = TargetSpec.of("1.20.1", "forge");

        List<TransformationRule> rules = kb.getApplicableRules(base, target120);

        String[] requiredFields = {"GENERIC", "MAGIC", "FALL", "OUT_OF_WORLD", "IN_FIRE", "ON_FIRE", "LAVA", "DROWN", "STARVE", "WITHER"};
        for (String field : requiredFields) {
            boolean hasFieldRule = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/world/damagesource/DamageSource".equals(pr.getSourceOwner()) &&
                    field.equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/DamageSourceShim".equals(pr.getShimOwner())
            );
            assertTrue(hasFieldRule, "DamageSource." + field + " rule must be registered with PolyfillRule");
        }

        // DamageSourceShim methods should execute gracefully without uncaught exceptions
        assertDoesNotThrow(DamageSourceShim::generic);
        assertDoesNotThrow(DamageSourceShim::magic);
        assertDoesNotThrow(DamageSourceShim::fall);
        assertDoesNotThrow(DamageSourceShim::outOfWorld);
        assertDoesNotThrow(DamageSourceShim::inFire);
        assertDoesNotThrow(DamageSourceShim::onFire);
        assertDoesNotThrow(DamageSourceShim::lava);
        assertDoesNotThrow(DamageSourceShim::drown);
        assertDoesNotThrow(DamageSourceShim::starve);
        assertDoesNotThrow(DamageSourceShim::wither);
        assertDoesNotThrow(() -> DamageSourceShim.playerAttack(null));
    }

    @Test
    @DisplayName("3. RecipeShim: adaptRecipeOutput / wrapOutput cleanly adapts RecipeOutput")
    public void testRecipeShimAdaptRecipeOutput() {
        // Test with null
        Consumer<Object> nullConsumer = RecipeShim.adaptRecipeOutput(null);
        assertNotNull(nullConsumer);
        assertDoesNotThrow(() -> nullConsumer.accept("test"));

        // Test with already a Consumer
        AtomicBoolean consumed = new AtomicBoolean(false);
        Consumer<Object> rawConsumer = obj -> consumed.set(true);
        Consumer<Object> wrappedConsumer = RecipeShim.adaptRecipeOutput(rawConsumer);
        wrappedConsumer.accept("hello");
        assertTrue(consumed.get());

        // Test with mock RecipeOutput that has 3-argument accept method
        List<Object> recorded = new ArrayList<>();
        Object mockRecipeOutput3 = new Object() {
            public void accept(Object id, Object recipe, Object advancement) {
                recorded.add(id);
                recorded.add(recipe);
            }
        };

        Consumer<Object> adapted3 = RecipeShim.adaptRecipeOutput(mockRecipeOutput3);
        assertNotNull(adapted3);

        // Dummy finished recipe with getId()
        Object mockFinishedRecipe = new Object() {
            public Object getId() {
                return "mymod:my_recipe";
            }
        };

        adapted3.accept(mockFinishedRecipe);
        assertEquals(2, recorded.size());
        assertEquals("mymod:my_recipe", recorded.get(0));
        assertSame(mockFinishedRecipe, recorded.get(1));

        // Test with mock RecipeOutput that has 2-argument accept method
        List<Object> recorded2 = new ArrayList<>();
        Object mockRecipeOutput2 = new Object() {
            public void accept(Object id, Object recipe) {
                recorded2.add(id);
                recorded2.add(recipe);
            }
        };

        Consumer<Object> adapted2 = RecipeShim.wrapOutput(mockRecipeOutput2);
        adapted2.accept(mockFinishedRecipe);
        assertEquals(2, recorded2.size());
        assertEquals("mymod:my_recipe", recorded2.get(0));
    }

    @Test
    @DisplayName("4. Client Rendering (GuiGraphics vs PoseStack), Entity Access, Sound, and Registry Delegates")
    public void testCatalogsAndShimsExtensions() {
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec target120 = TargetSpec.of("1.20.1", "neoforge");

        List<TransformationRule> rules120 = kb.getApplicableRules(base, target120);

        // 1. Client Rendering rules in ScreenAndUIRulesCatalog and ClientRenderingAndGuiCatalog
        boolean hasScreenRenderBackground = rules120.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                "net/minecraft/client/gui/screens/Screen".equals(pr.getSourceOwner()) &&
                "renderBackground".equals(pr.getSourceName())
        );
        assertTrue(hasScreenRenderBackground, "Screen.renderBackground polyfill must be registered");

        boolean hasScreenRenderTooltip = rules120.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                "net/minecraft/client/gui/screens/Screen".equals(pr.getSourceOwner()) &&
                "renderTooltip".equals(pr.getSourceName())
        );
        assertTrue(hasScreenRenderTooltip, "Screen.renderTooltip polyfill must be registered");

        boolean hasItemRenderer = rules120.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                "net/minecraft/client/renderer/entity/ItemRenderer".equals(pr.getSourceOwner()) &&
                "renderGuiItem".equals(pr.getSourceName())
        );
        assertTrue(hasItemRenderer, "ItemRenderer.renderGuiItem polyfill must be registered");

        // 2. SoundEvent rules in SoundAndMusicRulesCatalog
        boolean hasSoundEventInit = rules120.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                "net/minecraft/sounds/SoundEvent".equals(pr.getSourceOwner()) &&
                "<init>".equals(pr.getSourceName())
        );
        assertTrue(hasSoundEventInit, "SoundEvent.<init> polyfill must be registered for modern targets");

        assertDoesNotThrow(() -> SoundEventShim.create("minecraft:entity.experience_orb.pickup"));
        assertDoesNotThrow(() -> SoundEventShim.createVariableRangeEvent("minecraft:entity.experience_orb.pickup"));
        assertDoesNotThrow(() -> SoundEventShim.createFixedRangeEvent("minecraft:entity.experience_orb.pickup", 16.0f));

        // 3. Entity Access rules in BlockAndEntityRulesCatalog
        boolean hasEntityGetLevel = rules120.stream().anyMatch(r ->
                r instanceof MethodRedirectRule mr &&
                "net/minecraft/world/entity/Entity".equals(mr.getSourceOwner()) &&
                "getLevel".equals(mr.getSourceName()) &&
                "level".equals(mr.getTargetName())
        );
        assertTrue(hasEntityGetLevel, "Entity.getLevel() -> level() rule must be registered for 1.20+");

        boolean hasEntityOnGround = rules120.stream().anyMatch(r ->
                r instanceof MethodRedirectRule mr &&
                "net/minecraft/world/entity/Entity".equals(mr.getSourceOwner()) &&
                "isOnGround".equals(mr.getSourceName()) &&
                "onGround".equals(mr.getTargetName())
        );
        assertTrue(hasEntityOnGround, "Entity.isOnGround() -> onGround() rule must be registered for 1.20+");

        // 4. Registry Delegates
        boolean hasRegistryDelegateRedirect = rules120.stream().anyMatch(r ->
                r instanceof ClassRedirectRule cr &&
                "net/minecraftforge/registries/RegistryDelegate".equals(cr.getSourceInternalName())
        );
        assertTrue(hasRegistryDelegateRedirect, "RegistryDelegate class redirect must be registered");

        // RegistryShim holder operations
        Supplier<String> testSupplier = () -> "RegisteredBlock";
        assertEquals("RegisteredBlock", RegistryShim.getValue(testSupplier));
        assertEquals("RegisteredBlock", RegistryShim.resolveHolder(testSupplier));
        assertTrue(RegistryShim.isPresent(testSupplier));
    }
}
