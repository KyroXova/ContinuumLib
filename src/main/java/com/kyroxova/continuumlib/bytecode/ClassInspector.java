package com.kyroxova.continuumlib.bytecode;

import org.objectweb.asm.*;
import java.util.ArrayList;
import java.util.List;

/** Reads the actual class file, without running static initializers or linking game classes. */
public final class ClassInspector {
    public ClassInfo inspect(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        List<ClassInfo.Member> fields = new ArrayList<>();
        List<ClassInfo.Member> methods = new ArrayList<>();
        List<String> nestMembers = new ArrayList<>();
        String[] nestHost = new String[1];
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public void visitNestHost(String host) { nestHost[0] = host; }
            @Override public void visitNestMember(String nestMember) { nestMembers.add(nestMember); }
            @Override public FieldVisitor visitField(int access, String name, String descriptor,
                                                      String signature, Object value) {
                fields.add(new ClassInfo.Member(name, descriptor, access, signature));
                return null;
            }
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                        String signature, String[] exceptions) {
                methods.add(new ClassInfo.Member(name, descriptor, access, signature));
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new ClassInfo(reader.getClassName(), reader.getSuperName(), List.of(reader.getInterfaces()),
                reader.getAccess(), fields, methods, nestHost[0], nestMembers);
    }
}
