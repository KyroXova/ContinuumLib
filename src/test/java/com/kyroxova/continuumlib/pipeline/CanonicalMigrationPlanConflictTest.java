package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.continuumlib.pipeline.migration.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalMigrationPlanConflictTest {
    @Test
    void rejectsConflictingClassRenameAliases() {
        CanonicalMigrationRule first = CanonicalMigrationRule.builder()
                .type(MigrationType.CLASS_RENAME)
                .sourceOwner("example/Outer$Inner")
                .targetOwner("target/First")
                .build();
        CanonicalMigrationRule second = CanonicalMigrationRule.builder()
                .type(MigrationType.CLASS_RENAME)
                .sourceOwner("example.Outer.Inner")
                .targetOwner("target/Second")
                .build();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new CanonicalMigrationPlan(List.of(first, second))
        );
        assertTrue(failure.getMessage().contains("Conflicting canonical migration rules"));
    }

    @Test
    void rejectsConflictingExactMethodTargets() {
        CanonicalMigrationRule first = methodRename("newOne");
        CanonicalMigrationRule second = methodRename("newTwo");

        assertThrows(IllegalArgumentException.class,
                () -> new CanonicalMigrationPlan(List.of(first, second)));
    }

    @Test
    void rejectsWildcardRuleThatConflictsWithExactOverload() {
        CanonicalMigrationRule wildcard = CanonicalMigrationRule.builder()
                .type(MigrationType.MEMBER_RENAME)
                .sourceOwner("example/Api")
                .sourceName("old")
                .targetName("all")
                .build();
        CanonicalMigrationRule exact = methodRename("specific");

        assertThrows(IllegalArgumentException.class,
                () -> new CanonicalMigrationPlan(List.of(wildcard, exact)));
    }

    @Test
    void rejectsConflictingConstructorFactories() {
        CanonicalMigrationRule first = CanonicalMigrationRule.builder()
                .type(MigrationType.CONSTRUCTOR_TO_FACTORY)
                .sourceOwner("example/Api")
                .sourceDescriptor("(I)V")
                .targetOwner("example/Api")
                .targetName("create")
                .targetDescriptor("(I)Lexample/Api;")
                .build();
        CanonicalMigrationRule second = CanonicalMigrationRule.builder()
                .type(MigrationType.CONSTRUCTOR_TO_FACTORY)
                .sourceOwner("example/Api")
                .sourceDescriptor("(I)V")
                .targetOwner("example/Factories")
                .targetName("make")
                .targetDescriptor("(I)Lexample/Api;")
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> new CanonicalMigrationPlan(List.of(first, second)));
    }

    @Test
    void rejectsConflictingCallBridgesForSameOpcode() {
        CanonicalMigrationRule first = bridge("first", Opcodes.INVOKEVIRTUAL);
        CanonicalMigrationRule second = bridge("second", Opcodes.INVOKEVIRTUAL);

        assertThrows(IllegalArgumentException.class,
                () -> new CanonicalMigrationPlan(List.of(first, second)));
    }

    @Test
    void allowsSameSourceBridgeForDifferentOpcodes() {
        CanonicalMigrationRule virtual = bridge("bridge", Opcodes.INVOKEVIRTUAL);
        CanonicalMigrationRule interfaceCall = bridge("bridge", Opcodes.INVOKEINTERFACE);

        assertDoesNotThrow(() ->
                new CanonicalMigrationPlan(List.of(virtual, interfaceCall)));
    }

    private static CanonicalMigrationRule methodRename(String targetName) {
        return CanonicalMigrationRule.builder()
                .type(MigrationType.MEMBER_RENAME)
                .sourceOwner("example/Api")
                .sourceName("old")
                .sourceDescriptor("(I)I")
                .targetOwner("example/Api")
                .targetName(targetName)
                .targetDescriptor("(I)I")
                .build();
    }

    private static CanonicalMigrationRule bridge(String targetName, int opcode) {
        return new CanonicalMigrationRule(
                "test",
                MigrationType.CALL_BRIDGE,
                "example/Api",
                "old",
                "(I)I",
                "example/Hooks",
                targetName,
                "(Lexample/Api;I)I",
                opcode,
                null,
                null,
                MigrationLayer.BYTECODE,
                "test"
        );
    }
}
