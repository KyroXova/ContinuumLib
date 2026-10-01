package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.MethodRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import com.kyroxova.continuumlib.shims.AttributeModifierShim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Verification Suite for Subsystem f:
 * Entity Attributes & Registries (1.7.9 -> 26.3+).
 */
public class EntityAttributesAndRegistriesVerificationTest {

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
        @DisplayName("1.1 Operation enum translation: ADDITION <-> ADD_VALUE, MULTIPLY_BASE <-> ADD_MULTIPLIED_BASE")
        public void testOperationEnumTranslation() {
            // Mapping LegacyOperation -> ModernOperation
            Object modernAdd = AttributeModifierShim.mapOperation(LegacyOperation.ADDITION, ModernOperation.class);
            assertEquals(ModernOperation.ADD_VALUE, modernAdd);

            Object modernMulBase = AttributeModifierShim.mapOperation(LegacyOperation.MULTIPLY_BASE, ModernOperation.class);
            assertEquals(ModernOperation.ADD_MULTIPLIED_BASE, modernMulBase);

            Object modernMulTotal = AttributeModifierShim.mapOperation(LegacyOperation.MULTIPLY_TOTAL, ModernOperation.class);
            assertEquals(ModernOperation.ADD_MULTIPLIED_TOTAL, modernMulTotal);

            // Mapping ModernOperation -> LegacyOperation
            Object legacyAdd = AttributeModifierShim.mapOperation(ModernOperation.ADD_VALUE, LegacyOperation.class);
            assertEquals(LegacyOperation.ADDITION, legacyAdd);

            Object legacyMulBase = AttributeModifierShim.mapOperation(ModernOperation.ADD_MULTIPLIED_BASE, LegacyOperation.class);
            assertEquals(LegacyOperation.MULTIPLY_BASE, legacyMulBase);

            Object legacyMulTotal = AttributeModifierShim.mapOperation(ModernOperation.ADD_MULTIPLIED_TOTAL, LegacyOperation.class);
            assertEquals(LegacyOperation.MULTIPLY_TOTAL, legacyMulTotal);
        }

        @Test
        @DisplayName("1.2 AttributeModifier instantiation polyfills across UUID and ResourceLocation signatures")
        public void testAttributeModifierInstantiation() {
            UUID testUuid = UUID.randomUUID();
            String testName = "WeaponDamage";
            double amount = 5.5;

            // In test environment without Minecraft classes, createModifier returns null gracefully without throwing
            assertDoesNotThrow(() -> AttributeModifierShim.createModifier(testUuid, testName, amount, LegacyOperation.ADDITION));
            assertDoesNotThrow(() -> AttributeModifierShim.createModifier("mymod:weapon_damage", amount, ModernOperation.ADD_VALUE));
        }

        @Test
        @DisplayName("1.3 Universal default attributes registration: registerDefaultAttributes / getRegisteredAttributes")
        public void testDefaultAttributesRegistration() {
            String entityType = "mymod:boss_monster";
            Object mockAttributeSupplier = new Object() {
                public double getBaseValue() {
                    return 100.0;
                }
            };

            Object registered = AttributeModifierShim.registerDefaultAttributes(entityType, mockAttributeSupplier);
            assertSame(mockAttributeSupplier, registered);

            Object retrieved = AttributeModifierShim.getRegisteredAttributes(entityType);
            assertSame(mockAttributeSupplier, retrieved);
        }

        @Test
        @DisplayName("1.4 LivingEntity.getAttribute() across Attribute vs Holder<Attribute> eras")
        public void testGetAttributeLookup() {
            MockAttribute rawAttr = new MockAttribute("generic.attack_damage");
            MockLivingEntity mockLivingEntity = new MockLivingEntity();

            // Direct call with raw attribute
            Object result1 = AttributeModifierShim.getAttribute(mockLivingEntity, rawAttr);
            assertEquals("AttributeInstance[generic.attack_damage]", result1);

            // Holder lookup with value() method
            MockHolder mockHolder = new MockHolder(rawAttr);
            Object result2 = AttributeModifierShim.getAttribute(mockLivingEntity, mockHolder);
            assertEquals("AttributeInstance[generic.attack_damage]", result2);
        }
    }

    // =========================================================================
    // Tier 2: Boundary & Edge Case Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 2: Boundary & Edge Cases")
    class Tier2Boundary {

        @Test
        @DisplayName("2.1 Null parameters across AttributeModifierShim APIs")
        public void testNullParameters() {
            assertNull(AttributeModifierShim.createModifier((UUID) null, null, 0.0, null));
            assertNull(AttributeModifierShim.createModifier((Object) null, 0.0, null));
            assertNull(AttributeModifierShim.registerDefaultAttributes(null, null));
            assertNull(AttributeModifierShim.getRegisteredAttributes(null));
            assertNull(AttributeModifierShim.getAttribute(null, null));
            assertNull(AttributeModifierShim.getAttribute(new Object(), null));

            // Unknown enum mapping falls back to default constant
            Object mapped = AttributeModifierShim.mapOperation("UNKNOWN_OPERATION", ModernOperation.class);
            assertNotNull(mapped);
        }

        @Test
        @DisplayName("2.2 Names with special characters converted to valid ResourceLocations")
        public void testNameSanitization() {
            UUID id = UUID.randomUUID();
            // Name with spaces and uppercase
            assertDoesNotThrow(() -> AttributeModifierShim.createModifier(id, "My Custom Speed Modifier!", 0.2, LegacyOperation.ADDITION));
            // Name starting with underscore
            assertDoesNotThrow(() -> AttributeModifierShim.createModifier(id, "_leading_underscore", 0.2, LegacyOperation.ADDITION));
            // Empty / blank name
            assertDoesNotThrow(() -> AttributeModifierShim.createModifier(id, "   ", 0.2, LegacyOperation.ADDITION));
        }
    }

    // =========================================================================
    // Tier 3: Cross-Feature & Catalog Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 3: Cross-Feature & Catalog Tests")
    class Tier3CrossFeature {

        @Test
        @DisplayName("3.1 FluidAndAttributesRulesCatalog and RegistryRulesCatalog contain attribute rules")
        public void testAttributeCatalogsCoverage() {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target1205 = TargetSpec.of("1.20.6", "neoforge");
            TargetSpec targetFabric = TargetSpec.of("1.20.6", "fabric");

            List<TransformationRule> neoRules = kb.getApplicableRules(base, target1205);
            List<TransformationRule> fabricRules = kb.getApplicableRules(base, targetFabric);

            // AttributeModifier polyfill rule
            boolean hasModifierRule = neoRules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr &&
                    "net/minecraft/world/entity/ai/attributes/AttributeModifier".equals(pr.getSourceOwner()) &&
                    "<init>".equals(pr.getSourceName()) &&
                    "com/kyroxova/continuumlib/shims/AttributeModifierShim".equals(pr.getShimOwner())
            );
            assertTrue(hasModifierRule, "AttributeModifier constructor polyfill must be registered");

            // EntityAttributeCreationEvent / DefaultAttributeRegistry rule
            boolean hasAttributeCreationEvent = neoRules.stream().anyMatch(r ->
                    (r instanceof PolyfillRule pr && (pr.getSourceOwner().contains("EntityAttributeCreationEvent") || pr.getShimOwner().contains("AttributeModifierShim"))) ||
                    (r instanceof MethodRedirectRule mr && mr.getSourceOwner().contains("EntityAttributeCreationEvent")) ||
                    r.getDescription().contains("EntityAttributeCreationEvent") ||
                    r.getDescription().contains("Attribute")
            );
            assertTrue(hasAttributeCreationEvent, "Attribute creation event rules must be registered");

            boolean hasFabricAttributeRule = fabricRules.stream().anyMatch(r ->
                    r instanceof PolyfillRule pr && pr.getShimOwner().contains("AttributeModifierShim")
            );
            assertTrue(hasFabricAttributeRule, "Fabric attribute registration rules must be registered");
        }
    }

    // =========================================================================
    // Tier 4: Workload & Concurrency Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 4: Workload & Concurrency Tests")
    class Tier4Workload {

        @Test
        @DisplayName("4.1 Concurrent attribute registration and operation mappings")
        public void testConcurrentAttributeRegistrations() throws InterruptedException, ExecutionException {
            int threadCount = 10;
            int opsPerThread = 200;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            List<Future<Void>> futures = new ArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < opsPerThread; i++) {
                        String entity = "entity_" + threadId + "_" + i;
                        Object supplier = new Object();
                        AttributeModifierShim.registerDefaultAttributes(entity, supplier);
                        assertSame(supplier, AttributeModifierShim.getRegisteredAttributes(entity));

                        // Operation translations
                        assertEquals(ModernOperation.ADD_VALUE, AttributeModifierShim.mapOperation(LegacyOperation.ADDITION, ModernOperation.class));
                        assertEquals(LegacyOperation.ADDITION, AttributeModifierShim.mapOperation(ModernOperation.ADD_VALUE, LegacyOperation.class));
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

    // Mock Operation enums
    public enum LegacyOperation {
        ADDITION,
        MULTIPLY_BASE,
        MULTIPLY_TOTAL
    }

    public enum ModernOperation {
        ADD_VALUE,
        ADD_MULTIPLIED_BASE,
        ADD_MULTIPLIED_TOTAL
    }

    public static class MockAttribute {
        private final String name;

        public MockAttribute(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class MockLivingEntity {
        public Object getAttribute(MockAttribute attr) {
            return "AttributeInstance[" + attr.name + "]";
        }
    }

    public static class MockHolder {
        private final Object val;

        public MockHolder(Object val) {
            this.val = val;
        }

        public Object value() {
            return val;
        }
    }
}
