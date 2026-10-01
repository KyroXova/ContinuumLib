package com.kyroxova.continuumlib.knowledge.diff;

import com.kyroxova.continuumlib.bytecode.ClassInfo;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ApiDeltaTest {
    @Test void reportsExactDescriptorsAccessFieldsAndHierarchyWithoutGuessingReplacements() {
        var old = new ClassInfo("api/Block", "api/OldBase", List.of(), 1,
                List.of(new ClassInfo.Member("state", "I", 1, null)),
                List.of(new ClassInfo.Member("shape", "(I)I", 1, null), new ClassInfo.Member("tick", "()V", 1, null)));
        var next = new ClassInfo("api/Block", "api/NewBase", List.of(), 1,
                List.of(new ClassInfo.Member("state", "J", 1, null)),
                List.of(new ClassInfo.Member("shape", "(II)I", 1, null), new ClassInfo.Member("tick", "()V", 9, null)));
        var changes = new ApiDelta().compare(Map.of(old.name(), old), Map.of(next.name(), next));
        assertEquals(6, changes.size());
        assertTrue(changes.stream().anyMatch(c -> c.kind() == ApiDelta.Kind.HIERARCHY_CHANGED));
        assertTrue(changes.stream().anyMatch(c -> c.kind() == ApiDelta.Kind.METHOD_REMOVED && c.descriptor().equals("(I)I")));
        assertTrue(changes.stream().anyMatch(c -> c.kind() == ApiDelta.Kind.METHOD_ADDED && c.descriptor().equals("(II)I")));
        assertTrue(changes.stream().anyMatch(c -> c.kind() == ApiDelta.Kind.METHOD_CHANGED && c.name().equals("tick")));
        assertTrue(changes.stream().anyMatch(c -> c.kind() == ApiDelta.Kind.FIELD_REMOVED && c.descriptor().equals("I")));
        assertTrue(changes.stream().anyMatch(c -> c.kind() == ApiDelta.Kind.FIELD_ADDED && c.descriptor().equals("J")));
        assertTrue(new ApiDelta().compare(Map.of(old.name(), old), Map.of(old.name(), old)).isEmpty());
    }
    @Test void recordsWholeClassAdditionAndRemoval() {
        var old = new ClassInfo("api/Old", null, List.of(), 1, List.of(), List.of());
        var next = new ClassInfo("api/New", null, List.of(), 1, List.of(), List.of());
        var changes = new ApiDelta().compare(Map.of(old.name(), old), Map.of(next.name(), next));
        assertEquals(List.of(ApiDelta.Kind.CLASS_ADDED, ApiDelta.Kind.CLASS_REMOVED), changes.stream().map(ApiDelta.Change::kind).toList());
    }
}
