package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import com.kyroxova.continuumlib.shims.MenuTypeShim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Verification Suite for Subsystem:
 * Container Menus, Screen Handlers & Slot Synchronization (1.7.9 -> 26.3+).
 * Covers MenuType creation, container listeners, and slot synchronization.
 */
public class MenuAndContainerVerificationTest {

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
        @DisplayName("1.1 Extended MenuType creation, registration, and virtual menu instantiation")
        public void testMenuTypeCreation() {
            String menuId = "mymod:custom_furnace_menu";
            Object mockFactory = new Object() {
                public Object create(int windowId, Object inv) {
                    return new MenuTypeShim.VirtualContainerMenu(windowId);
                }
            };

            Object menuType = MenuTypeShim.createMenuType(menuId, mockFactory);
            assertNotNull(menuType);
            assertSame(menuType, MenuTypeShim.getMenuType(menuId));

            if (menuType instanceof MenuTypeShim.VirtualMenuType vmt) {
                assertEquals(menuId, vmt.getId());
                Object createdMenu = vmt.create(42, new Object());
                assertTrue(createdMenu instanceof MenuTypeShim.VirtualContainerMenu);
                assertEquals(42, ((MenuTypeShim.VirtualContainerMenu) createdMenu).getContainerId());
            }

            assertNull(MenuTypeShim.createMenuType(null));
        }

        @Test
        @DisplayName("1.2 Container slot listener registration and change event notification")
        public void testSlotListenerNotification() {
            MenuTypeShim.VirtualContainerMenu menu = new MenuTypeShim.VirtualContainerMenu(1);
            AtomicInteger changesReceived = new AtomicInteger(0);

            Object mockListener = new Object() {
                public void slotChanged(Object container, int slotId, Object itemStack) {
                    if (container == menu && slotId == 5 && "minecraft:diamond".equals(itemStack)) {
                        changesReceived.incrementAndGet();
                    }
                }
            };

            MenuTypeShim.addSlotListener(menu, mockListener);
            assertTrue(MenuTypeShim.getSlotListeners(menu).contains(mockListener));

            MenuTypeShim.syncSlot(menu, 5, "minecraft:diamond");
            assertEquals(1, changesReceived.get());

            MenuTypeShim.broadcastChanges(menu);
            assertEquals(2, changesReceived.get());
        }

        @Test
        @DisplayName("1.3 Slot synchronization, retrieval, and slot counting")
        public void testSlotSynchronization() {
            MenuTypeShim.VirtualContainerMenu menu = new MenuTypeShim.VirtualContainerMenu(2);

            MenuTypeShim.syncSlot(menu, 0, "minecraft:iron_sword");
            MenuTypeShim.syncSlot(menu, 1, "minecraft:shield");

            assertEquals("minecraft:iron_sword", MenuTypeShim.getSlotItem(menu, 0));
            assertEquals("minecraft:shield", MenuTypeShim.getSlotItem(menu, 1));
            assertNull(MenuTypeShim.getSlotItem(menu, 2));
            assertEquals(2, MenuTypeShim.getSlotCount(menu));

            // Virtual container setSlot alias
            menu.setSlot(2, "minecraft:golden_apple");
            assertEquals("minecraft:golden_apple", MenuTypeShim.getSlotItem(menu, 2));
            assertEquals(3, MenuTypeShim.getSlotCount(menu));

            assertNull(MenuTypeShim.getSlotItem(null, 0));
            assertEquals(0, MenuTypeShim.getSlotCount(null));
        }
    }

    // =========================================================================
    // Tier 2: Rules Catalog & Transformation Delta Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 2: Rules Catalog & Transformation Delta Tests")
    class Tier2RulesCatalog {

        @Test
        @DisplayName("2.1 IForgeMenuType.create polyfill active for NeoForge 1.20.4+ targets")
        public void testIForgeMenuTypePolyfill() {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target = TargetSpec.of("1.20.4", "neoforge");

            List<TransformationRule> rules = kb.getApplicableRules(base, target);

            boolean hasMenuTypePolyfill = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraftforge/common/extensions/IForgeMenuType".equals(pr.getSourceOwner()) &&
                    "create".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/MenuTypeShim".equals(pr.getShimOwner()) &&
                    "createMenuType".equals(pr.getShimName())
            );
            assertTrue(hasMenuTypePolyfill, "IForgeMenuType.create must polyfill to MenuTypeShim.createMenuType on NeoForge");
        }
    }

    // =========================================================================
    // Tier 3: Concurrency & Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 3: Concurrency & Stress Tests")
    class Tier3Concurrency {

        @Test
        @DisplayName("3.1 Highly concurrent slot updates and broadcast synchronizations")
        public void testConcurrentSlotSync() throws InterruptedException {
            int threadCount = 16;
            int updatesPerThread = 200;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            MenuTypeShim.VirtualContainerMenu menu = new MenuTypeShim.VirtualContainerMenu(99);

            for (int t = 0; t < threadCount; t++) {
                final int slot = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < updatesPerThread; i++) {
                            MenuTypeShim.syncSlot(menu, slot, "item_" + slot + "_" + i);
                            MenuTypeShim.getSlotItem(menu, slot);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent slot updates timed out");
            executor.shutdown();
            assertEquals(threadCount, MenuTypeShim.getSlotCount(menu));
        }
    }
}
