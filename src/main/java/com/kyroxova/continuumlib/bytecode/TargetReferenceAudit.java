package com.kyroxova.continuumlib.bytecode;

import org.objectweb.asm.Opcodes;
import java.util.*;

/** Conservative declaration audit, not a JVM linker or behavioral compatibility certificate. */
public final class TargetReferenceAudit {
    public enum Status { DECLARATION_FOUND, OWNER_MISSING, MEMBER_MISSING, HIERARCHY_INCOMPLETE,
        STATIC_MISMATCH, OWNER_KIND_MISMATCH, INHERITANCE_REQUIRES_REVIEW,
        ACCESS_DENIED, ACCESS_REQUIRES_REVIEW, FINAL_WRITE_ILLEGAL }
    public record Finding(Status status, String detail) {}
    private final Map<String, ClassInfo> classes;
    public TargetReferenceAudit(Map<String, ClassInfo> classes) { this.classes = Map.copyOf(classes); }

    public Finding check(ReferenceScanner.Use use) {
        var ref = use.target();
        ClassInfo owner;
        if (ref.owner().startsWith("[")) {
            owner = syntheticArrayType(ref.owner());
        } else {
            owner = classes.get(ref.owner());
        }
        if (owner == null) return new Finding(Status.OWNER_MISSING, ref.owner());
        if ((owner.access() & Opcodes.ACC_PUBLIC) == 0 && !owner.name().equals(use.caller().owner())) {
            if (!samePackage(owner.name(), use.caller().owner()))
                return new Finding(Status.ACCESS_DENIED, "Non-public symbolic owner: " + owner.name());
            return new Finding(Status.ACCESS_REQUIRES_REVIEW, "Runtime package/loader identity: " + owner.name());
        }
        boolean field = ref.descriptor().charAt(0) != '(';
        if (!field && use.interfaceOwner() != ((owner.access() & Opcodes.ACC_INTERFACE) != 0))
            return new Finding(Status.OWNER_KIND_MISMATCH, ref.owner());
        return lookup(use, owner, field);
    }

    private static ClassInfo syntheticArrayType(String arrayName) {
        return new ClassInfo(arrayName, "java/lang/Object",
                List.of("java/lang/Cloneable", "java/io/Serializable"),
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                List.of(),
                List.of(new ClassInfo.Member("clone", "()Ljava/lang/Object;", Opcodes.ACC_PUBLIC, null)));
    }

    private Finding lookup(ReferenceScanner.Use use, ClassInfo owner, boolean field) {
        if (!field && isSignaturePolymorphic(owner, use.target())) {
            boolean expectedStatic = use.opcode() == Opcodes.INVOKESTATIC;
            if (expectedStatic) return new Finding(Status.STATIC_MISMATCH, owner.name());
            return new Finding(Status.DECLARATION_FOUND, owner.name());
        }
        return field ? lookupField(use, owner) : lookupMethod(use, owner);
    }

    private static boolean isSignaturePolymorphic(ClassInfo owner, MemberReference ref) {
        if (!owner.name().equals("java/lang/invoke/MethodHandle") && !owner.name().equals("java/lang/invoke/VarHandle"))
            return false;
        for (var m : owner.methods()) {
            if (m.name().equals(ref.name())
                    && (m.access() & (Opcodes.ACC_VARARGS | Opcodes.ACC_NATIVE)) == (Opcodes.ACC_VARARGS | Opcodes.ACC_NATIVE)) {
                return true;
            }
        }
        return false;
    }

    private Finding lookupMethod(ReferenceScanner.Use use, ClassInfo owner) {
        var ref = use.target();
        boolean isInterface = (owner.access() & Opcodes.ACC_INTERFACE) != 0;

        if (isInterface) {
            for (var member : owner.methods()) {
                if (member.name().equals(ref.name()) && member.descriptor().equals(ref.descriptor())) {
                    return validateMember(use, owner, member, false);
                }
            }
            var obj = classes.get("java/lang/Object");
            if (obj != null) {
                for (var member : obj.methods()) {
                    if (member.name().equals(ref.name()) && member.descriptor().equals(ref.descriptor())
                            && (member.access() & Opcodes.ACC_PUBLIC) != 0 && (member.access() & Opcodes.ACC_STATIC) == 0) {
                        return validateMember(use, obj, member, false);
                    }
                }
            }
            return resolveSuperinterfaces(use, owner, Collections.singletonList(owner.name()));
        } else {
            if (ref.name().startsWith("<")) {
                for (var member : owner.methods()) {
                    if (member.name().equals(ref.name()) && member.descriptor().equals(ref.descriptor())) {
                        return validateMember(use, owner, member, false);
                    }
                }
                return new Finding(Status.MEMBER_MISSING, owner.name());
            }

            String currName = owner.name();
            Set<String> path = new HashSet<>();
            while (currName != null) {
                if (!path.add(currName)) return new Finding(Status.HIERARCHY_INCOMPLETE, "Hierarchy cycle: " + currName);
                if (currName.equals("java/lang/Object") && !classes.containsKey("java/lang/Object")) break;
                var curr = currName.startsWith("[") ? syntheticArrayType(currName) : classes.get(currName);
                if (curr == null) return new Finding(Status.HIERARCHY_INCOMPLETE, currName);
                for (var member : curr.methods()) {
                    if (member.name().equals(ref.name()) && member.descriptor().equals(ref.descriptor())) {
                        if (!currName.equals(owner.name()) && (member.access() & Opcodes.ACC_PRIVATE) != 0)
                            return new Finding(Status.INHERITANCE_REQUIRES_REVIEW, "Private ancestor declaration: " + currName);
                        return validateMember(use, curr, member, false);
                    }
                }
                currName = curr.superName();
            }

            return resolveSuperinterfaces(use, owner, path);
        }
    }

    private Finding resolveSuperinterfaces(ReferenceScanner.Use use, ClassInfo rootOwner, Collection<String> classChain) {
        var ref = use.target();
        Set<String> allInterfaces = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();
        for (String cls : classChain) {
            collectInterfaces(cls, allInterfaces, visited);
        }
        if (allInterfaces.isEmpty()) {
            return new Finding(Status.MEMBER_MISSING, rootOwner.name());
        }

        record Candidate(String declaringInterface, ClassInfo.Member member) {}
        List<Candidate> candidates = new ArrayList<>();
        for (String itfName : allInterfaces) {
            var itf = classes.get(itfName);
            if (itf == null) return new Finding(Status.HIERARCHY_INCOMPLETE, itfName);
            for (var member : itf.methods()) {
                if (member.name().equals(ref.name()) && member.descriptor().equals(ref.descriptor())) {
                    if ((member.access() & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0) {
                        candidates.add(new Candidate(itfName, member));
                    }
                }
            }
        }
        if (candidates.isEmpty()) {
            return new Finding(Status.MEMBER_MISSING, rootOwner.name());
        }

        List<Candidate> maximallySpecific = new ArrayList<>();
        for (var c1 : candidates) {
            boolean subExists = false;
            for (var c2 : candidates) {
                if (!c1.declaringInterface().equals(c2.declaringInterface())
                        && implementsInterface(c2.declaringInterface(), c1.declaringInterface(), new HashSet<>())) {
                    subExists = true;
                    break;
                }
            }
            if (!subExists) {
                maximallySpecific.add(c1);
            }
        }

        List<Candidate> defaults = new ArrayList<>();
        for (var c : maximallySpecific) {
            if ((c.member().access() & Opcodes.ACC_ABSTRACT) == 0) {
                defaults.add(c);
            }
        }
        if (defaults.size() > 1) {
            return new Finding(Status.INHERITANCE_REQUIRES_REVIEW, "Conflicting default methods: " + rootOwner.name());
        }
        if (defaults.size() == 1) {
            var selected = defaults.get(0);
            var declaring = classes.get(selected.declaringInterface());
            return validateMember(use, declaring, selected.member(), false);
        }
        var selected = maximallySpecific.get(0);
        var declaring = classes.get(selected.declaringInterface());
        return validateMember(use, declaring, selected.member(), false);
    }

    private void collectInterfaces(String className, Set<String> interfaces, Set<String> visited) {
        if (className == null || !visited.add(className)) return;
        var info = classes.get(className);
        if (info == null) return;
        for (String itf : info.interfaces()) {
            interfaces.add(itf);
            collectInterfaces(itf, interfaces, visited);
        }
    }

    private Finding lookupField(ReferenceScanner.Use use, ClassInfo owner) {
        var ref = use.target();
        Set<String> path = new HashSet<>();
        String currName = owner.name();
        while (currName != null) {
            if (!path.add(currName)) return new Finding(Status.HIERARCHY_INCOMPLETE, "Hierarchy cycle: " + currName);
            if (currName.equals("java/lang/Object") && !classes.containsKey("java/lang/Object")) break;
            var curr = currName.startsWith("[") ? syntheticArrayType(currName) : classes.get(currName);
            if (curr == null) return new Finding(Status.HIERARCHY_INCOMPLETE, currName);
            for (var member : curr.fields()) {
                if (member.name().equals(ref.name()) && member.descriptor().equals(ref.descriptor())) {
                    if (!currName.equals(owner.name()) && (member.access() & Opcodes.ACC_PRIVATE) != 0)
                        return new Finding(Status.INHERITANCE_REQUIRES_REVIEW, "Private ancestor declaration: " + currName);
                    return validateMember(use, curr, member, true);
                }
            }
            Set<String> itfs = new LinkedHashSet<>();
            collectInterfaces(currName, itfs, new HashSet<>());
            List<ClassInfo.Member> itfFields = new ArrayList<>();
            ClassInfo itfOwner = null;
            for (String itfName : itfs) {
                var itf = classes.get(itfName);
                if (itf == null) return new Finding(Status.HIERARCHY_INCOMPLETE, itfName);
                for (var m : itf.fields()) {
                    if (m.name().equals(ref.name()) && m.descriptor().equals(ref.descriptor())) {
                        itfFields.add(m);
                        itfOwner = itf;
                    }
                }
            }
            if (itfFields.size() > 1) {
                return new Finding(Status.INHERITANCE_REQUIRES_REVIEW, "Ambiguous interface fields: " + currName);
            }
            if (itfFields.size() == 1) {
                return validateMember(use, itfOwner, itfFields.get(0), true);
            }
            currName = curr.superName();
        }
        return new Finding(Status.MEMBER_MISSING, owner.name());
    }

    private Finding validateMember(ReferenceScanner.Use use, ClassInfo declaringOwner, ClassInfo.Member member, boolean field) {
        boolean expectedStatic = use.opcode() == Opcodes.INVOKESTATIC || use.opcode() == Opcodes.GETSTATIC || use.opcode() == Opcodes.PUTSTATIC;
        if (expectedStatic != ((member.access() & Opcodes.ACC_STATIC) != 0))
            return new Finding(Status.STATIC_MISMATCH, declaringOwner.name());

        var special = checkSpecial(use, declaringOwner, member);
        if (special != null) return special;

        var access = access(use, declaringOwner, member);
        if (access != null) return access;

        if (field && (member.access() & Opcodes.ACC_FINAL) != 0
                && (use.opcode() == Opcodes.PUTSTATIC || use.opcode() == Opcodes.PUTFIELD)) {
            if (use.handle() || !declaringOwner.name().equals(use.caller().owner()))
                return new Finding(Status.FINAL_WRITE_ILLEGAL, "Final field write outside declaring class or via handle: " + declaringOwner.name());
            String initializer = expectedStatic ? "<clinit>" : "<init>";
            if (!initializer.equals(use.caller().name()))
                return new Finding(Status.FINAL_WRITE_ILLEGAL, "Final field write outside " + initializer);
        }
        return new Finding(Status.DECLARATION_FOUND, declaringOwner.name());
    }

    private Finding checkSpecial(ReferenceScanner.Use use, ClassInfo owner, ClassInfo.Member member) {
        if (use.opcode() != Opcodes.INVOKESPECIAL) return null;
        var ref = use.target();
        if (ref.name().equals("<init>")) {
            if ((owner.access() & Opcodes.ACC_INTERFACE) != 0)
                return new Finding(Status.OWNER_KIND_MISMATCH, "Constructors cannot be invoked on interfaces: " + owner.name());
            return null;
        }
        String callerOwner = use.caller().owner();
        if ((owner.access() & Opcodes.ACC_INTERFACE) != 0) {
            if (!implementsInterface(callerOwner, owner.name(), new HashSet<>()))
                return new Finding(Status.ACCESS_DENIED, "Caller does not implement special target interface: " + owner.name());
            return null;
        }
        if ((member.access() & Opcodes.ACC_PRIVATE) != 0) {
            return null;
        }
        if (!callerOwner.equals(owner.name()) && !isSubclass(callerOwner, owner.name(), new HashSet<>()))
            return new Finding(Status.ACCESS_DENIED, "Special invocation caller is not a subclass: " + owner.name());
        return null;
    }

    private Finding access(ReferenceScanner.Use use, ClassInfo declaringOwner, ClassInfo.Member member) {
        String callerOwner = use.caller().owner();
        if ((member.access() & Opcodes.ACC_PUBLIC) != 0 || declaringOwner.name().equals(callerOwner)) return null;

        if ((member.access() & Opcodes.ACC_PRIVATE) != 0) {
            var callerInfo = classes.get(callerOwner);
            if (callerInfo != null) {
                boolean hasNestCaller = callerInfo.nestHost() != null || !callerInfo.nestMembers().isEmpty();
                boolean hasNestOwner = declaringOwner.nestHost() != null || !declaringOwner.nestMembers().isEmpty();
                if (hasNestCaller || hasNestOwner) {
                    if (!samePackage(declaringOwner.name(), callerOwner))
                        return new Finding(Status.ACCESS_DENIED, "Private member in another package: " + declaringOwner.name());
                    String hostCaller = callerInfo.nestHost() != null ? callerInfo.nestHost() : callerInfo.name();
                    String hostOwner = declaringOwner.nestHost() != null ? declaringOwner.nestHost() : declaringOwner.name();
                    if (hostCaller.equals(hostOwner)) {
                        var host = classes.get(hostCaller);
                        if (host != null) {
                            boolean callerInNest = hostCaller.equals(callerInfo.name()) || host.nestMembers().contains(callerInfo.name());
                            boolean ownerInNest = hostOwner.equals(declaringOwner.name()) || host.nestMembers().contains(declaringOwner.name());
                            if (callerInNest && ownerInNest) return null;
                        } else if (hostCaller.equals(callerInfo.name()) || hostOwner.equals(declaringOwner.name())) {
                            return null;
                        }
                    }
                    return new Finding(Status.ACCESS_DENIED, "Classes belong to different nests: " + callerOwner + " vs " + declaringOwner.name());
                }
            }
            return new Finding(Status.ACCESS_REQUIRES_REVIEW, "Cross-class private access requires validated nest membership: " + declaringOwner.name());
        }

        if (samePackage(declaringOwner.name(), callerOwner)) {
            if ((member.access() & Opcodes.ACC_PUBLIC) != 0) return null;
            return new Finding(Status.ACCESS_REQUIRES_REVIEW, "Non-public access requires runtime package/loader identity: " + declaringOwner.name());
        }

        if ((member.access() & Opcodes.ACC_PROTECTED) == 0)
            return new Finding(Status.ACCESS_DENIED, "Package-private member in another package: " + declaringOwner.name());

        if (!isSubclass(callerOwner, declaringOwner.name(), new HashSet<>()))
            return new Finding(Status.ACCESS_DENIED, "Protected member caller is not a subclass: " + declaringOwner.name());

        boolean isStatic = (member.access() & Opcodes.ACC_STATIC) != 0;
        if (isStatic || use.opcode() == Opcodes.INVOKESPECIAL) return null;
        String targetOwner = use.target().owner();
        if (targetOwner.equals(callerOwner) || isSubclass(targetOwner, callerOwner, new HashSet<>())) {
            return null;
        }
        return new Finding(Status.ACCESS_REQUIRES_REVIEW, "Protected access requires receiver/dispatch validation: " + declaringOwner.name());
    }

    private boolean isSubclass(String sub, String sup, Set<String> visited) {
        if (sub == null || !visited.add(sub)) return false;
        if (sub.equals(sup)) return true;
        if (sub.equals("java/lang/Object")) return false;
        var info = classes.get(sub);
        if (info == null || info.superName() == null) return false;
        if (info.superName().equals(sup)) return true;
        return isSubclass(info.superName(), sup, visited);
    }

    private boolean implementsInterface(String className, String interfaceName, Set<String> visited) {
        if (className == null || !visited.add(className)) return false;
        var info = classes.get(className);
        if (info == null) return false;
        for (String itf : info.interfaces()) {
            if (itf.equals(interfaceName)) return true;
            if (implementsInterface(itf, interfaceName, visited)) return true;
        }
        if (info.superName() != null) {
            if (implementsInterface(info.superName(), interfaceName, visited)) return true;
        }
        return false;
    }

    private static boolean samePackage(String left, String right) {
        return left.substring(0, Math.max(0, left.lastIndexOf('/')))
                .equals(right.substring(0, Math.max(0, right.lastIndexOf('/'))));
    }
}
