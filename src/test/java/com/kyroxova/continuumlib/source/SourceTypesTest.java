package com.kyroxova.continuumlib.source;

import com.kyroxova.continuumlib.source.ast.SourceParser;
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
}
