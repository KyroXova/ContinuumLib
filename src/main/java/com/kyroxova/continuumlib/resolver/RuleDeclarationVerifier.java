package com.kyroxova.continuumlib.resolver;

import com.kyroxova.continuumlib.bytecode.*;
import com.kyroxova.continuumlib.knowledge.rule.RulePack;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;
import java.util.*;

/** Checks explicit rule endpoints against supplied API declarations, not every mod reference.
 * Full caller accessibility, override contracts and JVM dispatch remain separate checks.
 */
public final class RuleDeclarationVerifier {
    public List<String> validate(Collection<RulePack> packs, Map<String, ClassInfo> source, Map<String, ClassInfo> target) {
        var problems = new ArrayList<String>();
        var sourceLookup = new MemberLookup(source);
        var targetLookup = new MemberLookup(target);
        var classes = new HashMap<String, String>();
        var members = new HashMap<MemberReference, MemberReference>();
        // Composition must already have passed ClassAdaptationPlan conflict checks.
        for (RulePack pack : packs) { classes.putAll(pack.classes()); members.putAll(pack.members()); }
        var types = new Remapper(Opcodes.ASM9) {
            @Override public String map(String name) { return classes.getOrDefault(name, name); }
        };
        for (RulePack pack : packs) {
            for (var mapping : pack.classes().entrySet()) {
                ClassInfo from = source.get(mapping.getKey()), to = target.get(mapping.getValue());
                if (from == null || to == null) problems.add(pack.id() + ": missing class endpoint " + mapping);
                else if ((from.access() & Opcodes.ACC_INTERFACE) != (to.access() & Opcodes.ACC_INTERFACE))
                    problems.add(pack.id() + ": class/interface migration needs a semantic strategy: " + mapping);
            }
            for (var mapping : pack.members().entrySet()) {
                var from = endpoint(sourceLookup, mapping.getKey(), pack.id(), "source", problems);
                var to = endpoint(targetLookup, mapping.getValue(), pack.id(), "target", problems);
                if (from != null && to != null) {
                    var fromOwner = source.get(mapping.getKey().owner());
                    var toOwner = target.get(mapping.getValue().owner());
                    if (fromOwner != null && toOwner != null && (fromOwner.access() & Opcodes.ACC_INTERFACE) != (toOwner.access() & Opcodes.ACC_INTERFACE))
                        problems.add(pack.id() + ": member owner changes class/interface invocation kind: " + mapping.getValue());
                    if ((from.access() & Opcodes.ACC_STATIC) != (to.access() & Opcodes.ACC_STATIC))
                        problems.add(pack.id() + ": static/instance mismatch for " + mapping.getKey());
                    if ((from.access() & Opcodes.ACC_PUBLIC) != 0 && (to.access() & Opcodes.ACC_PUBLIC) == 0)
                        problems.add(pack.id() + ": public member became non-public: " + mapping.getValue());
                }
            }
            for (CallBridge.Rule bridge : pack.bridges()) {
                var from = endpoint(sourceLookup, bridge.source(), pack.id(), "bridge source", problems);
                boolean staticCall = bridge.opcode() == Opcodes.INVOKESTATIC || bridge.opcode() == Opcodes.GETSTATIC || bridge.opcode() == Opcodes.PUTSTATIC;
                if (from != null && ((from.access() & Opcodes.ACC_STATIC) != 0) != staticCall)
                    problems.add(pack.id() + ": bridge invocation does not match source declaration: " + bridge.source());
                var hook = bridge.hook();
                var renamed = members.get(hook);
                var finalHook = new MemberReference(types.mapType(hook.owner()), renamed == null ? hook.name() : renamed.name(), types.mapMethodDesc(hook.descriptor()));
                var to = endpoint(targetLookup, finalHook, pack.id(), "bridge target", problems);
                var owner = target.get(finalHook.owner());
                if (to != null && ((to.access() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) != (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)
                        || owner == null || (owner.access() & Opcodes.ACC_INTERFACE) != 0 || (owner.access() & Opcodes.ACC_PUBLIC) == 0))
                    problems.add(pack.id() + ": bridge requires a public static hook on a class: " + finalHook);
            }
            for (ConstructorFactory.Rule factory : pack.constructors()) {
                var from = endpoint(sourceLookup, factory.source(), pack.id(), "constructor source", problems);
                if (from != null && (from.access() & Opcodes.ACC_STATIC) != 0)
                    problems.add(pack.id() + ": constructor source cannot be static: " + factory.source());
                var hook = factory.factory();
                var renamed = members.get(hook);
                var finalHook = new MemberReference(types.mapType(hook.owner()), renamed == null ? hook.name() : renamed.name(), types.mapMethodDesc(hook.descriptor()));
                var to = endpoint(targetLookup, finalHook, pack.id(), "constructor factory", problems);
                var owner = target.get(finalHook.owner());
                if (to != null && ((to.access() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) != (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)
                        || owner == null || (owner.access() & Opcodes.ACC_INTERFACE) != 0 || (owner.access() & Opcodes.ACC_PUBLIC) == 0))
                    problems.add(pack.id() + ": constructor factory requires a public static method on a public class: " + finalHook);
            }
        }
        return List.copyOf(problems);
    }
    private static ClassInfo.Member endpoint(MemberLookup lookup, MemberReference ref, String pack, String side, List<String> problems) {
        var result = lookup.find(ref);
        if (result.status() != MemberLookup.Status.FOUND) {
            problems.add(pack + ": " + side + " " + result.status() + " " + ref);
            return null;
        }
        return result.member();
    }
}
