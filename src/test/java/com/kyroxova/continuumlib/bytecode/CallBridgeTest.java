package com.kyroxova.continuumlib.bytecode;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import java.util.List;
import java.util.function.IntUnaryOperator;
import static org.junit.jupiter.api.Assertions.*;

class CallBridgeTest {
    public static class FieldApi { public int value; public static long count; }
    public static class FieldHooks {
        public static int read(FieldApi receiver) { return receiver.value + 1; }
        public static void write(FieldApi receiver, int value) { receiver.value = value * 2; }
        public static long readCount() { return FieldApi.count + 2; }
        public static void writeCount(long value) { FieldApi.count = value * 3; }
    }
    public static class FieldConsumer {
        public int instance() { var receiver = new FieldApi(); receiver.value = 4; return receiver.value; }
        public long shared() { FieldApi.count = 5; return FieldApi.count; }
    }
    @Test void redirectsFieldReadsAndWritesToStackCompatibleAccessors() throws Exception {
        String owner = name(FieldApi.class), hooks = name(FieldHooks.class);
        var value = new MemberReference(owner, "value", "I");
        var count = new MemberReference(owner, "count", "J");
        var bridge = new CallBridge(List.of(
                new CallBridge.Rule(value, Opcodes.GETFIELD, new MemberReference(hooks, "read", "(L" + owner + ";)I")),
                new CallBridge.Rule(value, Opcodes.PUTFIELD, new MemberReference(hooks, "write", "(L" + owner + ";I)V")),
                new CallBridge.Rule(count, Opcodes.GETSTATIC, new MemberReference(hooks, "readCount", "()J")),
                new CallBridge.Rule(count, Opcodes.PUTSTATIC, new MemberReference(hooks, "writeCount", "(J)V"))));
        byte[] original;
        try (var input = FieldConsumer.class.getResourceAsStream("/" + name(FieldConsumer.class) + ".class")) { original = input.readAllBytes(); }
        byte[] adapted = bridge.adapt(original);
        Class<?> type = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() { return defineClass(null, adapted, 0, adapted.length); }
        }.define();
        Object consumer = type.getConstructor().newInstance();
        assertEquals(9, type.getMethod("instance").invoke(consumer));
        assertEquals(17L, type.getMethod("shared").invoke(consumer));
        assertEquals(4, new FieldConsumer().instance());
        var writer = new org.objectweb.asm.ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "fixture/FieldHandle", null, "java/lang/Object", null);
        var getter = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getter", "()Ljava/lang/invoke/MethodHandle;", null, null);
        getter.visitCode(); getter.visitLdcInsn(new org.objectweb.asm.Handle(Opcodes.H_GETFIELD, owner, "value", "I", false));
        getter.visitInsn(Opcodes.ARETURN); getter.visitMaxs(1, 0); getter.visitEnd(); writer.visitEnd();
        byte[] handleBytes = bridge.adapt(writer.toByteArray());
        Class<?> handles = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() { return defineClass(null, handleBytes, 0, handleBytes.length); }
        }.define();
        var handle = (java.lang.invoke.MethodHandle) handles.getMethod("getter").invoke(null);
        FieldApi receiver = new FieldApi(); receiver.value = 10;
        try { assertEquals(11, (int) handle.invokeExact(receiver)); }
        catch (Throwable failure) { throw new AssertionError(failure); }
    }
    public static class OldApi { public int size(int value) { return value * 2; } }
    public static class Hooks { public static int size(OldApi receiver, int value) { return value * 3; } }
    public static class Consumer {
        public int run() { return new OldApi().size(4) + 7; }
        public int lambda() { IntUnaryOperator op = new OldApi()::size; return op.applyAsInt(4); }
    }
    private static String name(Class<?> c) { return c.getName().replace('.', '/'); }
    @Test void redirectsInstanceCallsAndBoundHandlesToTypedStaticBridge() throws Exception {
        var rule = new CallBridge.Rule(new MemberReference(name(OldApi.class), "size", "(I)I"),
                Opcodes.INVOKEVIRTUAL, new MemberReference(name(Hooks.class), "size", "(L" + name(OldApi.class) + ";I)I"));
        byte[] original;
        try (var in = Consumer.class.getResourceAsStream("/" + name(Consumer.class) + ".class")) { original = in.readAllBytes(); }
        byte[] adapted = new CallBridge(List.of(rule)).adapt(original);
        Class<?> type = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() { return defineClass(null, adapted, 0, adapted.length); }
        }.define();
        Object consumer = type.getConstructor().newInstance();
        assertEquals(19, type.getMethod("run").invoke(consumer));
        assertEquals(12, type.getMethod("lambda").invoke(consumer));
        assertEquals(15, new Consumer().run());
    }
    @Test void rejectsBridgesWithWrongOperandStackContractAndConstructors() {
        var from = new MemberReference("api/Block", "shape", "(I)I");
        assertThrows(IllegalArgumentException.class, () -> new CallBridge.Rule(from, Opcodes.INVOKEVIRTUAL,
                new MemberReference("hooks/Bridge", "shape", "(I)I")));
        assertThrows(IllegalArgumentException.class, () -> new CallBridge.Rule(
                new MemberReference("api/Block", "<init>", "()V"), Opcodes.INVOKESPECIAL,
                new MemberReference("hooks/Bridge", "create", "(Lapi/Block;)V")));
        assertThrows(IllegalArgumentException.class, () -> new CallBridge.Rule(
                new MemberReference("api/Block", "value", "I"), Opcodes.GETFIELD,
                new MemberReference("hooks/Bridge", "read", "()I")));
        assertThrows(IllegalArgumentException.class, () -> new CallBridge.Rule(
                new MemberReference("api/Block", "value", "J"), Opcodes.PUTSTATIC,
                new MemberReference("hooks/Bridge", "write", "(J)J")));
        assertThrows(IllegalArgumentException.class, () -> new CallBridge.Rule(
                new MemberReference("api/Block", "value", "V"), Opcodes.GETSTATIC,
                new MemberReference("hooks/Bridge", "read", "()V")));
        assertThrows(IllegalArgumentException.class, () -> new CallBridge.Rule(from, Opcodes.GETFIELD,
                new MemberReference("hooks/Bridge", "read", "(Lapi/Block;)I")));
        assertThrows(IllegalArgumentException.class, () -> new CallBridge.Rule(
                new MemberReference("api/Block", "value", "II"), Opcodes.GETSTATIC,
                new MemberReference("hooks/Bridge", "read", "()I")));
    }
}
