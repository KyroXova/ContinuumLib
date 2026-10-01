package com.kyroxova.continuumlib.source;

import com.kyroxova.continuumlib.bytecode.ClassInfo;
import com.kyroxova.continuumlib.model.diagnostic.Diagnostic;
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

import static org.junit.jupiter.api.Assertions.*;

class SourceReferenceTransformationTest {
    @Test
    void exactStaticImportCallBecomesScopedTargetCall(@TempDir Path root) throws Exception {
        writeLegacy(root, true);
        Path use = root.resolve("example/Use.java");
        Files.createDirectories(use.getParent());
        Files.writeString(use, """
                package example;
                import static api.Legacy.oldCall;
                public class Use {
                    public int run() { return oldCall(4); }
                }
                """);

        SourceUnit unit = parse(root, "example/Use.java");
        CanonicalMigrationRule rule = methodRename();
        var applied = new ArrayList<AppliedMigration>();
        var diagnostics = new ArrayList<Diagnostic>();

        new SourceTransformer(new CanonicalMigrationPlan(List.of(rule)), methodApi(true))
                .transformAstWithAccounting(unit.ast(), unit.relativePath(), applied, diagnostics);

        String generated = unit.ast().toString();
        assertFalse(generated.contains("import static api.Legacy.oldCall"));
        assertTrue(generated.contains("import api.Target;"), generated);
        assertTrue(generated.contains("Target.newCall(4)"), generated);
        assertTrue(diagnostics.isEmpty(), diagnostics.toString());
        assertEquals(1, applied.stream().filter(m -> m.type() == MigrationType.MEMBER_RENAME).count());
    }

    @Test
    void exactStaticMethodReferenceMovesToTargetOwner(@TempDir Path root) throws Exception {
        writeLegacy(root, true);
        Path use = root.resolve("example/Use.java");
        Files.createDirectories(use.getParent());
        Files.writeString(use, """
                package example;
                import api.Legacy;
                import java.util.function.IntUnaryOperator;
                public class Use {
                    public IntUnaryOperator operation() { return Legacy::oldCall; }
                }
                """);

        SourceUnit unit = parse(root, "example/Use.java");
        var diagnostics = new ArrayList<Diagnostic>();
        new SourceTransformer(new CanonicalMigrationPlan(List.of(methodRename())), methodApi(true))
                .transformAstWithAccounting(unit.ast(), unit.relativePath(), new ArrayList<>(), diagnostics);

        String generated = unit.ast().toString();
        assertTrue(generated.contains("Target::newCall"), generated);
        assertTrue(generated.contains("import api.Target;"), generated);
        assertTrue(diagnostics.isEmpty(), diagnostics.toString());
    }

    @Test
    void boundInstanceBridgeBecomesLambdaWithoutReevaluatingReceiver(@TempDir Path root) throws Exception {
        writeLegacy(root, false);
        Path use = root.resolve("example/Use.java");
        Files.createDirectories(use.getParent());
        Files.writeString(use, """
                package example;
                import api.Legacy;
                import java.util.function.IntUnaryOperator;
                public class Use {
                    public IntUnaryOperator operation(Legacy legacy) { return legacy::oldCall; }
                }
                """);

        CanonicalMigrationRule bridge = CanonicalMigrationRule.builder()
                .rulePackId("bridge")
                .type(MigrationType.CALL_BRIDGE)
                .sourceOwner("api/Legacy")
                .sourceName("oldCall")
                .sourceDescriptor("(I)I")
                .targetOwner("compat/Hooks")
                .targetName("bridge")
                .targetDescriptor("(Lapi/Legacy;I)I")
                .opcode(Opcodes.INVOKEVIRTUAL)
                .layer(MigrationLayer.BYTECODE)
                .build();

        SourceUnit unit = parse(root, "example/Use.java");
        var applied = new ArrayList<AppliedMigration>();
        var diagnostics = new ArrayList<Diagnostic>();
        new SourceTransformer(new CanonicalMigrationPlan(List.of(bridge)), methodApi(false))
                .transformAstWithAccounting(unit.ast(), unit.relativePath(), applied, diagnostics);

        String generated = unit.ast().toString();
        assertFalse(generated.contains("legacy::oldCall"), generated);
        assertTrue(generated.contains("Hooks.bridge(legacy, __continuum$arg0)"), generated);
        assertTrue(diagnostics.isEmpty(), diagnostics.toString());
        assertEquals(MigrationLayer.SOURCE_AST, applied.get(0).layer());
    }

    @Test
    void constructorReferenceWithFactoryMigrationFailsConservatively(@TempDir Path root) throws Exception {
        Path legacy = root.resolve("api/Legacy.java");
        Path use = root.resolve("example/Use.java");
        Files.createDirectories(legacy.getParent());
        Files.createDirectories(use.getParent());
        Files.writeString(legacy, "package api; public class Legacy { public Legacy() {} }");
        Files.writeString(use, """
                package example;
                import api.Legacy;
                import java.util.function.Supplier;
                public class Use {
                    public Supplier<Legacy> supplier() { return Legacy::new; }
                }
                """);

        CanonicalMigrationRule constructor = CanonicalMigrationRule.builder()
                .type(MigrationType.CONSTRUCTOR_TO_FACTORY)
                .sourceOwner("api/Legacy")
                .sourceName("<init>")
                .sourceDescriptor("()V")
                .targetOwner("api/Legacy")
                .targetName("create")
                .targetDescriptor("()Lapi/Legacy;")
                .build();

        SourceUnit unit = parse(root, "example/Use.java");
        var diagnostics = new ArrayList<Diagnostic>();
        new SourceTransformer(new CanonicalMigrationPlan(List.of(constructor)), constructorApi())
                .transformAstWithAccounting(unit.ast(), unit.relativePath(), new ArrayList<>(), diagnostics);

        String generated = unit.ast().toString();
        assertFalse(generated.contains("Legacy::new"), generated);
        assertTrue(generated.contains("Legacy::create"), generated);
        assertTrue(diagnostics.isEmpty(), diagnostics.toString());
    }

    private static java.util.Map<String, ClassInfo> methodApi(boolean isStatic) {
        int access = Opcodes.ACC_PUBLIC | (isStatic ? Opcodes.ACC_STATIC : 0);
        return java.util.Map.of(
                "api/Legacy",
                new ClassInfo(
                        "api/Legacy",
                        "java/lang/Object",
                        List.of(),
                        Opcodes.ACC_PUBLIC,
                        List.of(),
                        List.of(new ClassInfo.Member("oldCall", "(I)I", access, null))
                )
        );
    }

    private static java.util.Map<String, ClassInfo> constructorApi() {
        return java.util.Map.of(
                "api/Legacy",
                new ClassInfo(
                        "api/Legacy",
                        "java/lang/Object",
                        List.of(),
                        Opcodes.ACC_PUBLIC,
                        List.of(),
                        List.of(new ClassInfo.Member("<init>", "()V", Opcodes.ACC_PUBLIC, null))
                )
        );
    }

    private static CanonicalMigrationRule methodRename() {
        return CanonicalMigrationRule.builder()
                .rulePackId("rename")
                .type(MigrationType.MEMBER_RENAME)
                .sourceOwner("api/Legacy")
                .sourceName("oldCall")
                .sourceDescriptor("(I)I")
                .targetOwner("api/Target")
                .targetName("newCall")
                .targetDescriptor("(I)I")
                .build();
    }

    private static void writeLegacy(Path root, boolean isStatic) throws Exception {
        Path legacy = root.resolve("api/Legacy.java");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy,
                "package api; public class Legacy { public "
                        + (isStatic ? "static " : "")
                        + "int oldCall(int value) { return value; } }");
    }

    private static SourceUnit parse(Path root, String relative) throws Exception {
        return new SourceParser(List.of(root), List.of())
                .parseDirectory(root)
                .stream()
                .filter(unit -> unit.relativePath().equals(relative))
                .findFirst()
                .orElseThrow();
    }
    @Test
    void sourceApiIndexResolvesNestedSourceOwnerToBinaryName() {
        var api = new com.kyroxova.continuumlib.source.ast.SourceApiIndex(java.util.Map.of(
                "api/Outer$Inner",
                new ClassInfo(
                        "api/Outer$Inner",
                        "java/lang/Object",
                        List.of(),
                        Opcodes.ACC_PUBLIC,
                        List.of(),
                        List.of(new ClassInfo.Member("oldCall", "(I)I", Opcodes.ACC_PUBLIC, null))
                )
        ));

        var method = api.uniqueMethod("api.Outer.Inner", "oldCall").orElseThrow();

        assertEquals("api/Outer$Inner", method.reference().owner());
        assertEquals("(I)I", method.reference().descriptor());
    }

    @Test
    void canonicalPlanAliasesNestedSourceNamesWithoutAliasingExactBinaryOwners() {
        CanonicalMigrationRule nested = CanonicalMigrationRule.builder()
                .type(MigrationType.MEMBER_RENAME)
                .sourceOwner("api/Outer$Inner")
                .sourceName("oldCall")
                .sourceDescriptor("(I)I")
                .targetOwner("api/Outer$Inner")
                .targetName("newCall")
                .targetDescriptor("(I)I")
                .build();
        CanonicalMigrationPlan plan = new CanonicalMigrationPlan(List.of(nested));

        assertEquals(1, plan.findMethodRules("api.Outer.Inner", "oldCall").size());
        assertTrue(plan.findExactMemberRename(
                new com.kyroxova.continuumlib.bytecode.MemberReference(
                        "api/Outer$Inner", "oldCall", "(I)I")).isPresent());
        assertTrue(plan.findExactMemberRename(
                new com.kyroxova.continuumlib.bytecode.MemberReference(
                        "api/Outer/Inner", "oldCall", "(I)I")).isEmpty());
    }

}
