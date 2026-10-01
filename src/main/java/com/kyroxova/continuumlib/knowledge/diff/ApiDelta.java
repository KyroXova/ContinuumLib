package com.kyroxova.continuumlib.knowledge.diff;

import com.kyroxova.continuumlib.bytecode.ClassInfo;
import java.util.*;

/** Exact declared-surface differences, never inferred semantic replacements.
 * Compare artifacts in the intended namespaces; this does not perform deobfuscation.
 */
public final class ApiDelta {
    public enum Kind { CLASS_ADDED, CLASS_REMOVED, CLASS_ACCESS_CHANGED, HIERARCHY_CHANGED,
        METHOD_ADDED, METHOD_REMOVED, METHOD_CHANGED, FIELD_ADDED, FIELD_REMOVED, FIELD_CHANGED }
    public record Change(Kind kind, String owner, String name, String descriptor, String before, String after) {}
    public List<Change> compare(Map<String, ClassInfo> source, Map<String, ClassInfo> target) {
        List<Change> result = new ArrayList<>();
        Set<String> owners = new TreeSet<>(source.keySet()); owners.addAll(target.keySet());
        for (String owner : owners) {
            ClassInfo from = source.get(owner), to = target.get(owner);
            if (from == null) { result.add(new Change(Kind.CLASS_ADDED, owner, "", "", "", "present")); continue; }
            if (to == null) { result.add(new Change(Kind.CLASS_REMOVED, owner, "", "", "present", "")); continue; }
            if (from.access() != to.access()) result.add(new Change(Kind.CLASS_ACCESS_CHANGED, owner, "", "", Integer.toString(from.access()), Integer.toString(to.access())));
            String fromHierarchy = hierarchy(from), toHierarchy = hierarchy(to);
            if (!fromHierarchy.equals(toHierarchy)) result.add(new Change(Kind.HIERARCHY_CHANGED, owner, "", "", fromHierarchy, toHierarchy));
            members(owner, from.fields(), to.fields(), false, result);
            members(owner, from.methods(), to.methods(), true, result);
        }
        return List.copyOf(result);
    }
    private static String hierarchy(ClassInfo info) { return info.superName() + " " + String.join(",", info.interfaces()); }
    private static void members(String owner, List<ClassInfo.Member> before, List<ClassInfo.Member> after, boolean method, List<Change> out) {
        Map<String, ClassInfo.Member> source = index(before), target = index(after);
        Set<String> signatures = new TreeSet<>(source.keySet()); signatures.addAll(target.keySet());
        for (String signature : signatures) {
            ClassInfo.Member from = source.get(signature), to = target.get(signature);
            ClassInfo.Member identity = from == null ? to : from;
            if (Objects.equals(from, to)) continue;
            Kind kind = from == null ? (method ? Kind.METHOD_ADDED : Kind.FIELD_ADDED)
                    : to == null ? (method ? Kind.METHOD_REMOVED : Kind.FIELD_REMOVED)
                    : (method ? Kind.METHOD_CHANGED : Kind.FIELD_CHANGED);
            out.add(new Change(kind, owner, identity.name(), identity.descriptor(), shape(from), shape(to)));
        }
    }
    private static Map<String, ClassInfo.Member> index(List<ClassInfo.Member> members) {
        Map<String, ClassInfo.Member> indexed = new TreeMap<>();
        for (var member : members)
            if (indexed.putIfAbsent(member.name() + "\u0000" + member.descriptor(), member) != null)
                throw new IllegalArgumentException("Duplicate API member: " + member);
        return indexed;
    }
    private static String shape(ClassInfo.Member member) { return member == null ? "" : "access=" + member.access() + ";signature=" + member.signature(); }
}
