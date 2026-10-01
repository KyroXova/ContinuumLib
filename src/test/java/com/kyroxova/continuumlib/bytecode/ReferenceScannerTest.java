package com.kyroxova.continuumlib.bytecode;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import java.util.function.IntUnaryOperator;
import static org.junit.jupiter.api.Assertions.*;

class ReferenceScannerTest {
    public static class Sample {
        public int value;
        public IntUnaryOperator operation() { return Math::abs; }
        public int read(Sample other) { return other.value + new String("a").length(); }
    }
    @Test void includesDescriptorAndInstructionTypesEvenWithoutMethodCalls() throws Exception {
        try (var in = Sample.class.getResourceAsStream("/" + Sample.class.getName().replace('.', '/') + ".class")) {
            var types = new ReferenceScanner().types(in.readAllBytes());
            assertTrue(types.contains("java/util/function/IntUnaryOperator"));
            assertTrue(types.contains("java/lang/String"));
            assertTrue(types.contains("java/lang/Object"));
        }
    }
    @Test void includesConstructorFieldCallsAndLambdaHandlesWithCallerLocations() throws Exception {
        try (var in = Sample.class.getResourceAsStream("/" + Sample.class.getName().replace('.', '/') + ".class")) {
            var refs = new ReferenceScanner().scan(in.readAllBytes());
            assertTrue(refs.stream().anyMatch(r -> r.target().equals(new MemberReference("java/lang/Math", "abs", "(I)I")) && r.handle()));
            assertTrue(refs.stream().anyMatch(r -> r.target().name().equals("value") && r.opcode() == Opcodes.GETFIELD));
            assertTrue(refs.stream().anyMatch(r -> r.target().owner().equals("java/lang/String") && r.target().name().equals("<init>") && r.caller().name().equals("read")));
            assertTrue(refs.stream().anyMatch(r -> r.target().name().equals("length") && !r.handle()));
        }
    }
}
