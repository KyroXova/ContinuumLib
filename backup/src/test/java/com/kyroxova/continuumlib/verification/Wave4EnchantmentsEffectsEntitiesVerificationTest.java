package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import com.kyroxova.continuumlib.shims.EnchantmentShim;
import com.kyroxova.continuumlib.shims.FluidShim;
import com.kyroxova.continuumlib.shims.LivingEntityShim;
import com.kyroxova.continuumlib.shims.MobEffectShim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification Suite for Wave 4:
 * EnchantmentShim, MobEffectShim, LivingEntityShim, and FluidShim across 1.7.9 -> 26.3+.
 * Tests Holder<T> unwrapping, dynamic reflection invocations, legacy fallbacks, and rules catalog.
 */
public class Wave4EnchantmentsEffectsEntitiesVerificationTest {

    private ApiKnowledgeBase kb;

    @BeforeEach
    public void setup() {
        this.kb = ApiKnowledgeBase.createDefault();
    }

    // Helper mock holder class
    public static class MockHolder<T> {
        private final T val;
        public MockHolder(T val) {
            this.val = val;
        }
        public T value() {
            return val;
        }
    }

    // =========================================================================
    // Tier 1: Isolation Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 1: Isolation Tests")
    class Tier1Isolation {

        @Test
        @DisplayName("1.1 EnchantmentShim: Holder unwrapping and level queries")
        public void testEnchantmentShim() {
            Object rawEnchantment = "minecraft:sharpness";
            MockHolder<Object> holderEnchantment = new MockHolder<>(rawEnchantment);

            assertEquals(rawEnchantment, EnchantmentShim.unwrapHolder(holderEnchantment));
            assertEquals(rawEnchantment, EnchantmentShim.unwrapHolder(rawEnchantment));
            assertNull(EnchantmentShim.unwrapHolder(null));

            // Mock ItemStack with getEnchantmentLevel(Object)
            Object mockStack = new Object() {
                public int getEnchantmentLevel(Object enchantment) {
                    if ("minecraft:sharpness".equals(enchantment)) {
                        return 5;
                    }
                    return 0;
                }
            };

            // Query with raw enchantment
            assertEquals(5, EnchantmentShim.getEnchantmentLevel(rawEnchantment, mockStack));
            assertEquals(5, EnchantmentShim.getItemEnchantmentLevel(rawEnchantment, mockStack));
            assertTrue(EnchantmentShim.hasEnchantment(rawEnchantment, mockStack));

            // Query with Holder<Enchantment>
            assertEquals(5, EnchantmentShim.getEnchantmentLevel(holderEnchantment, mockStack));
            assertEquals(5, EnchantmentShim.getItemEnchantmentLevel(holderEnchantment, mockStack));
            assertTrue(EnchantmentShim.hasEnchantment(holderEnchantment, mockStack));

            // Query absent enchantment
            assertEquals(0, EnchantmentShim.getEnchantmentLevel("minecraft:unbreaking", mockStack));
            assertFalse(EnchantmentShim.hasEnchantment("minecraft:unbreaking", mockStack));

            // Null safety
            assertEquals(0, EnchantmentShim.getEnchantmentLevel(null, mockStack));
            assertEquals(0, EnchantmentShim.getEnchantmentLevel(rawEnchantment, null));
        }

        @Test
        @DisplayName("1.2 MobEffectShim: Modern & legacy effect query and manipulation")
        public void testMobEffectShim() {
            Object rawEffect = "minecraft:speed";
            MockHolder<Object> holderEffect = new MockHolder<>(rawEffect);

            // Modern entity mock
            Object modernEntity = new Object() {
                private Object active = "minecraft:speed";

                public boolean hasEffect(Object effect) {
                    return Objects.equals(active, effect);
                }

                public Object getEffect(Object effect) {
                    return Objects.equals(active, effect) ? "SpeedInstance(lvl=2)" : null;
                }

                public boolean addEffect(Object inst) {
                    this.active = inst;
                    return true;
                }

                public boolean removeEffect(Object effect) {
                    if (Objects.equals(active, effect)) {
                        active = null;
                        return true;
                    }
                    return false;
                }
            };

            // Holder unwrapping query
            assertTrue(MobEffectShim.hasEffect(modernEntity, holderEffect));
            assertTrue(MobEffectShim.hasEffect(modernEntity, rawEffect));
            assertNotNull(MobEffectShim.getEffect(modernEntity, holderEffect));
            assertNotNull(MobEffectShim.getEffect(modernEntity, rawEffect));

            // Removal via holder
            assertTrue(MobEffectShim.removeEffect(modernEntity, holderEffect));
            assertFalse(MobEffectShim.hasEffect(modernEntity, rawEffect));
            assertNull(MobEffectShim.getEffect(modernEntity, rawEffect));

            // Addition
            assertTrue(MobEffectShim.addEffect(modernEntity, "minecraft:speed"));
            assertTrue(MobEffectShim.hasEffect(modernEntity, rawEffect));

            // Legacy entity mock (isPotionActive / getActivePotionEffect / addPotionEffect / removePotionEffect)
            Object legacyEntity = new Object() {
                private Object potion = "minecraft:haste";

                public boolean isPotionActive(Object pot) {
                    return Objects.equals(potion, pot);
                }

                public Object getActivePotionEffect(Object pot) {
                    return Objects.equals(potion, pot) ? "HasteInstance(lvl=1)" : null;
                }

                public void addPotionEffect(Object pe) {
                    this.potion = pe;
                }

                public void removePotionEffect(Object pot) {
                    if (Objects.equals(potion, pot)) {
                        potion = null;
                    }
                }
            };

            MockHolder<Object> hasteHolder = new MockHolder<>("minecraft:haste");
            assertTrue(MobEffectShim.hasEffect(legacyEntity, hasteHolder));
            assertNotNull(MobEffectShim.getEffect(legacyEntity, hasteHolder));
            assertTrue(MobEffectShim.removeEffect(legacyEntity, hasteHolder));
            assertFalse(MobEffectShim.hasEffect(legacyEntity, hasteHolder));
            assertTrue(MobEffectShim.addEffect(legacyEntity, "minecraft:haste"));
            assertTrue(MobEffectShim.hasEffect(legacyEntity, "minecraft:haste"));

            // Null safety
            assertFalse(MobEffectShim.hasEffect(null, rawEffect));
            assertFalse(MobEffectShim.hasEffect(modernEntity, null));
            assertNull(MobEffectShim.getEffect(null, rawEffect));
            assertFalse(MobEffectShim.addEffect(null, "inst"));
            assertFalse(MobEffectShim.removeEffect(null, rawEffect));
        }

        @Test
        @DisplayName("1.3 LivingEntityShim: Attribute values and EquipmentSlot bridging")
        public void testLivingEntityShim() {
            Object rawAttribute = "minecraft:generic.attack_damage";
            MockHolder<Object> holderAttribute = new MockHolder<>(rawAttribute);

            // Modern living entity mock
            Object modernEntity = new Object() {
                private final Map<String, Object> slots = new HashMap<>();

                public double getAttributeValue(Object attr) {
                    if ("minecraft:generic.attack_damage".equals(attr)) return 9.5;
                    return 1.0;
                }

                public Object getItemBySlot(Object slot) {
                    return slots.get(String.valueOf(slot));
                }

                public void setItemSlot(Object slot, Object itemStack) {
                    slots.put(String.valueOf(slot), itemStack);
                }
            };

            assertEquals(9.5, LivingEntityShim.getAttributeValue(modernEntity, holderAttribute), 0.001);
            assertEquals(9.5, LivingEntityShim.getAttributeValue(modernEntity, rawAttribute), 0.001);
            assertEquals(0.0, LivingEntityShim.getAttributeValue(null, rawAttribute), 0.001);
            assertEquals(0.0, LivingEntityShim.getAttributeValue(modernEntity, null), 0.001);

            // Equipment slot testing
            LivingEntityShim.setItemSlot(modernEntity, "MAINHAND", "diamond_sword");
            assertEquals("diamond_sword", LivingEntityShim.getItemBySlot(modernEntity, "MAINHAND"));
            assertNull(LivingEntityShim.getItemBySlot(modernEntity, "OFFHAND"));

            // Legacy living entity mock with getAttribute(attr).getValue()
            Object legacyEntity = new Object() {
                public Object getAttribute(Object attr) {
                    if ("minecraft:generic.movement_speed".equals(attr)) {
                        return new Object() {
                            public double getValue() {
                                return 0.25;
                            }
                        };
                    }
                    return null;
                }

                private final Map<Integer, Object> eq = new HashMap<>();

                public Object getEquipmentInSlot(int slot) {
                    return eq.get(slot);
                }

                public void setCurrentItemOrArmor(int slot, Object stack) {
                    eq.put(slot, stack);
                }
            };

            MockHolder<Object> speedHolder = new MockHolder<>("minecraft:generic.movement_speed");
            assertEquals(0.25, LivingEntityShim.getAttributeValue(legacyEntity, speedHolder), 0.001);

            // Legacy slot mapping (MAINHAND = 0, FEET = 1, LEGS = 2, CHEST = 3, HEAD = 4, OFFHAND = 5)
            enum TestSlot { MAINHAND, FEET, LEGS, CHEST, HEAD, OFFHAND }
            LivingEntityShim.setItemSlot(legacyEntity, TestSlot.HEAD, "netherite_helmet");
            assertEquals("netherite_helmet", LivingEntityShim.getItemBySlot(legacyEntity, TestSlot.HEAD));
        }

        @Test
        @DisplayName("1.4 FluidShim: Fluid properties, FluidState, and source block resolution")
        public void testFluidShim() {
            // Fluid attributes creation test (safe fallback)
            Object props = FluidShim.createFluidAttributes("still", "flowing");
            // Depending on runtime classpath, could be null or FluidType.Properties
            // The method must not throw an exception.

            // Custom fluid mock
            Object customFluid = new Object() {
                public int getDensity() { return 2500; }
                public int getViscosity() { return 3000; }
                public int getTemperature() { return 1300; }
                public boolean isGaseous() { return true; }
            };

            assertEquals(2500, FluidShim.getDensity(customFluid));
            assertEquals(3000, FluidShim.getViscosity(customFluid));
            assertEquals(1300, FluidShim.getTemperature(customFluid));
            assertTrue(FluidShim.isGaseous(customFluid));

            // Defaults on standard / null object
            assertEquals(1000, FluidShim.getDensity(null));
            assertEquals(1000, FluidShim.getViscosity(null));
            assertEquals(300, FluidShim.getTemperature(null));
            assertFalse(FluidShim.isGaseous(null));

            // FluidState queries
            Object mockLevel = new Object() {
                public Object getFluidState(Object pos) {
                    if ("water_pos".equals(pos)) {
                        return new Object() {
                            public boolean isSource() { return true; }
                        };
                    } else if ("flowing_pos".equals(pos)) {
                        return new Object() {
                            public int getAmount() { return 4; }
                        };
                    } else if ("full_pos".equals(pos)) {
                        return new Object() {
                            public int getAmount() { return 8; }
                        };
                    }
                    return null;
                }
            };

            Object waterState = FluidShim.getFluidState(mockLevel, "water_pos");
            assertNotNull(waterState);
            assertTrue(FluidShim.isSource(waterState));

            Object flowingState = FluidShim.getFluidState(mockLevel, "flowing_pos");
            assertNotNull(flowingState);
            assertFalse(FluidShim.isSource(flowingState));

            Object fullState = FluidShim.getFluidState(mockLevel, "full_pos");
            assertNotNull(fullState);
            assertTrue(FluidShim.isSource(fullState));

            assertNull(FluidShim.getFluidState(null, "water_pos"));
            assertNull(FluidShim.getFluidState(mockLevel, null));
            assertFalse(FluidShim.isSource(null));
        }
    }

    // =========================================================================
    // Tier 2: Rules Catalog & Transformation Delta Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 2: Rules Catalog & Transformation Delta Tests")
    class Tier2RulesCatalog {

        @Test
        @DisplayName("2.1 Enchantment and loot rules registration")
        public void testEnchantmentAndLootRules() {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target = TargetSpec.of("1.21.0", "neoforge");

            List<TransformationRule> rules = kb.getApplicableRules(base, target);

            boolean hasLootParamsRedirect = rules.stream().anyMatch(r ->
                    r instanceof ClassRedirectRule cr &&
                    "net/minecraft/world/level/storage/loot/LootContext$Builder".equals(cr.getSourceInternalName()) &&
                    "net/minecraft/world/level/storage/loot/LootParams$Builder".equals(cr.getTargetInternalName())
            );
            assertTrue(hasLootParamsRedirect, "LootContext.Builder must redirect to LootParams.Builder on 1.20+");

            boolean hasGetItemEnchantmentLevel = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/world/item/enchantment/EnchantmentHelper".equals(pr.getSourceOwner()) &&
                    "getItemEnchantmentLevel".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/EnchantmentShim".equals(pr.getShimOwner())
            );
            assertTrue(hasGetItemEnchantmentLevel, "EnchantmentHelper.getItemEnchantmentLevel must polyfill to EnchantmentShim on 1.21+");

            boolean hasGetEnchantmentLevel = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/world/item/enchantment/EnchantmentHelper".equals(pr.getSourceOwner()) &&
                    "getEnchantmentLevel".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/EnchantmentShim".equals(pr.getShimOwner())
            );
            assertTrue(hasGetEnchantmentLevel, "EnchantmentHelper.getEnchantmentLevel must polyfill to EnchantmentShim");

            boolean hasEnchantment = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/world/item/enchantment/EnchantmentHelper".equals(pr.getSourceOwner()) &&
                    "hasEnchantment".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/EnchantmentShim".equals(pr.getShimOwner())
            );
            assertTrue(hasEnchantment, "EnchantmentHelper.hasEnchantment must polyfill to EnchantmentShim");
        }

        @Test
        @DisplayName("2.2 Fluid and attribute rules registration")
        public void testFluidAndAttributeRules() {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target = TargetSpec.of("1.20.4", "neoforge");

            List<TransformationRule> rules = kb.getApplicableRules(base, target);

            boolean hasFluidBuilder = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraftforge/fluids/FluidAttributes".equals(pr.getSourceOwner()) &&
                    "builder".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/FluidShim".equals(pr.getShimOwner())
            );
            assertTrue(hasFluidBuilder, "FluidAttributes.builder must polyfill to FluidShim on 1.19.2+");

            boolean hasGetDensity = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraftforge/fluids/FluidAttributes".equals(pr.getSourceOwner()) &&
                    "getDensity".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/FluidShim".equals(pr.getShimOwner())
            );
            assertTrue(hasGetDensity, "FluidAttributes.getDensity must polyfill to FluidShim");

            boolean hasFluidTypeRedirect = rules.stream().anyMatch(r ->
                    r instanceof ClassRedirectRule cr &&
                    "net/minecraftforge/fluids/FluidType".equals(cr.getSourceInternalName()) &&
                    "net/neoforged/neoforge/fluids/FluidType".equals(cr.getTargetInternalName())
            );
            assertTrue(hasFluidTypeRedirect, "FluidType must redirect to NeoForge FluidType on 1.20.4+");

            boolean hasGetFluidState = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/world/level/Level".equals(pr.getSourceOwner()) &&
                    "getFluidState".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/FluidShim".equals(pr.getShimOwner())
            );
            assertTrue(hasGetFluidState, "Level.getFluidState must polyfill to FluidShim");

            boolean hasIsSource = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/world/level/material/FluidState".equals(pr.getSourceOwner()) &&
                    "isSource".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/FluidShim".equals(pr.getShimOwner())
            );
            assertTrue(hasIsSource, "FluidState.isSource must polyfill to FluidShim");
        }

        @Test
        @DisplayName("2.3 LivingEntity and MobEffect rules in BlockAndEntity & DamageAndCombat catalogs")
        public void testEntityAndEffectRules() {
            TargetSpec base = TargetSpec.of("1.20.4", "forge");
            TargetSpec target = TargetSpec.of("26.3", "neoforge");

            List<TransformationRule> rules = kb.getApplicableRules(base, target);

            boolean hasGetAttributeValue = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/world/entity/LivingEntity".equals(pr.getSourceOwner()) &&
                    "getAttributeValue".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/LivingEntityShim".equals(pr.getShimOwner())
            );
            assertTrue(hasGetAttributeValue, "LivingEntity.getAttributeValue must polyfill to LivingEntityShim");

            boolean hasGetItemBySlot = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/world/entity/LivingEntity".equals(pr.getSourceOwner()) &&
                    "getItemBySlot".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/LivingEntityShim".equals(pr.getShimOwner())
            );
            assertTrue(hasGetItemBySlot, "LivingEntity.getItemBySlot must polyfill to LivingEntityShim");

            boolean hasHasEffect = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/world/entity/LivingEntity".equals(pr.getSourceOwner()) &&
                    "hasEffect".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/MobEffectShim".equals(pr.getShimOwner())
            );
            assertTrue(hasHasEffect, "LivingEntity.hasEffect must polyfill to MobEffectShim");

            boolean hasGetEffect = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/world/entity/LivingEntity".equals(pr.getSourceOwner()) &&
                    "getEffect".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/MobEffectShim".equals(pr.getShimOwner())
            );
            assertTrue(hasGetEffect, "LivingEntity.getEffect must polyfill to MobEffectShim");
        }
    }

    // =========================================================================
    // Tier 3: Concurrency & Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 3: Concurrency & Stress Tests")
    class Tier3Concurrency {

        @Test
        @DisplayName("3.1 Highly concurrent queries across Enchantment, MobEffect, Entity, and Fluid shims")
        public void testConcurrentShimQueries() throws InterruptedException {
            int threadCount = 16;
            int opsPerThread = 200;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger verifiedOps = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < opsPerThread; i++) {
                            // 1. Enchantment query
                            String enchName = "ench_" + threadId + "_" + i;
                            MockHolder<String> enchHolder = new MockHolder<>(enchName);
                            Object mockItem = new Object() {
                                public int getEnchantmentLevel(Object e) {
                                    return Objects.equals(e, enchName) ? 3 : 0;
                                }
                            };
                            int lvl = EnchantmentShim.getEnchantmentLevel(enchHolder, mockItem);
                            if (lvl == 3 && EnchantmentShim.hasEnchantment(enchHolder, mockItem)) {
                                verifiedOps.incrementAndGet();
                            }

                            // 2. MobEffect query
                            String effectName = "effect_" + threadId + "_" + i;
                            MockHolder<String> effectHolder = new MockHolder<>(effectName);
                            Object mockLiving = new Object() {
                                public boolean hasEffect(Object eff) {
                                    return Objects.equals(eff, effectName);
                                }
                                public Object getEffect(Object eff) {
                                    return Objects.equals(eff, effectName) ? "inst" : null;
                                }
                            };
                            if (MobEffectShim.hasEffect(mockLiving, effectHolder) && MobEffectShim.getEffect(mockLiving, effectHolder) != null) {
                                verifiedOps.incrementAndGet();
                            }

                            // 3. LivingEntity query
                            Object mockEntity = new Object() {
                                public double getAttributeValue(Object attr) { return 10.0; }
                                public Object getItemBySlot(Object slot) { return "item"; }
                            };
                            if (LivingEntityShim.getAttributeValue(mockEntity, "attr") == 10.0 && "item".equals(LivingEntityShim.getItemBySlot(mockEntity, "MAINHAND"))) {
                                verifiedOps.incrementAndGet();
                            }

                            // 4. Fluid query
                            Object mockState = new Object() {
                                public boolean isSource() { return true; }
                            };
                            if (FluidShim.isSource(mockState) && FluidShim.getDensity(null) == 1000) {
                                verifiedOps.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent shim queries timed out");
            executor.shutdown();

            assertEquals(threadCount * opsPerThread * 4, verifiedOps.get());
        }
    }
}
