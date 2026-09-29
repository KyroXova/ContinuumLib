package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.BootstrapperConfig;
import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.builder.ManifestGenerator;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import com.kyroxova.continuumlib.shims.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification Suite for Wave 5:
 * AdvancementShim, ExplosionShim, and SoundPlaybackShim across 1.7.9 -> 26.3+.
 * Tests Advancement vs AdvancementHolder, Level.explode dispatch across interaction enums,
 * SoundSource vs SoundCategory adaptation, power calculations, null safety, and high concurrency.
 */
public class Wave5AdvancementsExplosionsSoundVerificationTest {

    // =========================================================================
    // Tier 1: Isolation Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 1: Isolation Tests")
    class Tier1Isolation {

        @Test
        @DisplayName("1.1 AdvancementShim: Advancement vs AdvancementHolder wrapping, unwrapping, and id extraction")
        public void testAdvancementShim() {
            Object rawAdvancement = new Object() {
                public String getId() {
                    return "continuum:root";
                }
            };

            // Unwrapping raw object returns self
            assertSame(rawAdvancement, AdvancementShim.unwrap(rawAdvancement));
            assertSame(rawAdvancement, AdvancementShim.unwrapHolder(rawAdvancement));
            assertEquals("continuum:root", AdvancementShim.getId(rawAdvancement));

            // Wrap in holder
            Object holder = AdvancementShim.wrapHolder("continuum:root", rawAdvancement);
            assertNotNull(holder);

            // Holder id extraction & unwrapping
            assertEquals("continuum:root", AdvancementShim.getId(holder));
            assertSame(rawAdvancement, AdvancementShim.unwrapHolder(holder));
            assertSame(rawAdvancement, AdvancementShim.unwrap(holder));

            // Custom modern record holder mock
            Object modernHolder = new Object() {
                public String id() { return "continuum:story/mine_stone"; }
                public Object value() { return "stone_advancement_data"; }
            };
            assertEquals("continuum:story/mine_stone", AdvancementShim.getId(modernHolder));
            assertEquals("stone_advancement_data", AdvancementShim.unwrapHolder(modernHolder));

            // Builder execution
            Object mockBuilder = new Object() {
                public Object build(Object id) {
                    return AdvancementShim.wrapHolder(id, "built_advancement");
                }
            };
            Object built = AdvancementShim.build(mockBuilder, "continuum:custom_build");
            assertNotNull(built);
            assertEquals("continuum:custom_build", AdvancementShim.getId(built));
            assertEquals("built_advancement", AdvancementShim.unwrap(built));

            // Null safety
            assertNull(AdvancementShim.unwrap(null));
            assertNull(AdvancementShim.unwrapHolder(null));
            assertNull(AdvancementShim.getId(null));
            assertNull(AdvancementShim.wrapHolder(null, rawAdvancement));
            assertNull(AdvancementShim.wrapHolder("id", null));
            assertNull(AdvancementShim.build(null, "id"));
        }

        @Test
        @DisplayName("1.2 ExplosionShim: Level.explode dispatch across interaction enums, power calculation, and null safety")
        public void testExplosionShim() {
            // Mock Level with modern 7-parameter explode
            Object mockLevel7 = new Object() {
                public Object explode(Object entity, double x, double y, double z, float power, boolean causesFire, Object interaction) {
                    return "ExplosionResult[power=" + power + ", fire=" + causesFire + ", mode=" + interaction + "]";
                }
            };

            // Modern enum interaction
            enum TestModernInteraction { NONE, BLOCK, MOB, TNT }
            Object resModern = ExplosionShim.explode(mockLevel7, null, 10.0, 64.0, -10.0, 4.0f, true, TestModernInteraction.TNT);
            assertNotNull(resModern);
            assertTrue(resModern.toString().contains("power=4.0"));
            assertTrue(resModern.toString().contains("fire=true"));
            assertTrue(resModern.toString().contains("TNT"));

            // Mock Level with legacy 6-parameter explode (Entity, x, y, z, power, BlockInteraction)
            Object mockLevel6 = new Object() {
                public Object explode(Object entity, double x, double y, double z, float power, Object blockInteraction) {
                    return "LegacyResult[power=" + power + ", mode=" + blockInteraction + "]";
                }
            };
            enum TestLegacyInteraction { NONE, BREAK, DESTROY }
            Object resLegacy = ExplosionShim.explode(mockLevel6, "creeper", 0.0, 70.0, 0.0, 3.0f, TestLegacyInteraction.BREAK);
            assertNotNull(resLegacy);
            assertTrue(resLegacy.toString().contains("power=3.0"));
            assertTrue(resLegacy.toString().contains("BREAK"));

            // Interaction mapping / resolution
            Object resolved = ExplosionShim.resolveInteractionForMethod(TestLegacyInteraction.class, "BLOCK");
            assertNotNull(resolved);

            // Power calculation
            assertEquals(6.0f, ExplosionShim.calculatePower(4.0f, 1.5f), 0.001f);
            assertEquals(0.0f, ExplosionShim.calculatePower(4.0f, -0.5f), 0.001f);

            Object mockCalc = new Object() {
                public float calculatePower(float base) {
                    return base * 2.0f;
                }
            };
            assertEquals(8.0f, ExplosionShim.calculatePower(4.0f, mockCalc, mockLevel7, "pos"), 0.001f);

            // Null safety
            assertNull(ExplosionShim.explode(null, null, 0, 0, 0, 4.0f, false, "BLOCK"));
            assertNull(ExplosionShim.explode(mockLevel7, null, 0, 0, 0, 0.0f, false, "BLOCK"));
            assertNull(ExplosionShim.explode(mockLevel7, null, 0, 0, 0, -1.0f, false, "BLOCK"));
        }

        @Test
        @DisplayName("1.3 SoundPlaybackShim: playSound across SoundSource and SoundCategory")
        public void testSoundPlaybackShim() {
            // Mock Level implementing playSound(Player, BlockPos, SoundEvent, SoundSource, float, float)
            enum MockSoundSource { MASTER, MUSIC, RECORDS, WEATHER, BLOCKS, HOSTILE, NEUTRAL, PLAYERS, AMBIENT, VOICE }
            Object mockLevel = new Object() {
                private String lastPlayed = null;

                public void playSound(Object player, Object pos, Object sound, MockSoundSource source, float vol, float pitch) {
                    this.lastPlayed = sound + "@" + source + ":" + vol + "/" + pitch;
                }

                public String getLastPlayed() {
                    return lastPlayed;
                }
            };

            // Legacy SoundCategory enum
            enum MockSoundCategory { MASTER, MUSIC, RECORDS, WEATHER, BLOCKS, HOSTILE, NEUTRAL, PLAYERS, AMBIENT, VOICE }

            // Play sound with SoundCategory enum - should adapt to MockSoundSource by name
            boolean played1 = SoundPlaybackShim.playSound(mockLevel, null, "pos", "minecraft:entity.generic.explode", MockSoundCategory.BLOCKS, 1.0f, 1.0f);
            assertTrue(played1, "playSound should succeed with adapted SoundCategory");

            // Category name resolution
            assertEquals("BLOCKS", SoundPlaybackShim.getCategoryName(MockSoundCategory.BLOCKS));
            assertEquals("MASTER", SoundPlaybackShim.getCategoryName(null));
            assertEquals("MUSIC", SoundPlaybackShim.getCategoryName("MUSIC"));

            // VirtualSoundSource fallback
            Object virtualSource = SoundPlaybackShim.resolveSoundSource("HOSTILE");
            assertNotNull(virtualSource);
            assertEquals("HOSTILE", SoundPlaybackShim.getCategoryName(virtualSource));

            // Null safety
            assertFalse(SoundPlaybackShim.playSound(null, null, "pos", "sound", MockSoundSource.MASTER, 1.0f, 1.0f));
            assertFalse(SoundPlaybackShim.playSound(mockLevel, null, "pos", null, MockSoundSource.MASTER, 1.0f, 1.0f));
        }

        @Test
        @DisplayName("1.4 CommandShim: sendSuccess / sendFailure across Supplier and Component, and sendMessage fallback")
        public void testCommandShim() {
            // Modern CommandSourceStack mock (takes Supplier<Component>, boolean)
            class ModernStack {
                String receivedMessage = null;
                boolean receivedLogging = false;

                public void sendSuccess(Supplier<?> supplier, boolean allowLogging) {
                    this.receivedMessage = String.valueOf(supplier.get());
                    this.receivedLogging = allowLogging;
                }
            }

            ModernStack modern = new ModernStack();
            // Test with plain string / component
            CommandShim.sendSuccess(modern, "Hello Modern", true);
            assertEquals("Hello Modern", modern.receivedMessage);
            assertTrue(modern.receivedLogging);

            // Test with Supplier
            CommandShim.sendSuccess(modern, (Supplier<String>) () -> "Hello from Supplier", false);
            assertEquals("Hello from Supplier", modern.receivedMessage);
            assertFalse(modern.receivedLogging);

            // Legacy CommandSourceStack mock (takes Object component, boolean)
            class LegacyStack {
                String receivedMessage = null;
                boolean receivedLogging = false;

                public void sendSuccess(Object component, boolean allowLogging) {
                    this.receivedMessage = String.valueOf(component);
                    this.receivedLogging = allowLogging;
                }
            }

            LegacyStack legacy = new LegacyStack();
            CommandShim.sendSuccess(legacy, "Hello Legacy", true);
            assertEquals("Hello Legacy", legacy.receivedMessage);
            assertTrue(legacy.receivedLogging);

            // Test passing Supplier to legacy stack
            CommandShim.sendSuccess(legacy, (Supplier<String>) () -> "Supplier to Legacy", false);
            assertEquals("Supplier to Legacy", legacy.receivedMessage);
            assertFalse(legacy.receivedLogging);

            // Very old fallback mock (takes sendMessage(Object))
            class SenderFallback {
                String message = null;

                public void sendMessage(Object msg) {
                    this.message = String.valueOf(msg);
                }
            }

            SenderFallback sender = new SenderFallback();
            CommandShim.sendSuccess(sender, "Fallback Success", true);
            assertEquals("Fallback Success", sender.message);

            CommandShim.sendFailure(sender, "Fallback Failure");
            assertEquals("Fallback Failure", sender.message);

            // Null safety
            CommandShim.sendSuccess(null, "Test", true);
            CommandShim.sendSuccess(modern, null, true);
            CommandShim.sendFailure(null, "Fail");
            CommandShim.sendFailure(modern, null);
        }

        @Test
        @DisplayName("1.5 EntityDataShim: defineId, ThreadLocal builder capturing, define, get, and set")
        public void testEntityDataShim() {
            // 1. defineId and createKey
            Object accessor = EntityDataShim.defineId(String.class, "DUMMY_SERIALIZER");
            assertNotNull(accessor);
            assertEquals(accessor, EntityDataShim.createKey(String.class, "DUMMY_SERIALIZER"));

            // 2. Mock SynchedEntityData
            class MockEntityData {
                final Map<Object, Object> store = new HashMap<>();

                public void define(Object acc, Object def) {
                    store.put(acc, def);
                }

                public Object get(Object acc) {
                    return store.get(acc);
                }

                public void set(Object acc, Object val) {
                    store.put(acc, val);
                }
            }

            MockEntityData data = new MockEntityData();
            EntityDataShim.define(data, accessor, "initial_value");
            assertEquals("initial_value", EntityDataShim.get(data, accessor));

            EntityDataShim.set(data, accessor, "updated_value");
            assertEquals("updated_value", EntityDataShim.get(data, accessor));

            // 3. Mock Entity wrapping entityData
            class MockEntity {
                private final MockEntityData entityData = new MockEntityData();

                public MockEntityData getEntityData() {
                    return entityData;
                }
            }

            MockEntity entity = new MockEntity();
            EntityDataShim.define(entity, accessor, 42);
            assertEquals(42, EntityDataShim.get(entity, accessor));
            EntityDataShim.set(entity, accessor, 100);
            assertEquals(100, EntityDataShim.get(entity, accessor));

            // 4. ThreadLocal Builder capturing (1.20.5+ / 26.3+)
            class MockBuilder {
                final Map<Object, Object> builderStore = new HashMap<>();

                public void define(Object acc, Object val) {
                    builderStore.put(acc, val);
                }
            }

            MockBuilder builder = new MockBuilder();
            EntityDataShim.captureBuilder(builder);
            assertSame(builder, EntityDataShim.getCurrentBuilder());

            // define should route to captured builder even when target is null
            EntityDataShim.define(null, accessor, "builder_val");
            assertEquals("builder_val", builder.builderStore.get(accessor));

            EntityDataShim.releaseBuilder();
            assertNull(EntityDataShim.getCurrentBuilder());

            // 5. Serializers and null safety
            assertNotNull(EntityDataShim.getSerializer("INT"));
            assertNotNull(EntityDataShim.getSerializer("STRING"));
            assertNull(EntityDataShim.getSerializer(null));

            EntityDataShim.define(null, null, null);
            assertNull(EntityDataShim.get(null, accessor));
            EntityDataShim.set(null, accessor, "val");
        }

        @Test
        @DisplayName("1.6 KnowledgeBase: Wave 5 Catalogs registration and active rule queries")
        public void testWave5KnowledgeBaseCatalogs() {
            ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();

            // 1. Advancement catalog rules: 1.20.1 -> 1.20.4
            TargetSpec spec1201 = TargetSpec.of("1.20.1", "forge");
            TargetSpec spec1204 = TargetSpec.of("1.20.4", "neoforge");
            List<TransformationRule> rules1204 = kb.getActiveRules(spec1201, spec1204);

            boolean hasAdvancementRule = rules1204.stream().anyMatch(r ->
                    r.getDescription().toLowerCase().contains("advancement"));
            assertTrue(hasAdvancementRule, "KB must contain active Advancement rules for 1.20.1 -> 1.20.4");

            // 2. Explosion and Sound catalog rules: 1.16.5 -> 1.20.4
            TargetSpec spec1165 = TargetSpec.of("1.16.5", "forge");
            List<TransformationRule> rules1165To1204 = kb.getActiveRules(spec1165, spec1204);

            boolean hasSoundRule = rules1165To1204.stream().anyMatch(r ->
                    r.getDescription().toLowerCase().contains("sound"));
            assertTrue(hasSoundRule, "KB must contain active Sound rules for 1.16.5 -> 1.20.4");

            boolean hasExplosionRule = rules1165To1204.stream().anyMatch(r ->
                    r.getDescription().toLowerCase().contains("explosion") ||
                    r.getDescription().toLowerCase().contains("explode"));
            assertTrue(hasExplosionRule, "KB must contain active Explosion rules for 1.16.5 -> 1.20.4");

            // 3. Command catalog rules: CommandSource <-> CommandSourceStack & sendSuccess
            boolean hasCommandRule = rules1165To1204.stream().anyMatch(r ->
                    r.getDescription().toLowerCase().contains("commandsource"));
            assertTrue(hasCommandRule, "KB must contain CommandSource redirect rules");

            // 4. Entity Synced Data rules: DataParameter / EntityDataAccessor
            boolean hasEntityDataRule = rules1165To1204.stream().anyMatch(r ->
                    r.getDescription().toLowerCase().contains("dataparameter") ||
                    r.getDescription().toLowerCase().contains("entitydataaccessor") ||
                    r.getDescription().toLowerCase().contains("synchedentitydata"));
            assertTrue(hasEntityDataRule, "KB must contain Entity Synced Data rules");
        }

        @Test
        @DisplayName("1.7 ManifestGenerator: Multi-target manifests and 26.3+ loaderVersion")
        public void testManifestGeneration() {
            BootstrapperConfig config = BootstrapperConfig.loadFromClasspath(getClass().getClassLoader());

            // 1. generateAllLoaderManifests for 1.20.4
            TargetSpec target1204 = TargetSpec.of("1.20.4", "neoforge");
            Map<String, String> allManifests1204 = ManifestGenerator.generateAllLoaderManifests(config, target1204);
            assertEquals(5, allManifests1204.size());
            assertTrue(allManifests1204.containsKey("mcmod.info"));
            assertTrue(allManifests1204.containsKey("META-INF/mods.toml"));
            assertTrue(allManifests1204.containsKey("META-INF/neoforge.mods.toml"));
            assertTrue(allManifests1204.containsKey("fabric.mod.json"));
            assertTrue(allManifests1204.containsKey("quilt.mod.json"));

            assertTrue(allManifests1204.get("META-INF/neoforge.mods.toml").contains("loaderVersion=\"[20.4,)\""));

            // 2. 26.3+ target manifest
            TargetSpec target263 = TargetSpec.of("26.3", "neoforge");
            Map<String, String> allManifests263 = ManifestGenerator.generateAllLoaderManifests(config, target263);
            assertTrue(allManifests263.get("META-INF/neoforge.mods.toml").contains("loaderVersion=\"[26.3,)\""));
            assertTrue(allManifests263.get("META-INF/neoforge.mods.toml").contains("version=\"26.3\""));
        }
    }

    // =========================================================================
    // Tier 2: Concurrency & Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 2: Concurrency & Stress Tests")
    class Tier2Concurrency {

        @Test
        @DisplayName("2.1 High-throughput concurrent execution across Advancement, Explosion, Sound, Command, and EntityData shims")
        public void testConcurrentWave5Shims() throws InterruptedException {
            int threadCount = 16;
            int opsPerThread = 250;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger verifiedOps = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < opsPerThread; i++) {
                            // 1. AdvancementShim operations
                            String id = "advancement_" + threadId + "_" + i;
                            Object rawAdv = new Object() {
                                public String getId() { return id; }
                            };
                            Object holder = AdvancementShim.wrapHolder(id, rawAdv);
                            if (Objects.equals(id, AdvancementShim.getId(holder)) && AdvancementShim.unwrap(holder) == rawAdv) {
                                verifiedOps.incrementAndGet();
                            }

                            // 2. ExplosionShim operations
                            float power = ExplosionShim.calculatePower(2.0f, 1.5f);
                            if (power == 3.0f) {
                                verifiedOps.incrementAndGet();
                            }

                            // 3. SoundPlaybackShim operations
                            Object source = SoundPlaybackShim.resolveSoundSource("BLOCKS");
                            if ("BLOCKS".equals(SoundPlaybackShim.getCategoryName(source))) {
                                verifiedOps.incrementAndGet();
                            }

                            // 4. CommandShim operations
                            final AtomicInteger called = new AtomicInteger(0);
                            Object mockSource = new Object() {
                                public void sendSuccess(Supplier<?> s, boolean log) {
                                    if (s != null && s.get() != null) called.incrementAndGet();
                                }
                            };
                            CommandShim.sendSuccess(mockSource, "msg", true);
                            if (called.get() == 1) {
                                verifiedOps.incrementAndGet();
                            }

                            // 5. EntityDataShim operations
                            Object accessor = EntityDataShim.defineId(String.class, "INT");
                            if (accessor != null) {
                                verifiedOps.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent Wave 5 shim operations timed out");
            executor.shutdown();

            assertEquals(threadCount * opsPerThread * 5, verifiedOps.get());
        }
    }
}
