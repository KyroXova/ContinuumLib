package com.kyroxova.continuumlib.bytecode;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.LongFunction;
import static org.junit.jupiter.api.Assertions.*;

class ConstructorFactoryTest {
    public static class Value {
        public final long number;
        public Value(long number) { this.number = number; }
        public static Value create(long number) { return new Value(number * 3); }
    }
    public static class Consumer {
        public long ordinary() { return new Value(4L).number + 7; }
        public long nested() { return new Value(new Value(2L).number + 1).number; }
        public long reference() { LongFunction<Value> factory = Value::new; return factory.apply(5).number; }
    }
    public static class Subclass extends Value { public Subclass() { super(4); } }
    public static class Branched { public Value run(boolean flag) { return new Value(flag ? 1 : 2); } }
    static String name(Class<?> type) { return type.getName().replace('.', '/'); }
    static byte[] bytes(Class<?> type) throws Exception {
        try (var input = type.getResourceAsStream("/" + name(type) + ".class")) { return input.readAllBytes(); }
    }
    private ConstructorFactory adapter() {
        return new ConstructorFactory(List.of(new ConstructorFactory.Rule(
                new MemberReference(name(Value.class), "<init>", "(J)V"),
                new MemberReference(name(Value.class), "create", "(J)L" + name(Value.class) + ";"))));
    }
    @Test void rewritesAllocationAndNestedWideArgumentsToExecutableFactoryCalls() throws Exception {
        byte[] adapted = adapter().adapt(bytes(Consumer.class));
        Class<?> type = new ClassLoader(getClass().getClassLoader()) {
            Class<?> load() { return defineClass(null, adapted, 0, adapted.length); }
        }.load();
        Object instance = type.getConstructor().newInstance();
        assertEquals(19L, type.getMethod("ordinary").invoke(instance));
        assertEquals(21L, type.getMethod("nested").invoke(instance));
        assertEquals(15L, type.getMethod("reference").invoke(instance));
        assertEquals(11L, new Consumer().ordinary());
        assertTrue(new ReferenceScanner().scan(adapted).stream().noneMatch(use ->
                use.target().owner().equals(name(Value.class)) && use.target().name().equals("<init>")));
    }
    @Test void refusesSuperConstructorCallsInsteadOfWritingInvalidBytecode() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> adapter().adapt(bytes(Subclass.class)));
    }
    @Test void refusesFactoriesWithDifferentArgumentsOrReturnType() {
        var constructor = new MemberReference("api/Value", "<init>", "(J)V");
        assertThrows(IllegalArgumentException.class, () -> new ConstructorFactory.Rule(constructor,
                new MemberReference("api/Value", "create", "(I)Lapi/Value;")));
        assertThrows(IllegalArgumentException.class, () -> new ConstructorFactory.Rule(constructor,
                new MemberReference("api/Value", "create", "(J)Ljava/lang/Object;")));
    }
    @Test void refusesControlFlowAcrossAnUninitializedAllocation() throws Exception {
        var error = assertThrows(IllegalArgumentException.class, () -> adapter().adapt(bytes(Branched.class)));
        assertTrue(error.getMessage().contains("control-flow boundary"));
    }
    @Test void refusesExtraOrLocalUninitializedAliases() {
        for (boolean local : List.of(false, true)) {
            var writer = new org.objectweb.asm.ClassWriter(0);
            writer.visit(org.objectweb.asm.Opcodes.V17, 1, "example/Alias", null, "java/lang/Object", null);
            var method = writer.visitMethod(9, "run", "()L" + name(Value.class) + ";", null, null);
            method.visitCode(); method.visitTypeInsn(org.objectweb.asm.Opcodes.NEW, name(Value.class));
            method.visitInsn(org.objectweb.asm.Opcodes.DUP);
            if (local) { method.visitVarInsn(org.objectweb.asm.Opcodes.ASTORE, 0); method.visitInsn(org.objectweb.asm.Opcodes.DUP); }
            else method.visitInsn(org.objectweb.asm.Opcodes.DUP);
            method.visitLdcInsn(3L);
            method.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESPECIAL, name(Value.class), "<init>", "(J)V", false);
            if (!local) method.visitInsn(org.objectweb.asm.Opcodes.POP);
            method.visitInsn(org.objectweb.asm.Opcodes.ARETURN); method.visitMaxs(5, 1); method.visitEnd(); writer.visitEnd();
            assertThrows(IllegalArgumentException.class, () -> adapter().adapt(writer.toByteArray()));
        }
    }
}
