package com.kyroxova.continuumlib.bytecode;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HierarchyAdaptationTest {
    public static class PlatformCalls extends ArrayList<String> {
        public int run() { return "abc".length() + List.of(1, 2).size(); }
        public int length() { return 8; }
    }
    @Test void gameRenamesDoNotRequireUnrelatedPlatformDeclarations() throws Exception {
        var graph = Map.of(name(PlatformCalls.class), new ClassInspector().inspect(bytes(PlatformCalls.class)));
        var rules = Map.of(new MemberReference("game/Shape", "length", "()I"), new MemberReference("game/Shape", "a", "()I"),
                new MemberReference("game/Container", "size", "()I"), new MemberReference("game/Container", "b", "()I"));
        byte[] adapted = new ClassAdapter(Map.of(), rules, graph).adapt(bytes(PlatformCalls.class));
        Class<?> type = new ClassLoader(getClass().getClassLoader()) { Class<?> define() { return defineClass(null, adapted, 0, adapted.length); } }.define();
        Object instance = type.getConstructor().newInstance();
        assertEquals(5, type.getMethod("run").invoke(instance));
        assertEquals(8, type.getMethod("length").invoke(instance));
    }
    public static class SourceBase { public int oldShape(int n) { return n * 2; } public int oldValue = 4; }
    public static class TargetBase { public int shape(int n) { return n * 3; } public int value = 4; public int dispatch() { return shape(4); } }
    public static class CustomBlock extends SourceBase {
        @Override public int oldShape(int n) { return super.oldShape(n) + 7; }
        public int read() { return oldValue; }
    }
    public interface SourceFunction { int oldCall(int value); }
    public interface TargetFunction { int call(int value); }
    public static class LambdaUser { public int run() { SourceFunction function = n -> n + 7; return function.oldCall(4); } }
    public static class PrivateBase { private int oldMethod() { return 1; } }
    public static class PrivateTarget { private int method() { return 1; } }
    public static class IndependentMethod extends PrivateBase { public int oldMethod() { return 7; } }
    public interface Left { int oldMethod(); }
    public interface Right { int oldMethod(); }
    public static class Both implements Left, Right { public int oldMethod() { return 4; } }
    @Test void doesNotRenameAnUnrelatedMethodThatMatchesAPrivateAncestor() throws Exception {
        var inspector = new ClassInspector();
        var graph = Map.of(name(PrivateBase.class), inspector.inspect(bytes(PrivateBase.class)), name(IndependentMethod.class), inspector.inspect(bytes(IndependentMethod.class)));
        var rule = Map.of(new MemberReference(name(PrivateBase.class), "oldMethod", "()I"), new MemberReference(name(PrivateTarget.class), "method", "()I"));
        var adapted = new ClassAdapter(Map.of(name(PrivateBase.class), name(PrivateTarget.class)), rule, graph).adapt(bytes(IndependentMethod.class));
        Class<?> type = new ClassLoader(getClass().getClassLoader()) { Class<?> define() { return defineClass(null, adapted, 0, adapted.length); } }.define();
        assertEquals(7, type.getMethod("oldMethod").invoke(type.getConstructor().newInstance()));
        assertThrows(NoSuchMethodException.class, () -> type.getDeclaredMethod("method"));
    }
    @Test void conflictingInterfaceRenamesCannotSilentlyChooseAnOverrideName() throws Exception {
        var inspector = new ClassInspector();
        var graph = Map.of(name(Left.class), inspector.inspect(bytes(Left.class)), name(Right.class), inspector.inspect(bytes(Right.class)), name(Both.class), inspector.inspect(bytes(Both.class)));
        var rules = Map.of(new MemberReference(name(Left.class), "oldMethod", "()I"), new MemberReference(name(Left.class), "leftMethod", "()I"),
                new MemberReference(name(Right.class), "oldMethod", "()I"), new MemberReference(name(Right.class), "rightMethod", "()I"));
        assertThrows(IllegalArgumentException.class, () -> new ClassAdapter(Map.of(), rules, graph).adapt(bytes(Both.class)));
    }
    @Test void renamesLambdaFunctionalInterfaceMethodAsWellAsInvocation() throws Exception {
        var members = Map.of(new MemberReference(name(SourceFunction.class), "oldCall", "(I)I"), new MemberReference(name(TargetFunction.class), "call", "(I)I"));
        byte[] adapted = new ClassAdapter(Map.of(name(SourceFunction.class), name(TargetFunction.class)), members).adapt(bytes(LambdaUser.class));
        Class<?> type = new ClassLoader(getClass().getClassLoader()) { Class<?> define() { return defineClass(null, adapted, 0, adapted.length); } }.define();
        assertEquals(11, type.getMethod("run").invoke(type.getConstructor().newInstance()));
    }
    private static String name(Class<?> type) { return type.getName().replace('.', '/'); }
    private byte[] bytes(Class<?> type) throws Exception {
        try (var in = type.getResourceAsStream("/" + name(type) + ".class")) { return in.readAllBytes(); }
    }
    @Test void preservesVirtualOverrideDispatchAndInheritedFieldAccessAfterBaseApiRenames() throws Exception {
        var inspector = new ClassInspector();
        var graph = Map.of(name(SourceBase.class), inspector.inspect(bytes(SourceBase.class)), name(CustomBlock.class), inspector.inspect(bytes(CustomBlock.class)));
        var members = Map.of(new MemberReference(name(SourceBase.class), "oldShape", "(I)I"), new MemberReference(name(TargetBase.class), "shape", "(I)I"),
                new MemberReference(name(SourceBase.class), "oldValue", "I"), new MemberReference(name(TargetBase.class), "value", "I"));
        byte[] adapted = new ClassAdapter(Map.of(name(SourceBase.class), name(TargetBase.class)), members, graph).adapt(bytes(CustomBlock.class));
        Class<?> type = new ClassLoader(getClass().getClassLoader()) { Class<?> define() { return defineClass(null, adapted, 0, adapted.length); } }.define();
        Object block = type.getConstructor().newInstance();
        assertEquals(19, type.getMethod("dispatch").invoke(block));
        assertEquals(4, type.getMethod("read").invoke(block));
        assertThrows(NoSuchMethodException.class, () -> type.getMethod("oldShape", int.class));
    }
    @Test void incompleteHierarchyCannotSilentlyLeaveAnOverrideUnmapped() throws Exception {
        var graph = Map.of(name(CustomBlock.class), new ClassInspector().inspect(bytes(CustomBlock.class)));
        var members = Map.of(new MemberReference(name(SourceBase.class), "oldShape", "(I)I"), new MemberReference(name(TargetBase.class), "shape", "(I)I"));
        assertThrows(IllegalArgumentException.class, () -> new ClassAdapter(Map.of(name(SourceBase.class), name(TargetBase.class)), members, graph).adapt(bytes(CustomBlock.class)));
    }
}
