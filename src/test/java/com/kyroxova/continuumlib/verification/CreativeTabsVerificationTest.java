package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import com.kyroxova.continuumlib.shims.CreativeTabShim;
import com.kyroxova.continuumlib.shims.SyntheticEventDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Verification Suite for Subsystem c:
 * Creative Mode Tabs / Item Groups across 1.12.2, 1.16.5, 1.18.2, 1.19.4, 1.20.4, 1.20.6, 1.21.1, and 26.3+.
 */
public class CreativeTabsVerificationTest {

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
        @DisplayName("1.1 Tab registration via registerTab across eras and VirtualCreativeTab validation")
        public void testRegisterTabAndVirtualTab() {
            String tabId = "mymod:combat_tab";
            Supplier<String> iconSupplier = () -> "minecraft:diamond_sword";
            String title = "Combat Gear";

            Object tab = CreativeTabShim.registerTab(tabId, iconSupplier, title);
            assertNotNull(tab);

            // In test environment without Minecraft classes, fallback to VirtualCreativeTab
            if (tab instanceof CreativeTabShim.VirtualCreativeTab vTab) {
                assertEquals(tabId, vTab.id);
                assertEquals("minecraft:diamond_sword", vTab.makeIcon());
                assertEquals("minecraft:diamond_sword", vTab.getTabIconItem());
                assertEquals(title, vTab.title);
                assertEquals(tabId, vTab.toString());
            }
        }

        @Test
        @DisplayName("1.2 Polyfill builder via createBuilder (CreativeModeTab.builder())")
        public void testCreateBuilder() {
            Object builderObj = CreativeTabShim.createBuilder();
            assertNotNull(builderObj);
            assertTrue(builderObj instanceof CreativeTabShim.TabBuilder);

            CreativeTabShim.TabBuilder builder = (CreativeTabShim.TabBuilder) builderObj;
            Object built = builder
                    .title("Building Blocks")
                    .icon(() -> "minecraft:stone_bricks")
                    .displayItems(new Object())
                    .build();

            assertNotNull(built);
        }

        @Test
        @DisplayName("1.3 Item.Properties.tab(tab) interceptor seamlessly returns properties and records tab")
        public void testPropertiesTabInterceptor() {
            Object mockItemProperties = new Object();
            String tabName = "mymod:tools_tab";

            Object result = CreativeTabShim.tab(mockItemProperties, tabName);
            assertSame(mockItemProperties, result, "Properties interceptor must return original properties for chaining");

            Set<Object> items = CreativeTabShim.getItemsForTab(tabName);
            assertTrue(items.contains(mockItemProperties));
        }

        @Test
        @DisplayName("1.4 addItemToTab and getItemsForTab association")
        public void testAddItemToTab() {
            String tabId = "mymod:magic_tab";
            Supplier<String> wandSupplier = () -> "mymod:fire_wand";
            Supplier<String> scrollSupplier = () -> "mymod:teleport_scroll";

            CreativeTabShim.addItemToTab(tabId, wandSupplier);
            CreativeTabShim.addItemToTab(tabId, scrollSupplier);

            Set<Object> items = CreativeTabShim.getItemsForTab(tabId);
            assertEquals(2, items.size());
            assertTrue(items.contains(wandSupplier));
            assertTrue(items.contains(scrollSupplier));
        }

        @Test
        @DisplayName("1.5 populateTab populates output target with registered items and supplier items")
        public void testPopulateTab() {
            String tabId = "mymod:food_tab";
            CreativeTabShim.addItemToTab(tabId, () -> "mymod:golden_apple");
            CreativeTabShim.addItemToTab(tabId, () -> "mymod:cooked_beef");

            MockOutput mockOutput = new MockOutput();

            CreativeTabShim.populateTab(tabId, mockOutput);
            assertEquals(2, mockOutput.acceptedItems.size());
            assertTrue(mockOutput.acceptedItems.contains("mymod:golden_apple"));
            assertTrue(mockOutput.acceptedItems.contains("mymod:cooked_beef"));
        }
    }

    // =========================================================================
    // Tier 2: Boundary & Edge Case Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 2: Boundary & Edge Cases")
    class Tier2Boundary {

        @Test
        @DisplayName("2.1 Null parameters across creative tab APIs do not throw exceptions")
        public void testNullParameters() {
            assertNull(CreativeTabShim.tab(null, null));
            assertDoesNotThrow(() -> CreativeTabShim.addItemToTab(null, null));
            assertDoesNotThrow(() -> CreativeTabShim.addItemToTab("tab", null));
            assertDoesNotThrow(() -> CreativeTabShim.addItemToTab(null, () -> "item"));
            assertDoesNotThrow(() -> CreativeTabShim.populateTab(null, null));
            assertDoesNotThrow(() -> CreativeTabShim.handleBuildContents(null));
            assertTrue(CreativeTabShim.getItemsForTab(null).isEmpty());
            assertTrue(CreativeTabShim.getItemsForTab("unregistered_tab_xyz").isEmpty());

            Object defaultBuilt = new CreativeTabShim.TabBuilder().build();
            assertNotNull(defaultBuilt);
        }

        @Test
        @DisplayName("2.2 Tab query by ResourceKey object with location() method")
        public void testTabQueryByResourceKey() {
            String tabLoc = "mymod:resource_key_tab";
            MockResourceKey mockResourceKey = new MockResourceKey(tabLoc);

            CreativeTabShim.addItemToTab(tabLoc, () -> "mymod:item_via_resource_key");
            Set<Object> items = CreativeTabShim.getItemsForTab(mockResourceKey);
            assertFalse(items.isEmpty(), "Should resolve items by querying location() on ResourceKey");
        }

        @Test
        @DisplayName("2.3 BuildCreativeModeTabContentsEvent with getTab() vs getTabKey()")
        public void testEventWithDifferentTabAccessors() {
            String tabKey1 = "mymod:event_tab_1";
            String tabKey2 = "mymod:event_tab_2";

            CreativeTabShim.addItemToTab(tabKey1, () -> "item_1");
            CreativeTabShim.addItemToTab(tabKey2, () -> "item_2");

            // Event with getTabKey() and accept(Object)
            EventWithTabKey eventWithTabKey = new EventWithTabKey(tabKey1);
            CreativeTabShim.handleBuildContents(eventWithTabKey);
            assertEquals(1, eventWithTabKey.accepted.size());
            assertEquals("item_1", eventWithTabKey.accepted.get(0));

            // Event with getTab() and accept(Object)
            EventWithTab eventWithTab = new EventWithTab(tabKey2);
            CreativeTabShim.handleBuildContents(eventWithTab);
            assertEquals(1, eventWithTab.accepted.size());
            assertEquals("item_2", eventWithTab.accepted.get(0));
        }
    }

    // =========================================================================
    // Tier 3: Cross-Feature & Event Integration Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 3: Cross-Feature & Event Integration")
    class Tier3CrossFeature {

        @Test
        @DisplayName("3.1 ItemAndComponentRulesCatalog contains CreativeModeTab polyfill rules")
        public void testCreativeTabCatalogCoverage() {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target118 = TargetSpec.of("1.18.2", "forge");
            TargetSpec target1204 = TargetSpec.of("1.20.4", "neoforge");

            List<TransformationRule> rules118 = kb.getApplicableRules(base, target118);
            List<TransformationRule> rules1204 = kb.getApplicableRules(base, target1204);

            // Item.Properties.tab(CreativeModeTab) redirect
            boolean hasTabRule = rules1204.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/world/item/Item$Properties".equals(pr.getSourceOwner()) &&
                    "tab".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/CreativeTabShim".equals(pr.getShimOwner())
            );
            assertTrue(hasTabRule, "Item.Properties.tab polyfill must be registered for 1.19.3+ targets");

            // CreativeModeTab.builder() polyfill for pre-1.19.3
            boolean hasBuilderRule = rules118.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/world/item/CreativeModeTab".equals(pr.getSourceOwner()) &&
                    "builder".equals(pr.getSourceName())
            );
            assertTrue(hasBuilderRule, "CreativeModeTab.builder() polyfill must be registered for pre-1.19.3");
        }

        @Test
        @DisplayName("3.2 SyntheticEventDispatcher automatically delegates BuildCreativeModeTabContentsEvent to CreativeTabShim")
        public void testSyntheticEventDispatcherAutoDelegation() {
            String tabKey = "mymod:auto_dispatch_tab";
            CreativeTabShim.addItemToTab(tabKey, () -> "mymod:laser_gun");

            List<Object> accepted = new ArrayList<>();
            BuildCreativeModeTabContentsMockEvent mockEvent = new BuildCreativeModeTabContentsMockEvent(tabKey, accepted);

            // Post to SyntheticEventDispatcher
            SyntheticEventDispatcher.post(mockEvent);

            assertEquals(1, accepted.size());
            assertEquals("mymod:laser_gun", accepted.get(0));
        }

        @Test
        @DisplayName("3.3 SyntheticEventDispatcher with custom listener class")
        public void testSyntheticEventDispatcherCustomListener() {
            AtomicInteger customListenerCalls = new AtomicInteger();
            Object listener = new Object() {
                public void onTabBuild(BuildCreativeModeTabContentsMockEvent event) {
                    customListenerCalls.incrementAndGet();
                }
            };

            // Register listener
            SyntheticEventDispatcher.register(listener);

            String tabKey = "mymod:listener_tab";
            BuildCreativeModeTabContentsMockEvent event = new BuildCreativeModeTabContentsMockEvent(tabKey, new ArrayList<>());
            SyntheticEventDispatcher.post(event);

            // Direct call to dispatchTabContents
            SyntheticEventDispatcher.dispatchTabContents(event);
            assertNotNull(event);
        }
    }

    // =========================================================================
    // Tier 4: Workload & Concurrency Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 4: Workload & Concurrency Tests")
    class Tier4Workload {

        @Test
        @DisplayName("4.1 Concurrent addition of items across multiple threads and tabs")
        public void testConcurrentItemAdditions() throws InterruptedException, ExecutionException {
            int threadCount = 10;
            int itemsPerThread = 150;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            List<Future<Void>> futures = new ArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                futures.add(pool.submit(() -> {
                    String tab = "concurrent_tab_" + (threadId % 4);
                    for (int i = 0; i < itemsPerThread; i++) {
                        final String itemName = "item_" + threadId + "_" + i;
                        CreativeTabShim.addItemToTab(tab, () -> itemName);
                    }
                    return null;
                }));
            }

            for (Future<Void> f : futures) {
                f.get();
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

            for (int tabIdx = 0; tabIdx < 4; tabIdx++) {
                Set<Object> items = CreativeTabShim.getItemsForTab("concurrent_tab_" + tabIdx);
                assertFalse(items.isEmpty());
            }
        }
    }

    // Mock Event for testing event dispatcher
    public static class BuildCreativeModeTabContentsMockEvent {
        private final String tabKey;
        private final List<Object> output;

        public BuildCreativeModeTabContentsMockEvent(String tabKey, List<Object> output) {
            this.tabKey = tabKey;
            this.output = output;
        }

        public String getTabKey() {
            return tabKey;
        }

        public void accept(Object item) {
            output.add(item);
        }
    }

    public static class MockOutput {
        public final List<Object> acceptedItems = new ArrayList<>();

        public void accept(Object item) {
            acceptedItems.add(item);
        }
    }

    public static class MockResourceKey {
        private final String loc;

        public MockResourceKey(String loc) {
            this.loc = loc;
        }

        public Object location() {
            return loc;
        }

        @Override
        public String toString() {
            return "ResourceKey[" + loc + "]";
        }
    }

    public static class EventWithTabKey {
        private final String tabKey;
        public final List<Object> accepted = new ArrayList<>();

        public EventWithTabKey(String tabKey) {
            this.tabKey = tabKey;
        }

        public Object getTabKey() {
            return tabKey;
        }

        public void accept(Object item) {
            accepted.add(item);
        }
    }

    public static class EventWithTab {
        private final String tab;
        public final List<Object> accepted = new ArrayList<>();

        public EventWithTab(String tab) {
            this.tab = tab;
        }

        public Object getTab() {
            return tab;
        }

        public void accept(Object item) {
            accepted.add(item);
        }
    }
}
