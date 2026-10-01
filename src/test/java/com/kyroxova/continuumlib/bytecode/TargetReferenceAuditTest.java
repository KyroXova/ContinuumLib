package com.kyroxova.continuumlib.bytecode;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TargetReferenceAuditTest {
    @Test void existingDeclarationsDoNotHideInaccessibleMembers() {
        var owner = type("api/Base", null,
                new ClassInfo.Member("internal", "()V", 0, null),
                new ClassInfo.Member("hidden", "()V", Opcodes.ACC_PRIVATE, null),
                new ClassInfo.Member("guarded", "()V", Opcodes.ACC_PROTECTED, null));
        var caller = type("mod/Block", null);
        var audit = new TargetReferenceAudit(Map.of(owner.name(), owner, caller.name(), caller));
        assertEquals("ACCESS_DENIED", audit.check(use(owner.name(), "internal", Opcodes.INVOKEVIRTUAL)).status().name());
        assertEquals("ACCESS_REQUIRES_REVIEW", audit.check(use(owner.name(), "hidden", Opcodes.INVOKEVIRTUAL)).status().name());
        assertEquals("ACCESS_DENIED", audit.check(use(owner.name(), "guarded", Opcodes.INVOKEVIRTUAL)).status().name());
        var subclass = type("mod/Block", owner.name());
        var inheritedAudit = new TargetReferenceAudit(Map.of(owner.name(), owner, subclass.name(), subclass));
        assertEquals("ACCESS_REQUIRES_REVIEW", inheritedAudit.check(use(owner.name(), "guarded", Opcodes.INVOKEVIRTUAL)).status().name());
    }
    @Test void finalFieldsCannotBeWrittenOutsideTheirOwnInitializers() {
        var owner = new ClassInfo("api/Base", null, List.of(), Opcodes.ACC_PUBLIC,
                List.of(new ClassInfo.Member("constant", "I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, null)), List.of());
        var audit = new TargetReferenceAudit(Map.of(owner.name(), owner));
        var target = new MemberReference(owner.name(), "constant", "I");
        assertEquals("FINAL_WRITE_ILLEGAL", audit.check(new ReferenceScanner.Use(new MemberReference(owner.name(), "change", "()V"),
                target, Opcodes.PUTSTATIC, false, false, 1)).status().name());
        assertEquals(TargetReferenceAudit.Status.DECLARATION_FOUND, audit.check(new ReferenceScanner.Use(new MemberReference(owner.name(), "<clinit>", "()V"),
                target, Opcodes.PUTSTATIC, false, false, 1)).status());
        assertEquals("FINAL_WRITE_ILLEGAL", audit.check(new ReferenceScanner.Use(new MemberReference(owner.name(), "<clinit>", "()V"),
                target, Opcodes.PUTSTATIC, false, true, 1)).status().name());
    }
    private ClassInfo type(String name, String parent, ClassInfo.Member... methods) {
        return new ClassInfo(name, parent, List.of(), Opcodes.ACC_PUBLIC, List.of(), List.of(methods));
    }
    private ReferenceScanner.Use use(String owner, String name, int opcode) {
        return new ReferenceScanner.Use(new MemberReference("mod/Block", "tick", "()V"),
                new MemberReference(owner, name, "()V"), opcode, false, false, 42);
    }
    @Test void distinguishesMissingMembersFromMissingDependenciesAndChecksUnchangedCalls() {
        var base = type("api/Base", null, new ClassInfo.Member("tick", "()V", Opcodes.ACC_PUBLIC, null));
        var child = type("api/Child", "api/Base");
        var partial = type("api/Partial", "absent/Base");
        var audit = new TargetReferenceAudit(Map.of(base.name(), base, child.name(), child, partial.name(), partial));
        assertEquals(TargetReferenceAudit.Status.DECLARATION_FOUND, audit.check(use(child.name(), "tick", Opcodes.INVOKEVIRTUAL)).status());
        assertEquals(TargetReferenceAudit.Status.MEMBER_MISSING, audit.check(use(child.name(), "removed", Opcodes.INVOKEVIRTUAL)).status());
        assertEquals(TargetReferenceAudit.Status.HIERARCHY_INCOMPLETE, audit.check(use(partial.name(), "removed", Opcodes.INVOKEVIRTUAL)).status());
        assertEquals(TargetReferenceAudit.Status.OWNER_MISSING, audit.check(use("absent/Api", "tick", Opcodes.INVOKEVIRTUAL)).status());
        assertEquals(TargetReferenceAudit.Status.STATIC_MISMATCH, audit.check(use(base.name(), "tick", Opcodes.INVOKESTATIC)).status());
    }
    @Test void constructorsAreNeverInheritedAndMalformedCyclesAreNotReportedAsMissing() {
        var base = type("api/Base", null, new ClassInfo.Member("<init>", "()V", Opcodes.ACC_PUBLIC, null));
        var child = type("api/Child", base.name());
        var cycle = type("api/Cycle", "api/Cycle");
        var audit = new TargetReferenceAudit(Map.of(base.name(), base, child.name(), child, cycle.name(), cycle));
        assertEquals(TargetReferenceAudit.Status.MEMBER_MISSING, audit.check(use(child.name(), "<init>", Opcodes.INVOKESPECIAL)).status());
        assertEquals(TargetReferenceAudit.Status.HIERARCHY_INCOMPLETE, audit.check(use(cycle.name(), "tick", Opcodes.INVOKEVIRTUAL)).status());
    }
    @Test void privateAndInterfaceInheritanceAreNotMistakenForProvenLinkage() {
        var base = type("api/Base", null, new ClassInfo.Member("hidden", "()V", Opcodes.ACC_PRIVATE, null));
        var child = type("api/Child", base.name());
        var itf = new ClassInfo("api/Itf", null, List.of(), Opcodes.ACC_INTERFACE | Opcodes.ACC_PUBLIC, List.of(), List.of());
        var audit = new TargetReferenceAudit(Map.of(base.name(), base, child.name(), child, itf.name(), itf));
        assertEquals(TargetReferenceAudit.Status.INHERITANCE_REQUIRES_REVIEW, audit.check(use(child.name(), "hidden", Opcodes.INVOKEVIRTUAL)).status());
        assertEquals(TargetReferenceAudit.Status.OWNER_KIND_MISMATCH, audit.check(use(itf.name(), "tick", Opcodes.INVOKEVIRTUAL)).status());
    }
    @Test void auditsFieldsAndHandlesUsingTheirExactDescriptorsAndStaticShape() {
        var base = new ClassInfo("api/Base", null, List.of(), Opcodes.ACC_PUBLIC,
                List.of(new ClassInfo.Member("value", "I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, null)), List.of());
        var child = type("api/Child", base.name());
        var audit = new TargetReferenceAudit(Map.of(base.name(), base, child.name(), child));
        var caller = new MemberReference("mod/Block", "tick", "()V");
        var field = new MemberReference(child.name(), "value", "I");
        assertEquals(TargetReferenceAudit.Status.DECLARATION_FOUND,
                audit.check(new ReferenceScanner.Use(caller, field, Opcodes.GETSTATIC, false, true, 9)).status());
        assertEquals(TargetReferenceAudit.Status.STATIC_MISMATCH,
                audit.check(new ReferenceScanner.Use(caller, field, Opcodes.PUTFIELD, false, false, 9)).status());
        var wrongDescriptor = new MemberReference(child.name(), "value", "J");
        assertEquals(TargetReferenceAudit.Status.MEMBER_MISSING,
                audit.check(new ReferenceScanner.Use(caller, wrongDescriptor, Opcodes.GETSTATIC, false, false, 9)).status());
    }
    @Test void resolvesMethodsThroughInterfacesAndDefaultsWithoutRejectingImplementingClasses() {
        var itf = new ClassInfo("api/Service", null, List.of(), Opcodes.ACC_INTERFACE | Opcodes.ACC_PUBLIC, List.of(),
                List.of(new ClassInfo.Member("process", "()V", Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, null),
                        new ClassInfo.Member("defaultAction", "()V", Opcodes.ACC_PUBLIC, null)));
        var base = new ClassInfo("api/Base", "java/lang/Object", List.of("api/Service"), Opcodes.ACC_PUBLIC, List.of(),
                List.of(new ClassInfo.Member("baseAction", "()V", Opcodes.ACC_PUBLIC, null)));
        var child = new ClassInfo("api/Child", "api/Base", List.of(), Opcodes.ACC_PUBLIC, List.of(), List.of());
        var object = new ClassInfo("java/lang/Object", null, List.of(), Opcodes.ACC_PUBLIC, List.of(),
                List.of(new ClassInfo.Member("toString", "()Ljava/lang/String;", Opcodes.ACC_PUBLIC, null)));
        var audit = new TargetReferenceAudit(Map.of(itf.name(), itf, base.name(), base, child.name(), child, object.name(), object));

        // Child inherits baseAction from Base (even though Base implements Service)
        assertEquals(TargetReferenceAudit.Status.DECLARATION_FOUND,
                audit.check(use(child.name(), "baseAction", Opcodes.INVOKEVIRTUAL)).status());

        // Child inherits defaultAction from Service via Base
        assertEquals(TargetReferenceAudit.Status.DECLARATION_FOUND,
                audit.check(use(child.name(), "defaultAction", Opcodes.INVOKEVIRTUAL)).status());

        // Interface reference invoking Object.toString() is valid in JVM interface method resolution
        var itfToString = new ReferenceScanner.Use(new MemberReference("mod/Block", "tick", "()V"),
                new MemberReference("api/Service", "toString", "()Ljava/lang/String;"), Opcodes.INVOKEINTERFACE, true, false, 42);
        assertEquals(TargetReferenceAudit.Status.DECLARATION_FOUND, audit.check(itfToString).status());
    }

    @Test void detectsConflictingDefaultMethodsAcrossInterfaces() {
        var itfA = new ClassInfo("api/ServiceA", null, List.of(), Opcodes.ACC_INTERFACE | Opcodes.ACC_PUBLIC, List.of(),
                List.of(new ClassInfo.Member("action", "()V", Opcodes.ACC_PUBLIC, null)));
        var itfB = new ClassInfo("api/ServiceB", null, List.of(), Opcodes.ACC_INTERFACE | Opcodes.ACC_PUBLIC, List.of(),
                List.of(new ClassInfo.Member("action", "()V", Opcodes.ACC_PUBLIC, null)));
        var impl = new ClassInfo("api/DualImpl", "java/lang/Object", List.of("api/ServiceA", "api/ServiceB"), Opcodes.ACC_PUBLIC, List.of(), List.of());
        var audit = new TargetReferenceAudit(Map.of(itfA.name(), itfA, itfB.name(), itfB, impl.name(), impl));
        assertEquals(TargetReferenceAudit.Status.INHERITANCE_REQUIRES_REVIEW,
                audit.check(use(impl.name(), "action", Opcodes.INVOKEVIRTUAL)).status());
    }

    @Test void resolvesPublicObjectAndCloneMethodsOnArrayTypes() {
        var object = new ClassInfo("java/lang/Object", null, List.of(), Opcodes.ACC_PUBLIC, List.of(),
                List.of(new ClassInfo.Member("toString", "()Ljava/lang/String;", Opcodes.ACC_PUBLIC, null),
                        new ClassInfo.Member("hashCode", "()I", Opcodes.ACC_PUBLIC, null)));
        var audit = new TargetReferenceAudit(Map.of(object.name(), object));

        // [Ljava/lang/String;.clone()
        var cloneUse = new ReferenceScanner.Use(new MemberReference("mod/Block", "tick", "()V"),
                new MemberReference("[Ljava/lang/String;", "clone", "()Ljava/lang/Object;"), Opcodes.INVOKEVIRTUAL, false, false, 10);
        assertEquals(TargetReferenceAudit.Status.DECLARATION_FOUND, audit.check(cloneUse).status());

        // [Ljava/lang/String;.toString()
        var toStringUse = new ReferenceScanner.Use(new MemberReference("mod/Block", "tick", "()V"),
                new MemberReference("[Ljava/lang/String;", "toString", "()Ljava/lang/String;"), Opcodes.INVOKEVIRTUAL, false, false, 11);
        assertEquals(TargetReferenceAudit.Status.DECLARATION_FOUND, audit.check(toStringUse).status());

        // Arrays have no fields or constructors
        var fieldUse = new ReferenceScanner.Use(new MemberReference("mod/Block", "tick", "()V"),
                new MemberReference("[Ljava/lang/String;", "length", "I"), Opcodes.GETFIELD, false, false, 12);
        assertEquals(TargetReferenceAudit.Status.MEMBER_MISSING, audit.check(fieldUse).status());
    }

    @Test void resolvesSignaturePolymorphicMethodsWithAnyCallDescriptor() {
        var mh = new ClassInfo("java/lang/invoke/MethodHandle", "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC, List.of(),
                List.of(new ClassInfo.Member("invokeExact", "([Ljava/lang/Object;)Ljava/lang/Object;",
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_NATIVE | Opcodes.ACC_VARARGS, null)));
        var audit = new TargetReferenceAudit(Map.of(mh.name(), mh));

        var polyUse = new ReferenceScanner.Use(new MemberReference("mod/Block", "tick", "()V"),
                new MemberReference("java/lang/invoke/MethodHandle", "invokeExact", "(Ljava/lang/String;I)Ljava/lang/String;"),
                Opcodes.INVOKEVIRTUAL, false, false, 20);
        assertEquals(TargetReferenceAudit.Status.DECLARATION_FOUND, audit.check(polyUse).status());
    }

    @Test void validatesNestmatePrivateAccessAndRejectsDifferentNests() {
        var host = new ClassInfo("mod/Outer", "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC, List.of(),
                List.of(new ClassInfo.Member("secret", "()V", Opcodes.ACC_PRIVATE, null)),
                null, List.of("mod/Outer$Inner"));
        var inner = new ClassInfo("mod/Outer$Inner", "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC, List.of(),
                List.of(), "mod/Outer", List.of());
        var stranger = new ClassInfo("mod/Stranger", "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC, List.of(),
                List.of(), "mod/Stranger", List.of());
        var audit = new TargetReferenceAudit(Map.of(host.name(), host, inner.name(), inner, stranger.name(), stranger));

        // Inner accessing host's private member -> DECLARATION_FOUND
        var innerCall = new ReferenceScanner.Use(new MemberReference("mod/Outer$Inner", "run", "()V"),
                new MemberReference("mod/Outer", "secret", "()V"), Opcodes.INVOKEVIRTUAL, false, false, 5);
        assertEquals(TargetReferenceAudit.Status.DECLARATION_FOUND, audit.check(innerCall).status());

        // Stranger in different nest accessing host's private member -> ACCESS_DENIED
        var strangerCall = new ReferenceScanner.Use(new MemberReference("mod/Stranger", "run", "()V"),
                new MemberReference("mod/Outer", "secret", "()V"), Opcodes.INVOKEVIRTUAL, false, false, 5);
        assertEquals(TargetReferenceAudit.Status.ACCESS_DENIED, audit.check(strangerCall).status());
    }

    @Test void validatesProtectedReceiverConstraintsForSubclasses() {
        var base = new ClassInfo("api/Base", "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC, List.of(),
                List.of(new ClassInfo.Member("guarded", "()V", Opcodes.ACC_PROTECTED, null)));
        var sub = new ClassInfo("mod/Sub", "api/Base", List.of(), Opcodes.ACC_PUBLIC, List.of(), List.of());
        var audit = new TargetReferenceAudit(Map.of(base.name(), base, sub.name(), sub));

        // Subclass calls guarded via subclass reference -> receiver constraint satisfied statically
        var callViaSub = new ReferenceScanner.Use(new MemberReference("mod/Sub", "tick", "()V"),
                new MemberReference("mod/Sub", "guarded", "()V"), Opcodes.INVOKEVIRTUAL, false, false, 15);
        assertEquals(TargetReferenceAudit.Status.DECLARATION_FOUND, audit.check(callViaSub).status());

        // Subclass calls guarded via superclass reference -> receiver constraint cannot be statically guaranteed
        var callViaSuper = new ReferenceScanner.Use(new MemberReference("mod/Sub", "tick", "()V"),
                new MemberReference("api/Base", "guarded", "()V"), Opcodes.INVOKEVIRTUAL, false, false, 16);
        assertEquals(TargetReferenceAudit.Status.ACCESS_REQUIRES_REVIEW, audit.check(callViaSuper).status());
    }

    @Test void validatesInvokespecialDispatchRules() {
        var itf = new ClassInfo("api/DefItf", null, List.of(), Opcodes.ACC_INTERFACE | Opcodes.ACC_PUBLIC, List.of(),
                List.of(new ClassInfo.Member("greet", "()V", Opcodes.ACC_PUBLIC, null)));
        var impl = new ClassInfo("mod/Impl", "java/lang/Object", List.of("api/DefItf"), Opcodes.ACC_PUBLIC, List.of(), List.of());
        var unrelated = new ClassInfo("mod/Unrelated", "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC, List.of(), List.of());
        var audit = new TargetReferenceAudit(Map.of(itf.name(), itf, impl.name(), impl, unrelated.name(), unrelated));

        // Impl calls Interface.super.greet() -> DECLARATION_FOUND
        var legalSuperItf = new ReferenceScanner.Use(new MemberReference("mod/Impl", "greet", "()V"),
                new MemberReference("api/DefItf", "greet", "()V"), Opcodes.INVOKESPECIAL, true, false, 30);
        assertEquals(TargetReferenceAudit.Status.DECLARATION_FOUND, audit.check(legalSuperItf).status());

        // Unrelated calls Interface.super.greet() -> ACCESS_DENIED
        var illegalSuperItf = new ReferenceScanner.Use(new MemberReference("mod/Unrelated", "greet", "()V"),
                new MemberReference("api/DefItf", "greet", "()V"), Opcodes.INVOKESPECIAL, true, false, 31);
        assertEquals(TargetReferenceAudit.Status.ACCESS_DENIED, audit.check(illegalSuperItf).status());
    }
}

