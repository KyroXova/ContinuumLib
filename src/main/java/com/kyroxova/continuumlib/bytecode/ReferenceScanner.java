package com.kyroxova.continuumlib.bytecode;

import org.objectweb.asm.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Collections;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/** Instruction-level member dependencies, including bootstrap and constant handles.
 * Does not infer reflective accesses or compatibility from member names.
 */
public final class ReferenceScanner {
    /** Includes hierarchy, descriptors, signatures, annotations, instructions and stack map types.
     * String-based reflection and names in resources need separate policies.
     */
    public Set<String> types(byte[] bytecode) {
        Set<String> types = new TreeSet<>();
        var collector = new Remapper(Opcodes.ASM9) {
            @Override public String map(String internalName) {
                types.add(internalName);
                return internalName;
            }
        };
        new ClassReader(bytecode).accept(new ClassRemapper(new ClassWriter(0), collector), 0);
        return Collections.unmodifiableSet(types);
    }
    public record Use(MemberReference caller, MemberReference target, int opcode,
                      boolean interfaceOwner, boolean handle, int line) {}

    public List<Use> scan(byte[] bytecode) {
        List<Use> uses = new ArrayList<>();
        new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
            private String owner;
            @Override public void visit(int version, int access, String name, String signature,
                                        String parent, String[] interfaces) { owner = name; }
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                       String signature, String[] exceptions) {
                var caller = new MemberReference(owner, name, descriptor);
                return new MethodVisitor(Opcodes.ASM9) {
                    private int line = -1;
                    @Override public void visitLineNumber(int number, Label start) { line = number; }
                    private void add(String targetOwner, String targetName, String desc, int opcode,
                                     boolean itf, boolean handle) {
                        uses.add(new Use(caller, new MemberReference(targetOwner, targetName, desc),
                                opcode, itf, handle, line));
                    }
                    @Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                        add(owner, name, desc, opcode, itf, false);
                    }
                    @Override public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                        add(owner, name, desc, opcode, false, false);
                    }
                    private void constant(Object value) {
                        if (value instanceof Handle h) {
                            int opcode = switch (h.getTag()) {
                                case Opcodes.H_GETFIELD -> Opcodes.GETFIELD;
                                case Opcodes.H_GETSTATIC -> Opcodes.GETSTATIC;
                                case Opcodes.H_PUTFIELD -> Opcodes.PUTFIELD;
                                case Opcodes.H_PUTSTATIC -> Opcodes.PUTSTATIC;
                                case Opcodes.H_INVOKESTATIC -> Opcodes.INVOKESTATIC;
                                case Opcodes.H_INVOKEINTERFACE -> Opcodes.INVOKEINTERFACE;
                                case Opcodes.H_INVOKESPECIAL, Opcodes.H_NEWINVOKESPECIAL -> Opcodes.INVOKESPECIAL;
                                case Opcodes.H_INVOKEVIRTUAL -> Opcodes.INVOKEVIRTUAL;
                                default -> throw new IllegalArgumentException("Unknown handle kind " + h.getTag());
                            };
                            add(h.getOwner(), h.getName(), h.getDesc(), opcode, h.isInterface(), true);
                        } else if (value instanceof ConstantDynamic c) {
                            constant(c.getBootstrapMethod());
                            for (int i = 0; i < c.getBootstrapMethodArgumentCount(); i++) constant(c.getBootstrapMethodArgument(i));
                        }
                    }
                    @Override public void visitLdcInsn(Object value) { constant(value); }
                    @Override public void visitInvokeDynamicInsn(String name, String desc, Handle bootstrap, Object... args) {
                        constant(bootstrap);
                        for (Object arg : args) constant(arg);
                    }
                };
            }
        }, ClassReader.SKIP_FRAMES);
        return List.copyOf(uses);
    }
}
