package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.MethodRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import com.kyroxova.continuumlib.shims.ItemStackShim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Verification Suite for Subsystem d:
 * Item Data Components vs NBT Bidirectional Routing (1.7.9 -> 26.3+).
 */
public class ItemComponentsAndNbtVerificationTest {

    private ApiKnowledgeBase kb;

    @BeforeEach
    public void setup() {
        this.kb = ApiKnowledgeBase.createDefault();
    }

    // =========================================================================
    // Tier 1: Isolation Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 1: Isolation Tests")
    class Tier1Isolation {

        @Test
        @DisplayName("1.1 Legacy ItemStack NBT operations: getTag, setTag, hasTag, removeTagKey")
        public void testLegacyItemStackNbtOperations() {
            MockLegacyItemStack stack = new MockLegacyItemStack();
            assertFalse(ItemStackShim.hasTag(stack));
            assertNull(ItemStackShim.getTag(stack));

            // getOrCreateTag
            Object tag = ItemStackShim.getOrCreateTag(stack);
            assertNotNull(tag);
            assertTrue(ItemStackShim.hasTag(stack));

            // setTag
            MockCompoundTag newTag = new MockCompoundTag();
            newTag.putString("Key1", "Value1");
            newTag.putString("Key2", "Value2");
            ItemStackShim.setTag(stack, newTag);

            assertSame(newTag, ItemStackShim.getTag(stack));

            // removeTagKey
            ItemStackShim.removeTagKey(stack, "Key1");
            assertFalse(newTag.containsKey("Key1"));
            assertTrue(newTag.containsKey("Key2"));
        }

        @Test
        @DisplayName("1.2 Modern component get on legacy ItemStack: damage, custom_name, lore, arbitrary component")
        public void testModernGetOnLegacyItemStack() {
            MockLegacyItemStack stack = new MockLegacyItemStack();
            MockCompoundTag rootTag = (MockCompoundTag) ItemStackShim.getOrCreateTag(stack);

            // 1. Damage
            rootTag.putInt("Damage", 42);
            assertEquals(42, ItemStackShim.get(stack, "damage"));
            assertEquals(42, ItemStackShim.get(stack, "minecraft:damage"));

            // 2. Custom name
            MockCompoundTag displayTag = new MockCompoundTag();
            displayTag.putString("Name", "Excalibur");
            rootTag.put("display", displayTag);
            assertEquals("Excalibur", ItemStackShim.get(stack, "custom_name"));

            // 3. Lore
            List<String> loreList = Arrays.asList("Forged in fire", "Sharpness V");
            displayTag.put("Lore", loreList);
            assertEquals(loreList, ItemStackShim.get(stack, "lore"));

            // 4. Custom data
            Object customData = ItemStackShim.get(stack, "minecraft:custom_data");
            assertNotNull(customData);

            // 5. Arbitrary component stored in ContinuumComponents
            MockCompoundTag continuumComps = new MockCompoundTag();
            continuumComps.putString("mymod:mana_cost", "50");
            rootTag.put("ContinuumComponents", continuumComps);
            assertEquals("50", ItemStackShim.get(stack, "mymod:mana_cost"));
        }

        @Test
        @DisplayName("1.3 Modern component set on legacy ItemStack: damage, custom_name, arbitrary components")
        public void testModernSetOnLegacyItemStack() {
            MockLegacyItemStack stack = new MockLegacyItemStack();

            // Set damage
            ItemStackShim.set(stack, "damage", 100);
            assertEquals(100, ItemStackShim.get(stack, "damage"));

            // Set custom name
            ItemStackShim.set(stack, "custom_name", "Mjollnir");
            assertEquals("Mjollnir", ItemStackShim.get(stack, "custom_name"));

            // Set custom arbitrary component
            ItemStackShim.set(stack, "mymod:durability_multiplier", "2.5");
            assertEquals("2.5", ItemStackShim.get(stack, "mymod:durability_multiplier"));
            assertTrue(ItemStackShim.has(stack, "mymod:durability_multiplier"));

            // Remove component
            Object removed = ItemStackShim.remove(stack, "mymod:durability_multiplier");
            assertEquals("2.5", removed);
            assertNull(ItemStackShim.get(stack, "mymod:durability_multiplier"));
            assertFalse(ItemStackShim.has(stack, "mymod:durability_multiplier"));
        }

        @Test
        @DisplayName("1.4 Modern ItemStack routing get/set/has/remove via native modern methods")
        public void testModernItemStackDirectRouting() {
            MockModernItemStack modernStack = new MockModernItemStack();

            ItemStackShim.set(modernStack, "minecraft:attribute_modifiers", "StrengthModifier");
            assertTrue(ItemStackShim.has(modernStack, "minecraft:attribute_modifiers"));
            assertEquals("StrengthModifier", ItemStackShim.get(modernStack, "minecraft:attribute_modifiers"));

            Object removed = ItemStackShim.remove(modernStack, "minecraft:attribute_modifiers");
            assertEquals("StrengthModifier", removed);
            assertFalse(ItemStackShim.has(modernStack, "minecraft:attribute_modifiers"));
        }
    }

    // =========================================================================
    // Tier 2: Boundary & Edge Case Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 2: Boundary & Edge Cases")
    class Tier2Boundary {

        @Test
        @DisplayName("2.1 Null parameters across ItemStackShim APIs")
        public void testNullParameters() {
            assertNull(ItemStackShim.getTag(null));
            assertNull(ItemStackShim.getOrCreateTag(null));
            assertDoesNotThrow(() -> ItemStackShim.setTag(null, null));
            assertFalse(ItemStackShim.hasTag(null));
            assertDoesNotThrow(() -> ItemStackShim.removeTagKey(null, null));

            assertNull(ItemStackShim.get(null, null));
            assertNull(ItemStackShim.get(new MockLegacyItemStack(), null));
            assertNull(ItemStackShim.set(null, null, null));
            assertFalse(ItemStackShim.has(null, null));
            assertNull(ItemStackShim.remove(null, null));
        }

        @Test
        @DisplayName("2.2 Component key resolution from custom ComponentType objects")
        public void testComponentKeyResolution() {
            MockLegacyItemStack stack = new MockLegacyItemStack();

            Object customComponentType = new Object() {
                public Object getKey() {
                    return "mymod:custom_ability";
                }
                @Override
                public String toString() {
                    return "CustomAbilityType";
                }
            };

            ItemStackShim.set(stack, customComponentType, "Thunderstrike");
            assertEquals("Thunderstrike", ItemStackShim.get(stack, customComponentType));
            assertTrue(ItemStackShim.has(stack, customComponentType));
        }

        @Test
        @DisplayName("2.3 Missing sub-compounds return null cleanly without NullPointerException")
        public void testMissingSubCompounds() {
            MockLegacyItemStack stack = new MockLegacyItemStack();
            // No tag initialized
            assertNull(ItemStackShim.get(stack, "damage"));
            assertNull(ItemStackShim.get(stack, "custom_name"));
            assertNull(ItemStackShim.get(stack, "lore"));
            assertNull(ItemStackShim.get(stack, "non_existent_key"));
            assertFalse(ItemStackShim.has(stack, "damage"));
        }
    }

    // =========================================================================
    // Tier 3: Cross-Feature & Catalog Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 3: Cross-Feature & Catalog Tests")
    class Tier3CrossFeature {

        @Test
        @DisplayName("3.1 ItemAndComponentRulesCatalog contains getTag, setTag, and component rules")
        public void testItemAndComponentRulesCatalog() {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target1205 = TargetSpec.of("1.20.6", "neoforge");

            List<TransformationRule> rules = kb.getApplicableRules(base, target1205);

            // getTag redirect
            boolean hasGetTagRule = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/world/item/ItemStack".equals(pr.getSourceOwner()) &&
                    "getTag".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/ItemStackShim".equals(pr.getShimOwner())
            );
            assertTrue(hasGetTagRule, "ItemStack.getTag polyfill must be registered for 1.20.5+ targets");

            // setTag redirect
            boolean hasSetTagRule = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/world/item/ItemStack".equals(pr.getSourceOwner()) &&
                    "setTag".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/ItemStackShim".equals(pr.getShimOwner())
            );
            assertTrue(hasSetTagRule, "ItemStack.setTag polyfill must be registered for 1.20.5+ targets");

            // hasTag redirect
            boolean hasHasTagRule = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/world/item/ItemStack".equals(pr.getSourceOwner()) &&
                    "hasTag".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/ItemStackShim".equals(pr.getShimOwner())
            );
            assertTrue(hasHasTagRule, "ItemStack.hasTag polyfill must be registered for 1.20.5+ targets");
        }

        @Test
        @DisplayName("3.2 Legacy code interacting with modern item stack and vice versa without NoSuchMethodError")
        public void testBidirectionalItemStackInteroperability() {
            // Legacy stack -> accessed via modern API
            MockLegacyItemStack legacyStack = new MockLegacyItemStack();
            ItemStackShim.set(legacyStack, "custom_name", "Shadowblade");
            ItemStackShim.set(legacyStack, "damage", 15);

            assertEquals("Shadowblade", ItemStackShim.get(legacyStack, "custom_name"));
            assertEquals(15, ItemStackShim.get(legacyStack, "damage"));

            // Modern stack -> accessed via modern API
            MockModernItemStack modernStack = new MockModernItemStack();
            ItemStackShim.set(modernStack, "custom_name", "Sunblade");
            assertEquals("Sunblade", ItemStackShim.get(modernStack, "custom_name"));
        }
    }

    // =========================================================================
    // Tier 4: Workload & Concurrency Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 4: Workload & Concurrency Tests")
    class Tier4Workload {

        @Test
        @DisplayName("4.1 Concurrent reads and writes of diverse components across multiple ItemStacks")
        public void testConcurrentItemStackOperations() throws InterruptedException, ExecutionException {
            int threadCount = 12;
            int opsPerThread = 200;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            List<Future<Void>> futures = new ArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                futures.add(pool.submit(() -> {
                    MockLegacyItemStack stack = new MockLegacyItemStack();
                    for (int i = 0; i < opsPerThread; i++) {
                        String key = "comp_" + threadId + "_" + (i % 8);
                        ItemStackShim.set(stack, key, "val_" + i);
                        assertEquals("val_" + i, ItemStackShim.get(stack, key));
                        assertTrue(ItemStackShim.has(stack, key));

                        // Damage updates
                        ItemStackShim.set(stack, "damage", i);
                        assertEquals(i, ItemStackShim.get(stack, "damage"));
                    }
                    return null;
                }));
            }

            for (Future<Void> f : futures) {
                f.get();
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    // =========================================================================
    // Test Mocks
    // =========================================================================

    public static class MockLegacyItemStack {
        private MockCompoundTag tag;

        public MockCompoundTag getTag() {
            return tag;
        }

        public MockCompoundTag getOrCreateTag() {
            if (tag == null) {
                tag = new MockCompoundTag();
            }
            return tag;
        }

        public void setTag(MockCompoundTag tag) {
            this.tag = tag;
        }

        public boolean hasTag() {
            return tag != null;
        }

        public void removeTagKey(String key) {
            if (tag != null) {
                tag.remove(key);
            }
        }
    }

    public static class MockModernItemStack {
        private final Map<String, Object> components = new ConcurrentHashMap<>();

        public Object get(Object componentType) {
            return components.get(String.valueOf(componentType));
        }

        public Object set(Object componentType, Object value) {
            return components.put(String.valueOf(componentType), value);
        }

        public boolean has(Object componentType) {
            return components.containsKey(String.valueOf(componentType));
        }

        public Object remove(Object componentType) {
            return components.remove(String.valueOf(componentType));
        }
    }

    public static class MockCompoundTag {
        private final Map<String, Object> map = new ConcurrentHashMap<>();

        public void putString(String key, String value) {
            map.put(key, value);
        }

        public String getString(String key) {
            Object v = map.get(key);
            return v != null ? v.toString() : "";
        }

        public void putInt(String key, int value) {
            map.put(key, value);
        }

        public int getInt(String key) {
            Object v = map.get(key);
            return v instanceof Number num ? num.intValue() : 0;
        }

        public void put(String key, Object tag) {
            map.put(key, tag);
        }

        public Object get(String key) {
            return map.get(key);
        }

        public MockCompoundTag getCompound(String key) {
            Object v = map.get(key);
            return v instanceof MockCompoundTag m ? m : null;
        }

        public Object getList(String key, int type) {
            return map.get(key);
        }

        public void remove(String key) {
            map.remove(key);
        }

        public boolean containsKey(String key) {
            return map.containsKey(key);
        }
    }
}
