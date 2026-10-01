package com.kyroxova.continuumlib.resolver;

import com.kyroxova.continuumlib.bytecode.*;
import com.kyroxova.continuumlib.knowledge.rule.RulePack;
import com.kyroxova.continuumlib.model.environment.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RuleDeclarationVerifierTest {
    @Test void rejectsStaticHookThatExistsOnlyOnAnImplementedInterface() {
        var env = new EnvironmentId("1.18.2", Loader.FORGE, MappingNamespace.MOJMAP, 17);
        var source = api("oldCall", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        var rule = new CallBridge.Rule(new MemberReference("fixture/Api", "oldCall", "(I)I"), Opcodes.INVOKESTATIC,
                new MemberReference("hooks/Impl", "adapt", "(I)I"));
        var pack = new RulePack("invalid-static", "Synthetic", env, env, Map.of("api", "0".repeat(64)),
                Map.of("api", "0".repeat(64)), Map.of(), Map.of(), List.of(rule));
        var itf = new ClassInfo("hooks/Interface", null, List.of(), Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE,
                List.of(), List.of(new ClassInfo.Member("adapt", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, null)));
        var impl = new ClassInfo("hooks/Impl", null, List.of(itf.name()), Opcodes.ACC_PUBLIC, List.of(), List.of());
        assertFalse(new RuleDeclarationVerifier().validate(List.of(pack), source, Map.of(impl.name(), impl, itf.name(), itf)).isEmpty());
    }
    private RulePack pack() {
        var env = new EnvironmentId("1.18.2", Loader.FORGE, MappingNamespace.MOJMAP, 17);
        return new RulePack("fixture", "Synthetic", env, env, Map.of("api", "0".repeat(64)), Map.of("api", "0".repeat(64)), Map.of(),
                Map.of(new MemberReference("fixture/Api", "oldCall", "(I)I"), new MemberReference("fixture/Api", "newCall", "(I)I")), List.of());
    }
    private Map<String, ClassInfo> api(String method, int access) {
        return Map.of("fixture/Api", new ClassInfo("fixture/Api", null, List.of(), Opcodes.ACC_PUBLIC,
                List.of(), List.of(new ClassInfo.Member(method, "(I)I", access, null))));
    }
    @Test void validatesRealDeclarationShapeRatherThanAcceptingRuleText() {
        var source = api("oldCall", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        var verifier = new RuleDeclarationVerifier();
        assertTrue(verifier.validate(List.of(pack()), source, api("newCall", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)).isEmpty());
        assertFalse(verifier.validate(List.of(pack()), source, api("unrelated", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)).isEmpty());
        assertFalse(verifier.validate(List.of(pack()), source, api("newCall", Opcodes.ACC_PUBLIC)).isEmpty());
        assertFalse(verifier.validate(List.of(pack()), source, api("newCall", Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)).isEmpty());
        var targetInterface = new ClassInfo("fixture/Api", null, List.of(), Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE,
                List.of(), List.of(new ClassInfo.Member("newCall", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, null)));
        assertFalse(verifier.validate(List.of(pack()), source, Map.of("fixture/Api", targetInterface)).isEmpty());
    }
}
