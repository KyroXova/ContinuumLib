package com.kyroxova.continuumlib.bytecode;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import java.util.*;

/** Explicit constructor-to-static-factory migrations. No guessing from matching class names.
 * Only canonical straight-line NEW/DUP allocations are accepted; chaining and escaping
 * uninitialized references require a different semantic strategy.
 */
public final class ConstructorFactory {
    public record Rule(MemberReference source, MemberReference factory) {
        public Rule {
            Objects.requireNonNull(source); Objects.requireNonNull(factory);
            Type constructor = Type.getMethodType(source.descriptor());
            String expected = Type.getMethodDescriptor(Type.getObjectType(source.owner()), constructor.getArgumentTypes());
            if (!source.name().equals("<init>") || constructor.getReturnType() != Type.VOID_TYPE
                    || factory.name().startsWith("<") || !expected.equals(factory.descriptor()))
                throw new IllegalArgumentException("Constructor factory must retain arguments and return the constructed type: " + expected);
        }
    }
    private final Map<MemberReference, Rule> rules;
    public ConstructorFactory(Collection<Rule> rules) {
        var indexed = new HashMap<MemberReference, Rule>();
        for (var rule : rules) if (indexed.putIfAbsent(rule.source(), rule) != null)
            throw new IllegalArgumentException("Duplicate constructor factory: " + rule.source());
        this.rules = Map.copyOf(indexed);
    }
    public byte[] adapt(byte[] original) {
        if (rules.isEmpty()) return original.clone();
        var node = new ClassNode(Opcodes.ASM9);
        new ClassReader(original).accept(node, 0);
        for (MethodNode method : node.methods) adapt(node.name, method);
        var writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }
    private void adapt(String owner, MethodNode method) {
        var calls = new ArrayList<MethodInsnNode>();
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && rule(call) != null) calls.add(call);
            else if (instruction instanceof LdcInsnNode ldc) ldc.cst = constant(ldc.cst);
            else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                dynamic.bsm = (Handle) constant(dynamic.bsm);
                for (int i = 0; i < dynamic.bsmArgs.length; i++) dynamic.bsmArgs[i] = constant(dynamic.bsmArgs[i]);
            }
        }
        if (calls.isEmpty()) return;
        Frame<SourceValue>[] frames;
        try {
            // Keep allocation identity through DUP/loads, instead of assigning a new source
            // instruction to every copy. Merge retains multiple origins and is rejected below.
            var interpreter = new SourceInterpreter(Opcodes.ASM9) {
                @Override public SourceValue copyOperation(AbstractInsnNode instruction, SourceValue value) { return value; }
            };
            frames = new Analyzer<>(interpreter).analyze(owner, method);
        } catch (AnalyzerException e) {
            throw new IllegalArgumentException("Cannot analyze constructor allocations in " + owner + "." + method.name + method.desc, e);
        }
        var edits = new ArrayList<Runnable>();
        var claimed = Collections.newSetFromMap(new IdentityHashMap<AbstractInsnNode, Boolean>());
        for (var call : calls) {
            Rule rule = rule(call);
            int callIndex = method.instructions.indexOf(call);
            var frame = frames[callIndex];
            int receiver = frame == null ? -1 : frame.getStackSize() - Type.getArgumentTypes(call.desc).length - 1;
            if (call.getOpcode() != Opcodes.INVOKESPECIAL || receiver < 1)
                throw unsupported(owner, method, "not an ordinary allocation (super/this chaining is unsupported)");
            var origins = frame.getStack(receiver).insns;
            if (origins.size() != 1 || !(origins.iterator().next() instanceof TypeInsnNode allocation)
                    || allocation.getOpcode() != Opcodes.NEW || !allocation.desc.equals(call.owner))
                throw unsupported(owner, method, "constructor receiver is not one NEW allocation");
            var duplicate = allocation.getNext();
            while (duplicate != null && duplicate.getOpcode() < 0) duplicate = duplicate.getNext();
            if (duplicate == null || duplicate.getOpcode() != Opcodes.DUP || !claimed.add(allocation))
                throw unsupported(owner, method, "requires one canonical NEW/DUP pair");
            int begin = method.instructions.indexOf(allocation), dupIndex = method.instructions.indexOf(duplicate);
            if (begin >= callIndex || !origin(frame.getStack(receiver - 1), allocation))
                throw unsupported(owner, method, "allocation aliases do not match NEW/DUP");
            for (int i = begin + 1; i <= callIndex; i++) {
                var instruction = method.instructions.get(i);
                if (instruction instanceof FrameNode || instruction instanceof JumpInsnNode
                        || instruction instanceof TableSwitchInsnNode || instruction instanceof LookupSwitchInsnNode)
                    throw unsupported(owner, method, "control-flow boundary inside allocation");
                var current = frames[i];
                if (current == null) throw unsupported(owner, method, "unreachable allocation instruction");
                for (int local = 0; local < current.getLocals(); local++)
                    if (current.getLocal(local).insns.contains(allocation))
                        throw unsupported(owner, method, "uninitialized allocation stored in a local");
                int count = 0;
                for (int slot = 0; slot < current.getStackSize(); slot++) {
                    if (current.getStack(slot).insns.contains(allocation)) {
                        if (!origin(current.getStack(slot), allocation)) throw unsupported(owner, method, "merged allocation origins");
                        count++;
                        if (i > dupIndex && slot != receiver - 1 && slot != receiver)
                            throw unsupported(owner, method, "uninitialized stack aliases moved");
                    }
                }
                if (count != (i <= dupIndex ? 1 : 2)) throw unsupported(owner, method, "uninitialized aliases escaped");
            }
            AbstractInsnNode dup = duplicate;
            edits.add(() -> {
                method.instructions.remove(allocation); method.instructions.remove(dup);
                method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, rule.factory().owner(),
                        rule.factory().name(), rule.factory().descriptor(), false));
            });
        }
        edits.forEach(Runnable::run);
    }
    private Rule rule(MethodInsnNode call) { return rules.get(new MemberReference(call.owner, call.name, call.desc)); }
    private static boolean origin(SourceValue value, AbstractInsnNode instruction) {
        return value.insns.size() == 1 && value.insns.contains(instruction);
    }
    private static IllegalArgumentException unsupported(String owner, MethodNode method, String reason) {
        return new IllegalArgumentException("Unsupported constructor migration in " + owner + "." + method.name + method.desc + ": " + reason);
    }
    private Object constant(Object value) {
        if (value instanceof Handle handle && handle.getTag() == Opcodes.H_NEWINVOKESPECIAL) {
            var rule = rules.get(new MemberReference(handle.getOwner(), handle.getName(), handle.getDesc()));
            if (rule != null) return new Handle(Opcodes.H_INVOKESTATIC, rule.factory().owner(), rule.factory().name(), rule.factory().descriptor(), false);
        } else if (value instanceof ConstantDynamic dynamic) {
            Object[] arguments = new Object[dynamic.getBootstrapMethodArgumentCount()];
            for (int i = 0; i < arguments.length; i++) arguments[i] = constant(dynamic.getBootstrapMethodArgument(i));
            return new ConstantDynamic(dynamic.getName(), dynamic.getDescriptor(), (Handle) constant(dynamic.getBootstrapMethod()), arguments);
        }
        return value;
    }
}
