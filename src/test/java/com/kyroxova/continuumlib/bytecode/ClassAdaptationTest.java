package com.kyroxova.continuumlib.bytecode;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ClassAdaptationTest {
    public static class ShapeApi {
        public static int oldShape(int neighbors) { return neighbors * 2; }
        public static int newShape(int neighbors) { return neighbors * 3; }
    }
    public static class ConnectedBlock {
        public int shape(int neighbors) {
            int connected = neighbors & 15;
            return ShapeApi.oldShape(connected) + 7;
        }
    }
    private byte[] bytes(Class<?> type) throws Exception {
        try (var input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            return input.readAllBytes();
        }
    }
    @Test void indexesRealClassMembersAndHierarchyWithoutLoadingThem() throws Exception {
        var info = new ClassInspector().inspect(bytes(ConnectedBlock.class));
        assertEquals(ConnectedBlock.class.getName().replace('.', '/'), info.name());
        assertEquals("java/lang/Object", info.superName());
        assertTrue(info.methods().stream().anyMatch(m -> m.name().equals("shape") && m.descriptor().equals("(I)I")));
    }
    @Test void adaptsExactCallWhilePreservingCustomConnectionLogic() throws Exception {
        String owner = ShapeApi.class.getName().replace('.', '/');
        var from = new MemberReference(owner, "oldShape", "(I)I");
        var to = new MemberReference(owner, "newShape", "(I)I");
        byte[] original = bytes(ConnectedBlock.class);
        byte[] transformed = new ClassAdapter(Map.of(), Map.of(from, to)).adapt(original);
        Class<?> adapted = new ClassLoader(getClass().getClassLoader()) {
            Class<?> loadAdapted() { return defineClass(null, transformed, 0, transformed.length); }
        }.loadAdapted();
        Object block = adapted.getConstructor().newInstance();
        assertEquals(16, adapted.getMethod("shape", int.class).invoke(block, 19));
        assertEquals(13, new ConnectedBlock().shape(19));
        assertArrayEquals(original, bytes(ConnectedBlock.class));
    }
    @Test void rejectsDescriptorChangesWithoutAnExplicitBridge() {
        var from = new MemberReference("a/Block", "shape", "(I)I");
        var to = new MemberReference("b/Block", "shape", "(II)I");
        assertThrows(IllegalArgumentException.class, () -> new ClassAdapter(Map.of(), Map.of(from, to)));
    }
}
