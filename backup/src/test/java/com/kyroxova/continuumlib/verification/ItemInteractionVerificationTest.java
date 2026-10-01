package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import com.kyroxova.continuumlib.shims.ItemInteractionShim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification Suite for ItemInteractionShim & ItemAndComponentRulesCatalog interaction rules.
 * Covers InteractionResultHolder <-> InteractionResult adaptation across 1.7.9 -> 26.3+.
 */
public class ItemInteractionVerificationTest {

    private ApiKnowledgeBase kb;

    @BeforeEach
    public void setup() {
        this.kb = ApiKnowledgeBase.createDefault();
    }

    @Nested
    @DisplayName("Tier 1: Isolation Tests")
    class Tier1Isolation {

        @Test
        @DisplayName("1.1 ResultHolder creation methods")
        public void testResultHolderCreation() {
            Object mockStack = "minecraft:apple";

            Object success = ItemInteractionShim.success(mockStack);
            assertEquals("SUCCESS", ItemInteractionShim.getResult(success));
            assertEquals(mockStack, ItemInteractionShim.getObject(success));

            Object pass = ItemInteractionShim.pass(mockStack);
            assertEquals("PASS", ItemInteractionShim.getResult(pass));
            assertEquals(mockStack, ItemInteractionShim.getObject(pass));

            Object fail = ItemInteractionShim.fail(mockStack);
            assertEquals("FAIL", ItemInteractionShim.getResult(fail));
            assertEquals(mockStack, ItemInteractionShim.getObject(fail));

            Object consume = ItemInteractionShim.consume(mockStack);
            assertEquals("CONSUME", ItemInteractionShim.getResult(consume));
            assertEquals(mockStack, ItemInteractionShim.getObject(consume));

            Object sidedClient = ItemInteractionShim.sidedSuccess(mockStack, true);
            assertEquals("SUCCESS", ItemInteractionShim.getResult(sidedClient));

            Object sidedServer = ItemInteractionShim.sidedSuccess(mockStack, false);
            assertEquals("CONSUME", ItemInteractionShim.getResult(sidedServer));
        }

        @Test
        @DisplayName("1.2 InteractionResultHolder <-> InteractionResult adaptation")
        public void testAdaptation() {
            Object mockStack = "minecraft:diamond_sword";
            Object successHolder = ItemInteractionShim.success(mockStack);

            Object ir = ItemInteractionShim.toInteractionResult(successHolder);
            assertNotNull(ir);
            assertTrue(ir.toString().contains("SUCCESS"));

            Object adaptedHolder = ItemInteractionShim.toResultHolder("SUCCESS", mockStack);
            assertEquals("SUCCESS", ItemInteractionShim.getResult(adaptedHolder));
            assertEquals(mockStack, ItemInteractionShim.getObject(adaptedHolder));

            Object passHolder = ItemInteractionShim.toResultHolder("PASS", mockStack);
            assertEquals("PASS", ItemInteractionShim.getResult(passHolder));
        }
    }

    @Nested
    @DisplayName("Tier 2: Rules Catalog & Transformation Delta Tests")
    class Tier2RulesCatalog {

        @Test
        @DisplayName("2.1 ActionResult <-> InteractionResultHolder redirects")
        public void testClassRedirects() {
            TargetSpec base = TargetSpec.of("1.16.5", "forge");
            TargetSpec target = TargetSpec.of("1.18.2", "forge");

            List<TransformationRule> rules = kb.getApplicableRules(base, target);

            boolean hasHolderRedirect = rules.stream().anyMatch(r ->
                    r instanceof ClassRedirectRule cr &&
                    "net/minecraft/util/ActionResult".equals(cr.getSourceInternalName()) &&
                    "net/minecraft/world/InteractionResultHolder".equals(cr.getTargetInternalName())
            );
            assertTrue(hasHolderRedirect, "ActionResult must redirect to InteractionResultHolder on 1.17+");

            boolean hasResultRedirect = rules.stream().anyMatch(r ->
                    r instanceof ClassRedirectRule cr &&
                    "net/minecraft/util/ActionResultType".equals(cr.getSourceInternalName()) &&
                    "net/minecraft/world/InteractionResult".equals(cr.getTargetInternalName())
            );
            assertTrue(hasResultRedirect, "ActionResultType must redirect to InteractionResult on 1.17+");
        }

        @Test
        @DisplayName("2.2 InteractionResultHolder polyfill rules")
        public void testPolyfillRules() {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target = TargetSpec.of("1.20.4", "neoforge");

            List<TransformationRule> rules = kb.getApplicableRules(base, target);

            boolean hasSuccess = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/world/InteractionResultHolder".equals(pr.getSourceOwner()) &&
                    "success".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/ItemInteractionShim".equals(pr.getShimOwner())
            );
            assertTrue(hasSuccess, "InteractionResultHolder.success must polyfill to ItemInteractionShim");

            boolean hasConsume = rules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/world/InteractionResultHolder".equals(pr.getSourceOwner()) &&
                    "consume".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/ItemInteractionShim".equals(pr.getShimOwner())
            );
            assertTrue(hasConsume, "InteractionResultHolder.consume must polyfill to ItemInteractionShim");
        }
    }

    @Nested
    @DisplayName("Tier 3: Concurrency & Stress Tests")
    class Tier3Concurrency {

        @Test
        @DisplayName("3.1 High-throughput concurrent result holder conversions")
        public void testConcurrentConversions() throws InterruptedException {
            int threadCount = 16;
            int opsPerThread = 200;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < opsPerThread; i++) {
                            String item = "item_" + threadId + "_" + i;
                            Object holder = ItemInteractionShim.success(item);
                            Object ir = ItemInteractionShim.toInteractionResult(holder);
                            Object back = ItemInteractionShim.toResultHolder(ir, item);
                            assertEquals("SUCCESS", ItemInteractionShim.getResult(back));
                            assertEquals(item, ItemInteractionShim.getObject(back));
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent conversions timed out");
            executor.shutdown();
        }
    }
}
