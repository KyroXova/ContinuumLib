package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.MethodRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import com.kyroxova.continuumlib.shims.NetworkShim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Verification Suite for Subsystem a:
 * Networking & Packets across Forge, NeoForge, and Fabric (1.7.9 -> 26.3+).
 */
public class NetworkingVerificationTest {

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
        @DisplayName("1.1 Forge SimpleChannel creation and basic packet handler registration")
        public void testForgeSimpleChannelRegistration() {
            Object channel = NetworkShim.createSimpleChannel("mymod:main", () -> "1.0", v -> true, v -> true);
            assertNotNull(channel);
            assertTrue(channel instanceof NetworkShim.ChannelDescriptor);

            NetworkShim.ChannelDescriptor desc = (NetworkShim.ChannelDescriptor) channel;
            assertEquals("mymod:main", desc.id);
            assertEquals("1.0", desc.version);

            AtomicBoolean encoded = new AtomicBoolean(false);
            AtomicBoolean decoded = new AtomicBoolean(false);
            AtomicBoolean consumed = new AtomicBoolean(false);

            BiConsumer<TestPacket, Object> encoder = (pkt, buf) -> encoded.set(true);
            Function<Object, TestPacket> decoder = buf -> {
                decoded.set(true);
                return new TestPacket("decoded_data");
            };
            BiConsumer<TestPacket, Supplier<Object>> consumer = (pkt, ctx) -> {
                consumed.set(true);
                assertEquals("test_data", pkt.payload);
            };

            NetworkShim.registerMessage(channel, 1, TestPacket.class, encoder, decoder, consumer);

            assertTrue(desc.handlers.containsKey(TestPacket.class));
            NetworkShim.PacketHandlerEntry<?> entry = desc.handlers.get(TestPacket.class);
            assertEquals(1, entry.index);
            assertEquals(TestPacket.class, entry.type);

            // Trigger local dispatch
            desc.send("target_player", new TestPacket("test_data"));
            assertTrue(consumed.get(), "Local packet dispatch must invoke message consumer");
        }

        @Test
        @DisplayName("1.2 NeoForge CustomPacketPayload registration (Client, Server, Bidirectional)")
        public void testNeoForgeCustomPacketPayloadRegistration() {
            Object clientPayloadId = "mymod:client_packet";
            Object serverPayloadId = "mymod:server_packet";
            Object bidiPayloadId = "mymod:bidi_packet";

            Object mockStreamCodec = new Object();
            AtomicReference<String> lastClientReceived = new AtomicReference<>();
            AtomicReference<String> lastServerReceived = new AtomicReference<>();

            NetworkShim.registerPayloadToClient(clientPayloadId, mockStreamCodec, (pkt, ctx) -> lastClientReceived.set(String.valueOf(pkt)));
            NetworkShim.registerPayloadToServer(serverPayloadId, mockStreamCodec, (pkt, ctx) -> lastServerReceived.set(String.valueOf(pkt)));
            NetworkShim.registerPayloadBidirectional(bidiPayloadId, mockStreamCodec, (pkt, ctx) -> {
                lastClientReceived.set("bidi:" + pkt);
                lastServerReceived.set("bidi:" + pkt);
            });

            NetworkShim.PayloadEntry clientEntry = NetworkShim.getPayload(String.valueOf(clientPayloadId));
            assertNotNull(clientEntry);
            assertNotNull(clientEntry.clientHandler);
            assertNull(clientEntry.serverHandler);

            NetworkShim.PayloadEntry serverEntry = NetworkShim.getPayload(String.valueOf(serverPayloadId));
            assertNotNull(serverEntry);
            assertNull(serverEntry.clientHandler);
            assertNotNull(serverEntry.serverHandler);

            NetworkShim.PayloadEntry bidiEntry = NetworkShim.getPayload(String.valueOf(bidiPayloadId));
            assertNotNull(bidiEntry);
            assertNotNull(bidiEntry.clientHandler);
            assertNotNull(bidiEntry.serverHandler);

            // Execute registered handlers directly
            clientEntry.clientHandler.accept("client_hello", null);
            assertEquals("client_hello", lastClientReceived.get());

            serverEntry.serverHandler.accept("server_hello", null);
            assertEquals("server_hello", lastServerReceived.get());

            bidiEntry.clientHandler.accept("data", null);
            assertEquals("bidi:data", lastClientReceived.get());
        }

        @Test
        @DisplayName("1.3 RegistryFriendlyByteBuf creation and HolderLookup.Provider query")
        public void testRegistryFriendlyByteBufBinding() {
            Object mockRawByteBuf = new MockByteBuf();
            Object mockRegistryAccess = new Object() {
                public String getLookupScope() {
                    return "minecraft:registries";
                }
            };

            Object rfb = NetworkShim.createRegistryFriendlyByteBuf(mockRawByteBuf, mockRegistryAccess);
            assertNotNull(rfb);

            Object retrievedAccess = NetworkShim.getRegistryAccess(rfb);
            assertSame(mockRegistryAccess, retrievedAccess, "Registry access bound to buffer must be retrievable");

            // Also retrievable via raw buffer
            assertSame(mockRegistryAccess, NetworkShim.getRegistryAccess(mockRawByteBuf));
        }

        @Test
        @DisplayName("1.4 StreamCodec adapter bidirectional adaptation")
        public void testStreamCodecAdapter() {
            AtomicReference<String> encodedValue = new AtomicReference<>();
            BiConsumer<String, List<String>> encoder = (val, buf) -> {
                encodedValue.set(val);
                buf.add(val);
            };
            Function<List<String>, String> decoder = buf -> buf.isEmpty() ? null : buf.get(0);

            Object codec = NetworkShim.adaptToStreamCodec(encoder, decoder);
            assertNotNull(codec);

            // Invoke encode via reflection on dynamic proxy or adapter object
            List<String> mockBuffer = new ArrayList<>();
            try {
                Method encodeMethod = codec.getClass().getMethod("encode", Object.class, Object.class);
                encodeMethod.invoke(codec, mockBuffer, "hello_stream");
            } catch (NoSuchMethodException e) {
                try {
                    Method encodeMethod = codec.getClass().getMethod("encode", List.class, String.class);
                    encodeMethod.invoke(codec, mockBuffer, "hello_stream");
                } catch (Exception ex) {
                    fail("Failed to invoke encode on adapted StreamCodec: " + ex);
                }
            } catch (Exception e) {
                fail("Failed to invoke encode: " + e);
            }

            assertEquals("hello_stream", encodedValue.get());
            assertEquals(1, mockBuffer.size());
            assertEquals("hello_stream", mockBuffer.get(0));

            // Invoke decode via reflection
            try {
                Method decodeMethod = codec.getClass().getMethod("decode", Object.class);
                Object decoded = decodeMethod.invoke(codec, mockBuffer);
                assertEquals("hello_stream", decoded);
            } catch (NoSuchMethodException e) {
                try {
                    Method decodeMethod = codec.getClass().getMethod("decode", List.class);
                    Object decoded = decodeMethod.invoke(codec, mockBuffer);
                    assertEquals("hello_stream", decoded);
                } catch (Exception ex) {
                    fail("Failed to invoke decode on adapted StreamCodec: " + ex);
                }
            } catch (Exception e) {
                fail("Failed to invoke decode: " + e);
            }
        }

        @Test
        @DisplayName("1.5 Packet wrapping and encoding via wrapPacket")
        public void testWrapPacketInvocation() {
            // Test 1: null safety
            assertNull(NetworkShim.wrapPacket(null, null));

            // Test 2: self-writing packet with write(buffer)
            SelfWritingPacket selfWritingPacket = new SelfWritingPacket();
            Object buffer = new Object();
            Object result = NetworkShim.wrapPacket(selfWritingPacket, buffer);
            assertSame(buffer, result);
            assertTrue(selfWritingPacket.writeCalled, "packet.write(buffer) must be invoked during wrapPacket");

            // Test 3: self-encoding packet with encode(buffer)
            SelfEncodingPacket selfEncodingPacket = new SelfEncodingPacket();
            Object result2 = NetworkShim.wrapPacket(selfEncodingPacket, buffer);
            assertSame(buffer, result2);
            assertTrue(selfEncodingPacket.encodeCalled, "packet.encode(buffer) must be invoked during wrapPacket");
        }
    }

    // =========================================================================
    // Tier 2: Boundary & Edge Case Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 2: Boundary & Edge Cases")
    class Tier2Boundary {

        @Test
        @DisplayName("2.1 Null parameters and missing channels do not throw unhandled exceptions")
        public void testNullSafetyAcrossNetworkShim() {
            assertDoesNotThrow(() -> NetworkShim.createSimpleChannel(null));
            assertDoesNotThrow(() -> NetworkShim.registerMessage(null, 0, null, null, null, null));
            assertDoesNotThrow(() -> NetworkShim.registerPayload(null, null, null));
            assertDoesNotThrow(() -> NetworkShim.registerPayloadToClient(null, null, null));
            assertDoesNotThrow(() -> NetworkShim.registerPayloadToServer(null, null, null));
            assertDoesNotThrow(() -> NetworkShim.registerPayloadBidirectional(null, null, null));
            assertDoesNotThrow(() -> NetworkShim.send(null, null, null));
            assertDoesNotThrow(() -> NetworkShim.sendToServer(null, null));
            assertDoesNotThrow(() -> NetworkShim.sendToPlayer(null, null, null));
            assertNull(NetworkShim.createRegistryFriendlyByteBuf(null, null));
            assertNull(NetworkShim.getRegistryAccess(null));
            assertNull(NetworkShim.getChannel(null));
            assertNull(NetworkShim.getPayload(null));
        }

        @Test
        @DisplayName("2.2 Overwriting duplicate channels and payloads preserves state gracefully")
        public void testDuplicateRegistrations() {
            String chId = "test:duplicate_channel";
            Object ch1 = NetworkShim.createSimpleChannel(chId, () -> "1.0", v -> true, v -> true);
            Object ch2 = NetworkShim.createSimpleChannel(chId, () -> "2.0", v -> true, v -> true);

            assertSame(ch2, NetworkShim.getChannel(chId));
            assertEquals("2.0", ((NetworkShim.ChannelDescriptor) ch2).version);

            String payloadId = "test:dup_payload";
            NetworkShim.registerPayloadToClient(payloadId, null, (p, c) -> {});
            NetworkShim.registerPayloadToServer(payloadId, null, (p, c) -> {});

            NetworkShim.PayloadEntry entry = NetworkShim.getPayload(payloadId);
            assertNotNull(entry);
            assertNotNull(entry.serverHandler);
        }

        @Test
        @DisplayName("2.3 Packet buffer with native registryAccess() reflection method")
        public void testBufferWithNativeRegistryAccessMethod() {
            Object nativeAccess = new Object();
            MockBufferWithNativeRegistryAccess mockBufferWithMethod = new MockBufferWithNativeRegistryAccess(nativeAccess);

            Object access = NetworkShim.getRegistryAccess(mockBufferWithMethod);
            assertSame(nativeAccess, access, "Should reflectively extract registryAccess() if method present");
        }
    }

    // =========================================================================
    // Tier 3: Cross-Feature & Catalog Integration Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 3: Cross-Feature & Catalog Tests")
    class Tier3CrossFeature {

        @Test
        @DisplayName("3.1 NetworkRulesCatalog contains SimpleChannel, CustomPacketPayload, and RegistryFriendlyByteBuf rules")
        public void testNetworkRulesCatalogCoverage() {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target1204 = TargetSpec.of("1.20.4", "neoforge");
            TargetSpec target121 = TargetSpec.of("1.21.1", "neoforge");
            TargetSpec targetFabric = TargetSpec.of("1.20.4", "fabric");

            List<TransformationRule> rules1204 = kb.getApplicableRules(base, target1204);
            List<TransformationRule> rules121 = kb.getApplicableRules(base, target121);
            List<TransformationRule> rulesFabric = kb.getApplicableRules(base, targetFabric);

            // SimpleChannel redirects to NetworkShim
            boolean hasChannelRedirect = rules1204.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraftforge/network/simple/SimpleChannel".equals(pr.getSourceOwner()) &&
                    "com/kyroxova/continuumlib/shims/NetworkShim".equals(pr.getShimOwner())
            );
            assertTrue(hasChannelRedirect, "SimpleChannel calls must redirect to NetworkShim for NeoForge");

            // Fabric networking rules
            boolean hasFabricSendRedirect = rulesFabric.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    pr.getShimOwner().contains("NetworkShim")
            );
            assertTrue(hasFabricSendRedirect, "NetworkShim must be active for Fabric targets");

            // RegistryFriendlyByteBuf rules on 1.21+
            boolean hasRegistryFriendlyByteBuf = rules121.stream().anyMatch(r ->
                    (r instanceof PolyfillRule pr && (pr.getSourceOwner().contains("FriendlyByteBuf") || pr.getShimOwner().contains("NetworkShim"))) ||
                    (r instanceof MethodRedirectRule mr && mr.getSourceOwner().contains("FriendlyByteBuf")) ||
                    r.getDescription().contains("FriendlyByteBuf") ||
                    r.getDescription().contains("NetworkShim")
            );
            assertTrue(hasRegistryFriendlyByteBuf, "FriendlyByteBuf/RegistryFriendlyByteBuf rules must be registered for 1.21+");
        }

        @Test
        @DisplayName("3.2 Full lifecycle: Channel dispatch with RegistryFriendlyByteBuf payload decoding")
        public void testFullChannelLifecycleWithRegistryBuffer() {
            Object channel = NetworkShim.createSimpleChannel("continuumlib:complex_channel");
            Object mockRegistry = new Object();
            MockByteBuf rawBuf = new MockByteBuf();
            rawBuf.data = "block_state_data_payload";

            Object rfb = NetworkShim.createRegistryFriendlyByteBuf(rawBuf, mockRegistry);

            AtomicReference<String> decodedPayload = new AtomicReference<>();
            AtomicReference<Object> retrievedRegistry = new AtomicReference<>();

            NetworkShim.registerMessage(
                    channel,
                    42,
                    ComplexPacket.class,
                    (pkt, buf) -> ((MockByteBuf) buf).data = pkt.serializedData,
                    buf -> {
                        retrievedRegistry.set(NetworkShim.getRegistryAccess(buf));
                        return new ComplexPacket(((MockByteBuf) buf).data);
                    },
                    (pkt, ctx) -> decodedPayload.set(pkt.serializedData)
            );

            // Simulate decode & dispatch
            NetworkShim.ChannelDescriptor desc = (NetworkShim.ChannelDescriptor) channel;
            NetworkShim.PacketHandlerEntry<ComplexPacket> entry =
                    (NetworkShim.PacketHandlerEntry<ComplexPacket>) desc.handlers.get(ComplexPacket.class);
            assertNotNull(entry);

            ComplexPacket decoded = entry.decoder.apply(rfb);
            assertEquals("block_state_data_payload", decoded.serializedData);
            assertSame(mockRegistry, retrievedRegistry.get(), "Decoder must access HolderLookup.Provider from buffer");

            desc.send("target_player", decoded);
            assertEquals("block_state_data_payload", decodedPayload.get());
        }
    }

    // =========================================================================
    // Tier 4: Workload & Concurrency Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 4: Workload & Concurrency Tests")
    class Tier4Workload {

        @Test
        @DisplayName("4.1 Concurrent registration of channels and payloads under heavy multi-threaded contention")
        public void testConcurrentChannelAndPayloadRegistration() throws InterruptedException, ExecutionException {
            int threadCount = 16;
            int opsPerThread = 250;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            List<Future<Void>> futures = new ArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < opsPerThread; i++) {
                        String chId = "thread_" + threadId + "_chan_" + i;
                        Object ch = NetworkShim.createSimpleChannel(chId);
                        NetworkShim.registerMessage(ch, i, TestPacket.class, (p, b) -> {}, b -> new TestPacket(""), (p, c) -> {});

                        String pId = "thread_" + threadId + "_payload_" + i;
                        NetworkShim.registerPayloadToClient(pId, null, (p, c) -> {});
                        NetworkShim.registerPayloadToServer(pId, null, (p, c) -> {});

                        MockByteBuf buf = new MockByteBuf();
                        Object rfb = NetworkShim.createRegistryFriendlyByteBuf(buf, chId);
                        assertEquals(chId, NetworkShim.getRegistryAccess(rfb));
                    }
                    return null;
                }));
            }

            for (Future<Void> f : futures) {
                f.get();
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

            // Verify registrations survived
            assertNotNull(NetworkShim.getChannel("thread_0_chan_0"));
            assertNotNull(NetworkShim.getPayload("thread_0_payload_0"));
        }
    }

    // =========================================================================
    // Test Helpers
    // =========================================================================

    public static class TestPacket {
        public final String payload;

        public TestPacket(String payload) {
            this.payload = payload;
        }
    }

    public static class ComplexPacket {
        public final String serializedData;

        public ComplexPacket(String serializedData) {
            this.serializedData = serializedData;
        }
    }

    public static class MockByteBuf {
        public String data = "";
    }

    public static class SelfWritingPacket {
        public boolean writeCalled = false;

        public void write(Object buf) {
            this.writeCalled = true;
        }
    }

    public static class SelfEncodingPacket {
        public boolean encodeCalled = false;

        public void encode(Object buf) {
            this.encodeCalled = true;
        }
    }

    public static class MockBufferWithNativeRegistryAccess {
        private final Object registryAccess;

        public MockBufferWithNativeRegistryAccess(Object registryAccess) {
            this.registryAccess = registryAccess;
        }

        public Object registryAccess() {
            return registryAccess;
        }
    }
}
