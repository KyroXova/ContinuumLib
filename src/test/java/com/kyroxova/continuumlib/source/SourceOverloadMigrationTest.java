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

class SourceOverloadMigrationTest {
    @Test
    void resolvedUnchangedOverloadIsNotRenamed(@TempDir Path root) throws Exception {
        Path api = root.resolve("api/Legacy.java");
        Path use = root.resolve("example/Use.java");
        Files.createDirectories(api.getParent());
        Files.createDirectories(use.getParent());

        Files.writeString(api, """
                package api;
                public class Legacy {
                    public static int old(int value) { return value; }
                    public static String old(String value) { return value; }
                }
                """);
        Files.writeString(use, """
                package example;
                import api.Legacy;
                public class Use {
                    public String run() { return Legacy.old("x"); }
                }
                """);

        SourceUnit unit = parse(root, "example/Use.java");
        var diagnostics = new ArrayList<Diagnostic>();
        new SourceTransformer(
                new CanonicalMigrationPlan(List.of(intMethodRename())),
                overloadedMethodApi()
        ).transformAstWithAccounting(unit.ast(), unit.relativePath(), new ArrayList<>(), diagnostics);

        String generated = unit.ast().toString();
        assertTrue(generated.contains("Legacy.old(\"x\")"), generated);
        assertFalse(generated.contains("newCall(\"x\")"), generated);
        assertTrue(diagnostics.isEmpty(), diagnostics.toString());
    }

    @Test
    void resolvedUnchangedConstructorIsNotConvertedToFactory(@TempDir Path root) throws Exception {
        Path api = root.resolve("api/Legacy.java");
        Path use = root.resolve("example/Use.java");
        Files.createDirectories(api.getParent());
        Files.createDirectories(use.getParent());

        Files.writeString(api, """
                package api;
                public class Legacy {
                    public Legacy(int value) {}
                    public Legacy(String value) {}
                }
                """);
        Files.writeString(use, """
                package example;
                import api.Legacy;
                public class Use {
                    public Legacy run() { return new Legacy("x"); }
                }
                """);

        SourceUnit unit = parse(root, "example/Use.java");
        var diagnostics = new ArrayList<Diagnostic>();
        new SourceTransformer(
                new CanonicalMigrationPlan(List.of(intConstructorFactory())),
                overloadedConstructorApi()
        ).transformAstWithAccounting(unit.ast(), unit.relativePath(), new ArrayList<>(), diagnostics);

        String generated = unit.ast().toString();
        assertTrue(generated.contains("new Legacy(\"x\")"), generated);
        assertFalse(generated.contains("Legacy.create(\"x\")"), generated);
        assertTrue(diagnostics.isEmpty(), diagnostics.toString());
    }

    @Test
    void exactLiteralTypeKeepsUnmigratedOverloadWithoutGuessing() {
        String code = """
                package example;
                import api.Legacy;
                public class Use {
                    public String run() { return Legacy.old("x"); }
                }
                """;
        SourceUnit unit = new SourceParser(List.of(), List.of()).parseString("example/Use.java", code);
        var diagnostics = new ArrayList<Diagnostic>();

        new SourceTransformer(
                new CanonicalMigrationPlan(List.of(intMethodRename())),
                overloadedMethodApi()
        ).transformAstWithAccounting(unit.ast(), unit.relativePath(), new ArrayList<>(), diagnostics);

        String generated = unit.ast().toString();
        assertTrue(generated.contains("Legacy.old(\"x\")"), generated);
        assertFalse(generated.contains("newCall(\"x\")"), generated);
        assertTrue(diagnostics.isEmpty(), diagnostics.toString());
    }

    private static CanonicalMigrationRule intMethodRename() {
        return CanonicalMigrationRule.builder()
                .rulePackId("method")
                .type(MigrationType.MEMBER_RENAME)
                .sourceOwner("api/Legacy")
                .sourceName("old")
                .sourceDescriptor("(I)I")
                .targetOwner("api/Legacy")
                .targetName("newCall")
                .targetDescriptor("(I)I")
                .layer(MigrationLayer.SOURCE_AST)
                .build();
    }

    private static CanonicalMigrationRule intConstructorFactory() {
        return CanonicalMigrationRule.builder()
                .rulePackId("constructor")
                .type(MigrationType.CONSTRUCTOR_TO_FACTORY)
                .sourceOwner("api/Legacy")
                .sourceName("<init>")
                .sourceDescriptor("(I)V")
                .targetOwner("api/Legacy")
                .targetName("create")
                .targetDescriptor("(I)Lapi/Legacy;")
                .layer(MigrationLayer.SOURCE_AST)
                .build();
    }

    private static Map<String, ClassInfo> overloadedMethodApi() {
        return Map.of(
                "api/Legacy",
                new ClassInfo(
                        "api/Legacy",
                        "java/lang/Object",
                        List.of(),
                        Opcodes.ACC_PUBLIC,
                        List.of(),
                        List.of(
                                new ClassInfo.Member(
                                        "old",
                                        "(I)I",
                                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                                        null
                                ),
                                new ClassInfo.Member(
                                        "old",
                                        "(Ljava/lang/String;)Ljava/lang/String;",
                                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                                        null
                                )
                        )
                )
        );
    }

    private static Map<String, ClassInfo> overloadedConstructorApi() {
        return Map.of(
                "api/Legacy",
                new ClassInfo(
                        "api/Legacy",
                        "java/lang/Object",
                        List.of(),
                        Opcodes.ACC_PUBLIC,
                        List.of(),
                        List.of(
                                new ClassInfo.Member("<init>", "(I)V", Opcodes.ACC_PUBLIC, null),
                                new ClassInfo.Member(
                                        "<init>",
                                        "(Ljava/lang/String;)V",
                                        Opcodes.ACC_PUBLIC,
                                        null
                                )
                        )
                )
        );
    }

    private static SourceUnit parse(Path root, String relative) throws Exception {
        return new SourceParser(List.of(root), List.of())
                .parseDirectory(root)
                .stream()
                .filter(unit -> unit.relativePath().equals(relative))
                .findFirst()
                .orElseThrow();
    }
}
