package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.mappings.MappingFormat;
import com.kyroxova.continuumlib.knowledgebase.mappings.MappingTranslationTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Verification Suite for Universal Mappings:
 * Tests MappingFormat resolution, detection heuristics, MappingTranslationTable bidirectional translation,
 * owner-scoped method/field translations, bytecode descriptor rewriting, ApiKnowledgeBase integration,
 * and multi-threaded concurrency.
 */
public class MappingTranslationVerificationTest {

    // =========================================================================
    // Tier 1: Isolation Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 1: Isolation Tests")
    class Tier1Isolation {

        @Test
        @DisplayName("1.1 MappingFormat resolution, normalization, and TargetSpec extraction")
        public void testMappingFormatResolution() {
            // Standard IDs
            assertEquals(MappingFormat.MOJMAP, MappingFormat.fromString("mojmap"));
            assertEquals(MappingFormat.INTERMEDIARY, MappingFormat.fromString("intermediary"));
            assertEquals(MappingFormat.SRG, MappingFormat.fromString("srg"));
            assertEquals(MappingFormat.YARN, MappingFormat.fromString("yarn"));

            // Case-insensitivity & aliases
            assertEquals(MappingFormat.MOJMAP, MappingFormat.fromString("MOJANG"));
            assertEquals(MappingFormat.MOJMAP, MappingFormat.fromString("official"));
            assertEquals(MappingFormat.MOJMAP, MappingFormat.fromString("moj"));
            assertEquals(MappingFormat.INTERMEDIARY, MappingFormat.fromString("Fabric"));
            assertEquals(MappingFormat.INTERMEDIARY, MappingFormat.fromString("inter"));
            assertEquals(MappingFormat.SRG, MappingFormat.fromString("Searge"));
            assertEquals(MappingFormat.SRG, MappingFormat.fromString("mcp"));
            assertEquals(MappingFormat.SRG, MappingFormat.fromString("FORGE"));
            assertEquals(MappingFormat.YARN, MappingFormat.fromString("Quilt"));

            // Null & empty fallbacks
            assertEquals(MappingFormat.MOJMAP, MappingFormat.fromString(null));
            assertEquals(MappingFormat.MOJMAP, MappingFormat.fromString(""));
            assertEquals(MappingFormat.MOJMAP, MappingFormat.fromString("   "));
            assertEquals(MappingFormat.SRG, MappingFormat.fromString("unknown_xyz", MappingFormat.SRG));

            // TargetSpec extraction
            TargetSpec mojSpec = new TargetSpec(MCVersion.of("1.18.2"), LoaderType.FORGE, "mojmap", 0);
            assertEquals(MappingFormat.MOJMAP, MappingFormat.fromTargetSpec(mojSpec));

            TargetSpec fabSpec = new TargetSpec(MCVersion.of("1.20.1"), LoaderType.FABRIC, "intermediary", 0);
            assertEquals(MappingFormat.INTERMEDIARY, MappingFormat.fromTargetSpec(fabSpec));

            TargetSpec nullSpec = null;
            assertEquals(MappingFormat.MOJMAP, MappingFormat.fromTargetSpec(nullSpec));

            // Properties
            assertTrue(MappingFormat.MOJMAP.isDeobfuscated());
            assertTrue(MappingFormat.MOJMAP.isNamed());
            assertFalse(MappingFormat.INTERMEDIARY.isDeobfuscated());
            assertFalse(MappingFormat.SRG.isDeobfuscated());
            assertTrue(MappingFormat.YARN.isDeobfuscated());
        }

        @Test
        @DisplayName("1.2 MappingFormat heuristic detection from class, method, and member names")
        public void testMappingFormatDetection() {
            // Intermediary detection
            assertEquals(MappingFormat.INTERMEDIARY, MappingFormat.detectFromIdentifier("net/minecraft/class_2248"));
            assertEquals(MappingFormat.INTERMEDIARY, MappingFormat.detectFromIdentifier("net.minecraft.class_1792"));
            assertEquals(MappingFormat.INTERMEDIARY, MappingFormat.detectFromIdentifier("method_9567"));
            assertEquals(MappingFormat.INTERMEDIARY, MappingFormat.detectFromIdentifier("field_8901"));

            // SRG detection
            assertEquals(MappingFormat.SRG, MappingFormat.detectFromIdentifier("func_150787_b"));
            assertEquals(MappingFormat.SRG, MappingFormat.detectFromIdentifier("field_150914_a"));
            assertEquals(MappingFormat.SRG, MappingFormat.detectFromIdentifier("net/minecraft/src/C_1234_"));
            assertEquals(MappingFormat.SRG, MappingFormat.detectFromIdentifier("p_12345_"));
            assertEquals(MappingFormat.SRG, MappingFormat.detectFromIdentifier("m_12345_"));
            assertEquals(MappingFormat.SRG, MappingFormat.detectFromIdentifier("f_12345_"));

            // Mojmap default
            assertEquals(MappingFormat.MOJMAP, MappingFormat.detectFromIdentifier("net/minecraft/world/level/block/Block"));
            assertEquals(MappingFormat.MOJMAP, MappingFormat.detectFromIdentifier("isClientSide"));
            assertEquals(MappingFormat.MOJMAP, MappingFormat.detectFromIdentifier(null));

            // Member-specific & Class-specific
            assertEquals(MappingFormat.INTERMEDIARY, MappingFormat.detectFromClassName("net/minecraft/class_2248"));
            assertEquals(MappingFormat.SRG, MappingFormat.detectFromClassName("net/minecraft/src/C_123_"));
            assertEquals(MappingFormat.MOJMAP, MappingFormat.detectFromClassName("net/minecraft/world/item/Item"));

            assertEquals(MappingFormat.INTERMEDIARY, MappingFormat.detectFromMemberName("method_1234"));
            assertEquals(MappingFormat.SRG, MappingFormat.detectFromMemberName("func_76543_a"));
            assertEquals(MappingFormat.MOJMAP, MappingFormat.detectFromMemberName("getDescriptionId"));
        }

        @Test
        @DisplayName("1.3 MappingTranslationTable default class mappings and bidirectional translation")
        public void testDefaultClassMappings() {
            MappingTranslationTable table = MappingTranslationTable.createDefault();
            assertTrue(table.getRegisteredClassCount() >= 10);

            // Block translation
            String mojBlock = "net/minecraft/world/level/block/Block";
            String interBlock = "net/minecraft/class_2248";
            String srgBlock = "net/minecraft/block/Block";

            assertEquals(interBlock, table.translateClass(mojBlock, MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
            assertEquals(srgBlock, table.translateClass(mojBlock, MappingFormat.MOJMAP, MappingFormat.SRG));
            assertEquals(mojBlock, table.translateClass(interBlock, MappingFormat.INTERMEDIARY, MappingFormat.MOJMAP));
            assertEquals(mojBlock, table.translateClass(srgBlock, MappingFormat.SRG, MappingFormat.MOJMAP));
            assertEquals(interBlock, table.translateClass(srgBlock, MappingFormat.SRG, MappingFormat.INTERMEDIARY));

            // Dot notation preservation
            String dotMoj = "net.minecraft.world.level.block.Block";
            String dotInter = "net.minecraft.class_2248";
            assertEquals(dotInter, table.translateClass(dotMoj, MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
            assertEquals(dotMoj, table.translateClass(dotInter, MappingFormat.INTERMEDIARY, MappingFormat.MOJMAP));

            // Level / World
            String mojLevel = "net/minecraft/world/level/Level";
            String interWorld = "net/minecraft/class_1937";
            assertEquals(interWorld, table.translateClass(mojLevel, MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
            assertEquals(mojLevel, table.translateClass(interWorld, MappingFormat.INTERMEDIARY, MappingFormat.MOJMAP));

            // Unknown class preserved
            String unknown = "com/example/mod/MyCustomClass";
            assertEquals(unknown, table.translateClass(unknown, MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
            assertEquals(unknown, table.translateClass(unknown, MappingFormat.INTERMEDIARY, MappingFormat.MOJMAP));

            // Identity when from == to
            assertEquals(mojBlock, table.translateClass(mojBlock, MappingFormat.MOJMAP, MappingFormat.MOJMAP));
        }

        @Test
        @DisplayName("1.4 Method and Field translations with owner-scoping and unqualified fallback")
        public void testMethodAndFieldTranslations() {
            MappingTranslationTable table = MappingTranslationTable.createDefault();

            // Level#isClientSide -> class_1937#method_8608 / func_201670_d
            String mojMethod = "isClientSide";
            String interMethod = "method_8608";
            String srgMethod = "func_201670_d";

            assertEquals(interMethod, table.translateMethod("net/minecraft/world/level/Level", mojMethod, "()Z", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
            assertEquals(srgMethod, table.translateMethod("net/minecraft/world/level/Level", mojMethod, "()Z", MappingFormat.MOJMAP, MappingFormat.SRG));
            assertEquals(mojMethod, table.translateMethod("net/minecraft/class_1937", interMethod, "()Z", MappingFormat.INTERMEDIARY, MappingFormat.MOJMAP));

            // Unqualified lookup
            assertEquals(interMethod, table.translateMethod(mojMethod, MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
            assertEquals(mojMethod, table.translateMethod(interMethod, MappingFormat.INTERMEDIARY, MappingFormat.MOJMAP));

            // Field translation: Level#isClientSide field
            assertEquals("field_9236", table.translateField("net/minecraft/world/level/Level", "isClientSide", "Z", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
            assertEquals("field_72995_K", table.translateField("net/minecraft/world/level/Level", "isClientSide", "Z", MappingFormat.MOJMAP, MappingFormat.SRG));

            // Unknown method and field preserved
            assertEquals("myCustomMethod", table.translateMethod("myCustomMethod", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
            assertEquals("myCustomField", table.translateField("myCustomField", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
        }

        @Test
        @DisplayName("1.5 Bytecode Descriptor Translation across MappingFormats")
        public void testDescriptorTranslation() {
            MappingTranslationTable table = MappingTranslationTable.createDefault();

            // Simple descriptor: (Lnet/minecraft/world/level/block/Block;)V
            String mojDesc1 = "(Lnet/minecraft/world/level/block/Block;)V";
            String expectedInter1 = "(Lnet/minecraft/class_2248;)V";
            assertEquals(expectedInter1, table.translateDescriptor(mojDesc1, MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
            assertEquals(mojDesc1, table.translateDescriptor(expectedInter1, MappingFormat.INTERMEDIARY, MappingFormat.MOJMAP));

            // Complex descriptor with multiple types, arrays, and return value
            // (Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;[Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/level/block/state/BlockState;
            String mojDesc2 = "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;[Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/level/block/state/BlockState;";
            String translatedInter2 = table.translateDescriptor(mojDesc2, MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY);

            assertTrue(translatedInter2.contains("Lnet/minecraft/class_1937;")); // Level
            assertTrue(translatedInter2.contains("Lnet/minecraft/class_2338;")); // BlockPos
            assertTrue(translatedInter2.contains("[Lnet/minecraft/class_1799;")); // ItemStack[]
            assertTrue(translatedInter2.contains("Lnet/minecraft/class_2680;")); // BlockState

            // Round trip back to Mojmap
            String roundTrip = table.translateDescriptor(translatedInter2, MappingFormat.INTERMEDIARY, MappingFormat.MOJMAP);
            assertEquals(mojDesc2, roundTrip);

            // Primitive / no objects descriptor unchanged
            assertEquals("(II)Z", table.translateDescriptor("(II)Z", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
            assertNull(table.translateDescriptor(null, MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
        }

        @Test
        @DisplayName("1.6 Custom Class, Method, and Field registration and alias binding")
        public void testCustomRegistrations() {
            MappingTranslationTable table = new MappingTranslationTable();

            // Register custom mod class
            table.registerClass("com/mod/block/GeneratorBlock", "com/mod/class_100", "com/mod/block/BlockGen", "com/mod/block/GeneratorBlock");
            table.registerClassAlias(MappingFormat.MOJMAP, "com/mod/block/GeneratorBlock", "com/mod/block/OldGeneratorBlock");

            assertEquals("com/mod/class_100", table.translateClass("com/mod/block/GeneratorBlock", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
            assertEquals("com/mod/class_100", table.translateClass("com/mod/block/OldGeneratorBlock", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
            assertEquals("com/mod/block/GeneratorBlock", table.translateClass("com/mod/class_100", MappingFormat.INTERMEDIARY, MappingFormat.MOJMAP));

            // Register custom method
            table.registerMethod("com/mod/block/GeneratorBlock", "generateEnergy", "method_500", "func_5000_g", "generateEnergy");
            table.registerMethodAlias(MappingFormat.MOJMAP, "generateEnergy", "producePower");

            assertEquals("method_500", table.translateMethod("com/mod/block/GeneratorBlock", "generateEnergy", "()I", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
            assertEquals("method_500", table.translateMethod("producePower", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
            assertEquals("generateEnergy", table.translateMethod("method_500", MappingFormat.INTERMEDIARY, MappingFormat.MOJMAP));
        }
    }

    // =========================================================================
    // Tier 2: Rules Catalog & KnowledgeBase Integration Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 2: KnowledgeBase Integration Tests")
    class Tier2KnowledgeBaseIntegration {

        @Test
        @DisplayName("2.1 ApiKnowledgeBase mapping methods delegate to MappingTranslationTable")
        public void testApiKnowledgeBaseDelegation() {
            ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
            assertNotNull(kb.getMappingTranslationTable());

            // Class
            String interBlock = kb.translateClass("net/minecraft/world/level/block/Block", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY);
            assertEquals("net/minecraft/class_2248", interBlock);

            // Method
            String interMethod = kb.translateMethod("net/minecraft/world/level/Level", "isClientSide", "()Z", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY);
            assertEquals("method_8608", interMethod);

            // Field
            String interField = kb.translateField("net/minecraft/world/level/Level", "isClientSide", "Z", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY);
            assertEquals("field_9236", interField);

            // Descriptor
            String interDesc = kb.translateDescriptor("(Lnet/minecraft/world/level/block/Block;)V", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY);
            assertEquals("(Lnet/minecraft/class_2248;)V", interDesc);
        }

        @Test
        @DisplayName("2.2 ApiKnowledgeBase custom MappingTranslationTable constructor")
        public void testCustomTableInKnowledgeBase() {
            MappingTranslationTable customTable = new MappingTranslationTable();
            customTable.registerClass("com/custom/Foo", "com/custom/class_99", "com/custom/FooLegacy", "com/custom/Foo");

            ApiKnowledgeBase kb = new ApiKnowledgeBase(customTable);
            assertEquals(customTable, kb.getMappingTranslationTable());
            assertEquals("com/custom/class_99", kb.translateClass("com/custom/Foo", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
        }
    }

    // =========================================================================
    // Tier 3: Concurrency & Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 3: Concurrency & Stress Tests")
    class Tier3Concurrency {

        @Test
        @DisplayName("3.1 Highly concurrent translation and registration across multiple threads")
        public void testConcurrentTranslationAndRegistration() throws Exception {
            final MappingTranslationTable table = MappingTranslationTable.createDefault();
            final int threadCount = 16;
            final int operationsPerThread = 2000;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successCounter = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                pool.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        for (int i = 0; i < operationsPerThread; i++) {
                            // Reads
                            String interBlock = table.translateClass("net/minecraft/world/level/block/Block", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY);
                            assertEquals("net/minecraft/class_2248", interBlock);

                            String desc = table.translateDescriptor("(Lnet/minecraft/world/level/Level;)V", MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY);
                            assertTrue(desc.contains("class_1937"));

                            // Dynamic writes on distinct IDs
                            if (i % 20 == 0) {
                                String customMoj = "com/thread" + threadId + "/Class" + i;
                                String customInter = "com/thread" + threadId + "/class_" + i;
                                table.registerClass(customMoj, customInter, customMoj, customMoj);
                                assertEquals(customInter, table.translateClass(customMoj, MappingFormat.MOJMAP, MappingFormat.INTERMEDIARY));
                            }
                            successCounter.incrementAndGet();
                        }
                    } catch (Exception e) {
                        fail("Exception in thread " + threadId + ": " + e.getMessage());
                    }
                });
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
            assertEquals(threadCount * operationsPerThread, successCounter.get());
        }
    }
}
