package com.kyroxova.continuumlib.bytecode;

import org.objectweb.asm.*;
import java.util.*;

/** Redirects explicitly approved calls and field accesses to public static hooks in non-interface classes.
 * Hooks carry semantic migrations; the transformer only guarantees the stack shape.
 * Apply before namespace renames. Constructors and super calls need other strategies.
 */
public final class CallBridge {
    public record Rule(MemberReference source, int opcode, MemberReference hook) {
        public Rule {
            Objects.requireNonNull(source);
            Objects.requireNonNull(hook);
            if (source.name().startsWith("<") || hook.name().startsWith("<"))
                throw new IllegalArgumentException("Initializers cannot use ordinary bridges");
            String expected;
            if (opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD || opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC) {
                if (!source.descriptor().matches("\\[{0,255}(?:[BCDFIJSZ]|L[^.;\\[]+;)"))
                    throw new IllegalArgumentException("Field bridges require one valid field descriptor");
                Type field = Type.getType(source.descriptor());
                if (field.getSort() == Type.METHOD || field.getSort() == Type.VOID)
                    throw new IllegalArgumentException("Field bridges require a field descriptor");
                boolean write = opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC;
                var arguments = new ArrayList<Type>();
                if (opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD) arguments.add(Type.getObjectType(source.owner()));
                if (write) arguments.add(field);
                expected = Type.getMethodDescriptor(write ? Type.VOID_TYPE : field, arguments.toArray(Type[]::new));
            } else {
                if (opcode != Opcodes.INVOKESTATIC && opcode != Opcodes.INVOKEVIRTUAL && opcode != Opcodes.INVOKEINTERFACE)
                    throw new IllegalArgumentException("Only ordinary method calls and field accesses support bridges");
                Type method = Type.getMethodType(source.descriptor());
                Type[] arguments = method.getArgumentTypes();
                if (opcode != Opcodes.INVOKESTATIC) {
                    Type[] withReceiver = new Type[arguments.length + 1];
                    withReceiver[0] = Type.getObjectType(source.owner());
                    System.arraycopy(arguments, 0, withReceiver, 1, arguments.length);
                    arguments = withReceiver;
                }
                expected = Type.getMethodDescriptor(method.getReturnType(), arguments);
            }
            if (!expected.equals(hook.descriptor())) {
                throw new IllegalArgumentException("Bridge must preserve the operand stack: expected " + expected);
            }
        }
    }
    private record Key(MemberReference member, int opcode) {}
    private final Map<Key, Rule> rules;
    public CallBridge(Collection<Rule> rules) {
        Map<Key, Rule> indexed = new HashMap<>();
        for (Rule rule : rules) {
            if (indexed.putIfAbsent(new Key(rule.source(), rule.opcode()), rule) != null)
                throw new IllegalArgumentException("Conflicting bridge for " + rule.source());
        }
        this.rules = Map.copyOf(indexed);
    }
    public byte[] adapt(byte[] original) {
        if (rules.isEmpty()) return original.clone();
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(original).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, desc, signature, exceptions)) {
                    @Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                        Rule rule = rules.get(new Key(new MemberReference(owner, name, desc), opcode));
                        if (rule == null) super.visitMethodInsn(opcode, owner, name, desc, itf);
                        else super.visitMethodInsn(Opcodes.INVOKESTATIC, rule.hook().owner(), rule.hook().name(), rule.hook().descriptor(), false);
                    }
                    @Override public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                        Rule rule = rules.get(new Key(new MemberReference(owner, name, desc), opcode));
                        if (rule == null) super.visitFieldInsn(opcode, owner, name, desc);
                        else super.visitMethodInsn(Opcodes.INVOKESTATIC, rule.hook().owner(), rule.hook().name(), rule.hook().descriptor(), false);
                    }
                    @Override public void visitLdcInsn(Object value) { super.visitLdcInsn(adaptConstant(value)); }
                    @Override public void visitInvokeDynamicInsn(String name, String desc, Handle bootstrap, Object... args) {
                        Object[] mapped = Arrays.stream(args).map(CallBridge.this::adaptConstant).toArray();
                        super.visitInvokeDynamicInsn(name, desc, (Handle) adaptConstant(bootstrap), mapped);
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }
    private Object adaptConstant(Object value) {
        if (value instanceof Handle h) {
            int opcode = switch (h.getTag()) {
                case Opcodes.H_INVOKESTATIC -> Opcodes.INVOKESTATIC;
                case Opcodes.H_INVOKEVIRTUAL -> Opcodes.INVOKEVIRTUAL;
                case Opcodes.H_INVOKEINTERFACE -> Opcodes.INVOKEINTERFACE;
                case Opcodes.H_GETFIELD -> Opcodes.GETFIELD;
                case Opcodes.H_PUTFIELD -> Opcodes.PUTFIELD;
                case Opcodes.H_GETSTATIC -> Opcodes.GETSTATIC;
                case Opcodes.H_PUTSTATIC -> Opcodes.PUTSTATIC;
                default -> -1;
            };
            Rule rule = rules.get(new Key(new MemberReference(h.getOwner(), h.getName(), h.getDesc()), opcode));
            if (rule != null) return new Handle(Opcodes.H_INVOKESTATIC, rule.hook().owner(), rule.hook().name(), rule.hook().descriptor(), false);
        } else if (value instanceof ConstantDynamic c) {
            Object[] args = new Object[c.getBootstrapMethodArgumentCount()];
            for (int i = 0; i < args.length; i++) args[i] = adaptConstant(c.getBootstrapMethodArgument(i));
            return new ConstantDynamic(c.getName(), c.getDescriptor(), (Handle) adaptConstant(c.getBootstrapMethod()), args);
        }
        return value;
    }
}
