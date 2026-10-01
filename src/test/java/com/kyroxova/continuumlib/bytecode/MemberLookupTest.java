package com.kyroxova.continuumlib.bytecode;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MemberLookupTest {
    private ClassInfo type(String name, String parent, List<ClassInfo.Member> methods) {
        return new ClassInfo(name, parent, List.of(), 1, List.of(), methods);
    }
    @Test void resolvesInheritedMethodButNeverInheritsConstructors() {
        var parent = type("api/Parent", null, List.of(new ClassInfo.Member("shape", "(I)I", 1, null), new ClassInfo.Member("<init>", "(I)V", 1, null)));
        var child = type("mod/Child", "api/Parent", List.of());
        var lookup = new MemberLookup(Map.of(parent.name(), parent, child.name(), child));
        var result = lookup.find(new MemberReference("mod/Child", "shape", "(I)I"));
        assertEquals(MemberLookup.Status.FOUND, result.status());
        assertEquals("api/Parent", result.declaringOwner());
        assertEquals(MemberLookup.Status.MISSING_MEMBER, lookup.find(new MemberReference("mod/Child", "<init>", "(I)V")).status());
    }
    @Test void incompleteHierarchyIsNotReportedAsMissingOrCompatible() {
        var child = type("mod/Child", "missing/Parent", List.of());
        var lookup = new MemberLookup(Map.of(child.name(), child));
        assertEquals(MemberLookup.Status.INCOMPLETE_CLASSPATH, lookup.find(new MemberReference(child.name(), "shape", "()V")).status());
    }
    @Test void preservesOverloadIdentity() {
        var owner = type("api/Block", null, List.of(new ClassInfo.Member("shape", "(I)I", 1, null)));
        assertEquals(MemberLookup.Status.MISSING_MEMBER, new MemberLookup(Map.of(owner.name(), owner))
                .find(new MemberReference(owner.name(), "shape", "(J)I")).status());
    }
}
