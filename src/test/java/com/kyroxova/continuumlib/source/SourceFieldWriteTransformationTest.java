package com.kyroxova.continuumlib.source;

import com.kyroxova.continuumlib.bytecode.ClassInfo;
import com.kyroxova.continuumlib.model.diagnostic.Diagnostic;
import com.kyroxova.continuumlib.model.diagnostic.DiagnosticCode;
import com.kyroxova.continuumlib.pipeline.migration.*;
import com.kyroxova.continuumlib.source.ast.SourceParser;
import com.kyroxova.continuumlib.source.ast.SourceUnit;
import com.kyroxova.continuumlib.source.transform.SourceTransformer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SourceFieldWriteTransformationTest {
    @Test
    void simpleFieldAssignmentUsesExplicitPutBridge(@TempDir Path root) throws Exception {
        SourceUnit unit = source(root, "legacy.value = 3;");
        var diagnostics = new ArrayList<Diagnostic>();
        var applied = new ArrayList<AppliedMigration>();

        new SourceTransformer(
                new CanonicalMigrationPlan(List.of(putBridge())),
                fieldApi()
        ).transformAstWithAccounting(unit.ast(), unit.relativePath(), applied, diagnostics);

        String generated = unit.ast().toString();
        assertTrue(generated.contains("Hooks.set(legacy, 3)"), generated);
        assertFalse(generated.contains("legacy.value = 3"), generated);
        assertTrue(diagnostics.isEmpty(), diagnostics.toString());
        assertEquals(1, applied.size());
        assertEquals(MigrationLayer.SOURCE_AST, applied.get(0).layer());
    }

    @Test
    void compoundAssignmentFailsInsteadOfChangingReadModifyWriteSemantics(@TempDir Path root) throws Exception {
        SourceUnit unit = source(root, "legacy.value += 1;");
        var diagnostics = new ArrayList<Diagnostic>();

        new SourceTransformer(
                new CanonicalMigrationPlan(List.of(putBridge())),
                fieldApi()
        ).transformAstWithAccounting(unit.ast(), unit.relativePath(), new ArrayList<>(), diagnostics);

        String generated = unit.ast().toString();
        assertTrue(generated.contains("legacy.value += 1"), generated);
        assertEquals(1, diagnostics.size(), diagnostics.toString());
        assertEquals(DiagnosticCode.MIGRATION_UNRESOLVED, diagnostics.get(0).code());
    }

    @Test
    void incrementFailsInsteadOfRewritingFieldAsGetterCall(@TempDir Path root) throws Exception {
        SourceUnit unit = source(root, "legacy.value++;");
        var diagnostics = new ArrayList<Diagnostic>();

        new SourceTransformer(
                new CanonicalMigrationPlan(List.of(getBridge(), putBridge())),
                fieldApi()
        ).transformAstWithAccounting(unit.ast(), unit.relativePath(), new ArrayList<>(), diagnostics);

        String generated = unit.ast().toString();
        assertTrue(generated.contains("legacy.value++"), generated);
        assertFalse(generated.contains("Hooks.get(legacy)++"), generated);
        assertEquals(1, diagnostics.size(), diagnostics.toString());
        assertEquals(DiagnosticCode.MIGRATION_UNRESOLVED, diagnostics.get(0).code());
    }

    @Test
    void fieldToAccessorWriteFailsInsteadOfProducingInvalidGetterAssignment(@TempDir Path root) throws Exception {
        SourceUnit unit = source(root, "legacy.value = 3;");
        var diagnostics = new ArrayList<Diagnostic>();

        CanonicalMigrationRule accessor = CanonicalMigrationRule.builder()
                .rulePackId("accessor")
                .type(MigrationType.FIELD_TO_ACCESSOR)
                .sourceOwner("api/Legacy")
                .sourceName("value")
                .sourceDescriptor("I")
                .targetOwner("api/Legacy")
                .targetName("getValue")
                .targetDescriptor("()I")
                .layer(MigrationLayer.SOURCE_AST)
                .build();

        new SourceTransformer(
                new CanonicalMigrationPlan(List.of(accessor)),
                fieldApi()
        ).transformAstWithAccounting(unit.ast(), unit.relativePath(), new ArrayList<>(), diagnostics);

        String generated = unit.ast().toString();
        assertTrue(generated.contains("legacy.value = 3"), generated);
        assertFalse(generated.contains("getValue() = 3"), generated);
        assertEquals(1, diagnostics.size(), diagnostics.toString());
        assertEquals(DiagnosticCode.MIGRATION_UNRESOLVED, diagnostics.get(0).code());
    }

    @Test
    void exactFieldRenamePreservesIncrementSemantics(@TempDir Path root) throws Exception {
        SourceUnit unit = source(root, "legacy.value++;");
        var diagnostics = new ArrayList<Diagnostic>();

        CanonicalMigrationRule rename = CanonicalMigrationRule.builder()
                .rulePackId("rename")
                .type(MigrationType.FIELD_RENAME)
                .sourceOwner("api/Legacy")
                .sourceName("value")
                .sourceDescriptor("I")
                .targetOwner("api/Legacy")
                .targetName("newValue")
                .targetDescriptor("I")
                .layer(MigrationLayer.SOURCE_AST)
                .build();

        new SourceTransformer(
                new CanonicalMigrationPlan(List.of(rename)),
                fieldApi()
        ).transformAstWithAccounting(unit.ast(), unit.relativePath(), new ArrayList<>(), diagnostics);

        String generated = unit.ast().toString();
        assertTrue(generated.contains("legacy.newValue++"), generated);
        assertTrue(diagnostics.isEmpty(), diagnostics.toString());
    }

    private static SourceUnit source(Path root, String statement) throws Exception {
        Path legacy = root.resolve("api/Legacy.java");
        Path use = root.resolve("example/Use.java");
        Files.createDirectories(legacy.getParent());
        Files.createDirectories(use.getParent());

        Files.writeString(legacy, """
                package api;
                public class Legacy {
                    public int value;
                }
                """);
        Files.writeString(use, """
                package example;
                import api.Legacy;
                public class Use {
                    public void run(Legacy legacy) {
                        %s
                    }
                }
                """.formatted(statement));

        return new SourceParser(List.of(root), List.of())
                .parseDirectory(root)
                .stream()
                .filter(unit -> unit.relativePath().equals("example/Use.java"))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, ClassInfo> fieldApi() {
        return Map.of(
                "api/Legacy",
                new ClassInfo(
                        "api/Legacy",
                        "java/lang/Object",
                        List.of(),
                        Opcodes.ACC_PUBLIC,
                        List.of(new ClassInfo.Member("value", "I", Opcodes.ACC_PUBLIC, null)),
                        List.of()
                )
        );
    }

    private static CanonicalMigrationRule getBridge() {
        return bridge("get", "(Lapi/Legacy;)I", Opcodes.GETFIELD);
    }

    private static CanonicalMigrationRule putBridge() {
        return bridge("set", "(Lapi/Legacy;I)V", Opcodes.PUTFIELD);
    }

    private static CanonicalMigrationRule bridge(String name, String descriptor, int opcode) {
        return CanonicalMigrationRule.builder()
                .rulePackId("bridge")
                .type(MigrationType.CALL_BRIDGE)
                .sourceOwner("api/Legacy")
                .sourceName("value")
                .sourceDescriptor("I")
                .targetOwner("compat/Hooks")
                .targetName(name)
                .targetDescriptor(descriptor)
                .opcode(opcode)
                .layer(MigrationLayer.BYTECODE)
                .build();
    }
}
