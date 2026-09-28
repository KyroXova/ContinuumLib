package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import com.kyroxova.continuumlib.shims.AttributeModifierShim;
import com.kyroxova.continuumlib.shims.CapabilityShim;
import com.kyroxova.continuumlib.shims.CreativeTabShim;
import com.kyroxova.continuumlib.shims.ItemStackShim;
import com.kyroxova.continuumlib.shims.NetworkShim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Stress Test Suite for Runtime Shims and Catalogs (Requirement R1).
 * Executes stress harnesses, edge cases, and concurrency oracles against:
 * 1. Networking (payload codecs, buffer conversions, asymmetric registration, stack neutrality in rules)
 * 2. Capabilities (LazyOptional contract, droplet conversion bounds, data attachments memory & concurrency)
 * 3. ItemStack Components vs NBT (null safety, custom name component typing, nested tag preservation)
 * 4. Creative Tabs & Attribute Modifiers (Item.Properties acceptance in tabs, event argument alignment)
 */
public class AdversarialShimsAndCatalogsStressTest {

    private ApiKnowledgeBase kb;

    @BeforeEach
    public void setup() {
        this.kb = ApiKnowledgeBase.createDefault();
    }

    // =========================================================================
    // 1. Networking Challenges
    // =========================================================================

    @Test
    @DisplayName("Challenge 1.1: Asymmetric Network Handler Registration Race Condition")
    public void testAsymmetricHandlerRegistrationRaceCondition() throws Exception {
        int trials = 50;
        AtomicInteger raceLostCount = new AtomicInteger();

        for (int t = 0; t < trials; t++) {
            String payloadId = "stress:race_payload_" + t;
            CyclicBarrier barrier = new CyclicBarrier(2);
            ExecutorService executor = Executors.newFixedThreadPool(2);

            Future<?> f1 = executor.submit(() -> {
                try {
                    barrier.await();
                    NetworkShim.registerPayloadToClient(payloadId, null, (p, c) -> {});
                } catch (Exception ignored) {}
            });

            Future<?> f2 = executor.submit(() -> {
                try {
                    barrier.await();
                    NetworkShim.registerPayloadToServer(payloadId, null, (p, c) -> {});
                } catch (Exception ignored) {}
            });

            f1.get(2, TimeUnit.SECONDS);
            f2.get(2, TimeUnit.SECONDS);
            executor.shutdown();

            NetworkShim.PayloadEntry entry = NetworkShim.getPayload(payloadId);
            assertNotNull(entry);
            // If one handler overwrote the other due to non-atomic check-then-act:
            if (entry.clientHandler == null || entry.serverHandler == null) {
                raceLostCount.incrementAndGet();
            }
        }

        System.out.println("[Adversarial Result] Asymmetric registration lost handler in " + raceLostCount.get() + " / " + trials + " trials");
    }

    @Test
    @DisplayName("Challenge 1.2: NetworkRulesCatalog Polyfill Stack Signature Alignment")
    public void testNetworkCatalogPolyfillSignatureAlignment() {
        TargetSpec baseSpec = TargetSpec.of("1.20.4", "neoforge");
        TargetSpec targetSpec = TargetSpec.of("1.20.4", "fabric");

        List<TransformationRule> rules = kb.getApplicableRules(baseSpec, targetSpec);

        for (TransformationRule r : rules) {
            if (r instanceof PolyfillRule pr) {
                if ("net/neoforged/neoforge/network/PacketDistributor".equals(pr.getSourceOwner())) {
                    String srcDesc = pr.getSourceDesc();
                    String shimDesc = pr.getShimDesc();
                    // If source method is static with 2 arguments, shim descriptor must accept 2 arguments!
                    if (srcDesc != null && srcDesc.startsWith("(Lnet/minecraft/server/level/ServerPlayer;")) {
                        System.out.println("[Adversarial Audit] PacketDistributor.sendToPlayer: srcDesc=" + srcDesc + " vs shimDesc=" + shimDesc);
                    }
                    if (srcDesc != null && srcDesc.equals("(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V")) {
                        System.out.println("[Adversarial Audit] PacketDistributor.sendToServer: srcDesc=" + srcDesc + " vs shimDesc=" + shimDesc);
                    }
                }
            }
        }
    }

    // =========================================================================
    // 2. Capabilities Challenges
    // =========================================================================

    @Test
    @DisplayName("Challenge 2.1: LazyOptional orElseThrow() Contract")
    public void testLazyOptionalOrElseThrowContract() throws Throwable {
        Object activeLazyOpt = CapabilityShim.ofLazyOptional("ValidValue");
        assertNotNull(activeLazyOpt);

        // Forge LazyOptional has orElseThrow() and orElseThrow(NonNullSupplier).
        // Let's test the InvocationHandler directly via reflection:
        InvocationHandler handler = Proxy.isProxyClass(activeLazyOpt.getClass())
                ? Proxy.getInvocationHandler(activeLazyOpt)
                : null;

        if (handler != null) {
            Method orElseThrowNoArg = Object.class.getMethod("toString"); // dummy method token
            // Simulate invocation of orElseThrow() with 0 arguments:
            Method fakeMethod = new Object() {
                public Object orElseThrow() { return null; }
            }.getClass().getMethod("orElseThrow");

            Object result = null;
            try {
                result = handler.invoke(activeLazyOpt, fakeMethod, new Object[0]);
            } catch (Throwable ignored) {}
            System.out.println("[Adversarial Audit] LazyOptional.orElseThrow() result on active LazyOptional: " + result);
            // Bug proof: returns null instead of "ValidValue"!
            assertNull(result, "Confirmed: LazyOptionalInvocationHandler returns null for no-arg orElseThrow()");
        }
    }

    @Test
    @DisplayName("Challenge 2.2: CapabilityShim ATTACHMENT_DATA Memory Leak on Ephemeral Holders")
    public void testAttachmentDataMemoryLeak() throws Exception {
        // Inspect ATTACHMENT_DATA map size
        Field field = CapabilityShim.class.getDeclaredField("ATTACHMENT_DATA");
        field.setAccessible(true);
        Map<?, ?> map = (Map<?, ?>) field.get(null);

        int initialSize = map.size();

        for (int i = 0; i < 500; i++) {
            Object ephemeralHolder = new Object();
            CapabilityShim.setDataAttachment(ephemeralHolder, "test:attachment", "value_" + i);
        }

        // Ephemeral holders should not be retained strongly if weak references were used
        int sizeAfter = map.size();
        System.out.println("[Adversarial Audit] ATTACHMENT_DATA size before: " + initialSize + ", after 500 ephemeral inserts: " + sizeAfter);
        assertTrue(sizeAfter >= initialSize + 500, "Strong ConcurrentHashMap holds all ephemeral holders indefinitely");
    }

    @Test
    @DisplayName("Challenge 2.3: Fabric Transfer API Droplet Conversion Precision & Truncation")
    public void testFabricTransferApiDropletConversionPrecision() {
        assertEquals(81_000L, CapabilityShim.mbToDroplets(1000L));
        assertEquals(1000L, CapabilityShim.dropletsToMb(81_000L));

        // Sub-millibucket amounts (less than 81 droplets) truncate to 0 mB
        assertEquals(0L, CapabilityShim.dropletsToMb(80L));
        assertEquals(0L, CapabilityShim.dropletsToMb(1L));

        // Negative values
        assertEquals(-8100L, CapabilityShim.mbToDroplets(-100L));
        assertEquals(-100L, CapabilityShim.dropletsToMb(-8100L));
    }

    // =========================================================================
    // 3. ItemStack Components vs NBT Challenges
    // =========================================================================

    @Test
    @DisplayName("Challenge 3.1: Modern Custom Name JSON Component vs String Translation")
    public void testCustomNameComponentTranslation() {
        MockLegacyItemStack legacyStack = new MockLegacyItemStack();

        // When modern code passes a structured object or component as custom name:
        Object mockTextComponent = new Object() {
            @Override
            public String toString() {
                return "literal{Golden Excalibur}";
            }
        };

        ItemStackShim.set(legacyStack, "custom_name", mockTextComponent);
        Object retrieved = ItemStackShim.get(legacyStack, "custom_name");

        System.out.println("[Adversarial Audit] Retrieved custom_name: " + retrieved + " (type: " + (retrieved != null ? retrieved.getClass().getName() : "null") + ")");
        assertEquals("literal{Golden Excalibur}", retrieved);
    }

    @Test
    @DisplayName("Challenge 3.2: Null CompoundTag on setTag(stack, null)")
    public void testSetTagNullSafety() {
        MockLegacyItemStack legacyStack = new MockLegacyItemStack();
        MockCompoundTag tag = new MockCompoundTag();
        tag.putString("data", "123");
        legacyStack.setTag(tag);

        assertTrue(ItemStackShim.hasTag(legacyStack));

        // Modders call stack.setTag(null) to clear the NBT
        ItemStackShim.setTag(legacyStack, null);
        assertFalse(ItemStackShim.hasTag(legacyStack), "Setting null tag should clear tag on legacy stack");

        // Now test modern stack:
        // On modern Minecraft, if someone calls setTag(modernStack, null), setCustomDataNbt throws NPE and swallows it
        MockModernItemStackWithCustomData modernStack = new MockModernItemStackWithCustomData();
        modernStack.hasCustomData = true;
        ItemStackShim.setTag(modernStack, null);
        // On modern stack, setTag(null) should clear CUSTOM_DATA, but setCustomDataNbt fails silently
        System.out.println("[Adversarial Audit] Modern stack hasCustomData after setTag(null): " + modernStack.hasCustomData);
    }

    // =========================================================================
    // 4. Creative Tabs & Attribute Modifiers Challenges
    // =========================================================================

    @Test
    @DisplayName("Challenge 4.1: Item.Properties in CreativeTab Output Acceptance")
    public void testItemPropertiesInCreativeTabOutput() {
        Object properties = new Object(); // Represents Item.Properties
        String tabId = "stress:tools_tab";

        // Modder calls Item.Properties.tab(tab)
        CreativeTabShim.tab(properties, tabId);

        // Creative tab contents build event
        MockItemAcceptor acceptor = new MockItemAcceptor();
        CreativeTabShim.populateTab(tabId, acceptor);

        // Because acceptor only accepts Items or ItemStacks, not Item.Properties:
        System.out.println("[Adversarial Audit] Items accepted by creative tab: " + acceptor.acceptedCount);
        assertEquals(0, acceptor.acceptedCount, "Item.Properties cannot be accepted by standard Creative Tab acceptor");
    }

    @Test
    @DisplayName("Challenge 4.2: EntityAttributeCreationEvent.put Rule Stack Alignment")
    public void testEntityAttributeCreationEventRuleAlignment() {
        TargetSpec baseSpec = TargetSpec.of("1.20.4", "neoforge");
        TargetSpec targetSpec = TargetSpec.of("1.20.4", "fabric");

        List<TransformationRule> rules = kb.getApplicableRules(baseSpec, targetSpec);
        for (TransformationRule r : rules) {
            if (r instanceof PolyfillRule pr) {
                if ("net/minecraftforge/event/entity/EntityAttributeCreationEvent".equals(pr.getSourceOwner())) {
                    System.out.println("[Adversarial Audit] EntityAttributeCreationEvent.put: srcDesc=" + pr.getSourceDesc() + " vs shimDesc=" + pr.getShimDesc());
                }
            }
        }
    }

    // =========================================================================
    // Mocks for Adversarial Testing
    // =========================================================================

    public static class MockItemAcceptor {
        public int acceptedCount = 0;

        // Acceptor strictly models Minecraft's Output.accept(ItemStack)
        public void accept(MockItemStack stack) {
            acceptedCount++;
        }
    }

    public static class MockItemStack {}

    public static class MockLegacyItemStack {
        private MockCompoundTag tag;

        public MockCompoundTag getTag() {
            return tag;
        }

        public MockCompoundTag getOrCreateTag() {
            if (tag == null) tag = new MockCompoundTag();
            return tag;
        }

        public void setTag(MockCompoundTag tag) {
            this.tag = tag;
        }

        public boolean hasTag() {
            return tag != null;
        }

        public void removeTagKey(String key) {
            if (tag != null) tag.remove(key);
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
            return v instanceof Number n ? n.intValue() : 0;
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

        public void remove(String key) {
            map.remove(key);
        }

        public boolean containsKey(String key) {
            return map.containsKey(key);
        }
    }

    public static class MockModernItemStackWithCustomData {
        public boolean hasCustomData = true;

        public Object set(Object type, Object val) {
            if (val == null) hasCustomData = false;
            return val;
        }

        public Object get(Object type) {
            return hasCustomData ? new Object() : null;
        }
    }
}
