package com.kyroxova.continuumlib.source;

import com.kyroxova.continuumlib.source.ast.SourceParser;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.kyroxova.continuumlib.source.ast.SourceTypes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SourceTypesTest {
    @Test
    void discoversAndRemovesNestedRecords() {
        String code = """
                package example;
                class Outer {
                    record Keep(int value) {}
                    record Legacy(int value) {}
                }
                """;

        var unit = new SourceParser(List.of(), List.of())
                .parseString("example/Outer.java", code);

        var memberKinds = unit.ast().getType(0).getMembers().stream()
                .map(member -> member.getClass().getName() + "::" + member)
                .toList();
        assertEquals(2, unit.ast().findAll(RecordDeclaration.class).size(), memberKinds.toString());

        var types = SourceTypes.all(unit.ast());
        assertEquals(
                List.of("example.Outer", "example.Outer.Keep", "example.Outer.Legacy"),
                types.stream().map(SourceTypes::qualifiedName).toList()
        );

        var legacy = types.stream()
                .filter(type -> SourceTypes.qualifiedName(type).equals("example.Outer.Legacy"))
                .findFirst()
                .orElseThrow();

        assertTrue(SourceTypes.nested(legacy));
        assertTrue(SourceTypes.remove(legacy));
        assertFalse(unit.ast().toString().contains("record Legacy"), unit.ast().toString());
        assertTrue(unit.ast().toString().contains("record Keep"), unit.ast().toString());
    }


    @Test
    void preservesSourceOrderBeyondIntegerLineOverflowRange() {
        StringBuilder code = new StringBuilder("package example;\nclass First {}\n");
        code.append("\n".repeat(3_000));
        code.append("class Second {}\n");

        var unit = new SourceParser(List.of(), List.of())
                .parseString("example/Large.java", code.toString());

        assertEquals(
                List.of("example.First", "example.Second"),
                SourceTypes.all(unit.ast()).stream()
                        .map(SourceTypes::qualifiedName)
                        .toList()
        );
    }

    @Test
    void discoversNestedMemberTypesButNotMethodLocalTypes() {
        String code = """
                package example;
                class Outer {
                    interface NestedInterface {}
                    enum NestedEnum { VALUE }
                    @interface NestedAnnotation {}

                    void method() {
                        class LocalOnly {}
                    }
                }
                """;

        var unit = new SourceParser(List.of(), List.of())
                .parseString("example/Outer.java", code);

        assertEquals(
                List.of(
                        "example.Outer",
                        "example.Outer.NestedInterface",
                        "example.Outer.NestedEnum",
                        "example.Outer.NestedAnnotation"
                ),
                SourceTypes.all(unit.ast()).stream()
                        .map(SourceTypes::qualifiedName)
                        .toList()
        );
    }
}
