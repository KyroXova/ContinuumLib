package com.kyroxova.continuumlib.source;

import com.kyroxova.continuumlib.bytecode.CallBridge;
import com.kyroxova.continuumlib.bytecode.ConstructorFactory;
import com.kyroxova.continuumlib.bytecode.MemberReference;
import com.kyroxova.continuumlib.knowledge.rule.RulePack;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.Loader;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;
import com.kyroxova.continuumlib.source.ast.SourceParser;
import com.kyroxova.continuumlib.source.rule.SourceMigrationPlan;
import com.kyroxova.continuumlib.source.transform.SourceTransformer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SourceMigrationPrecisionTest {
    private static final String HASH = "0".repeat(64);

    @Test
    void rulePackPreservesDescriptorsAndBridges(@TempDir Path sourceRoot) throws Exception {
        Path api = sourceRoot.resolve("fixture/Api.java");
        Path mod = sourceRoot.resolve("example/Mod.java");
        Files.createDirectories(api.getParent());
        Files.createDirectories(mod.getParent());
        Files.writeString(api, """
                package fixture;
                public class Api {
                    public int value;
                    public Api(int value) { this.value = value; }
                    public static int oldCall(int value) { return value; }
                    public static String oldCall(String value) { return value; }
                }
                """);
        Files.writeString(mod, """
                package example;
                import fixture.Api;
                public class Mod {
                    public int run() { return Api.oldCall(1) + new Api(2).value; }
                    public String untouched() { return Api.oldCall("x"); }
                }
                """);

        MemberReference intCall = new MemberReference("fixture/Api", "oldCall", "(I)I");
        MemberReference intTarget = new MemberReference("fixture/Api", "newCall", "(I)I");
        MemberReference constructor = new MemberReference("fixture/Api", "<init>", "(I)V");
        MemberReference factory = new MemberReference("fixture/Api", "create", "(I)Lfixture/Api;");
        MemberReference field = new MemberReference("fixture/Api", "value", "I");
        MemberReference getter = new MemberReference("fixture/Api", "readValue", "(Lfixture/Api;)I");

        EnvironmentId env = new EnvironmentId("1.20.1", Loader.FORGE, MappingNamespace.MOJMAP, 17);
        RulePack pack = new RulePack("fixture", "verified fixture", env, env,
                Map.of("api", HASH), Map.of("api", HASH),
                Map.of(), Map.of(intCall, intTarget),
                List.of(new CallBridge.Rule(field, Opcodes.GETFIELD, getter)),
                List.of(new ConstructorFactory.Rule(constructor, factory)));

        var units = new SourceParser(List.of(sourceRoot), List.of()).parseDirectory(sourceRoot);
        var modUnit = units.stream().filter(unit -> unit.relativePath().equals("example/Mod.java")).findFirst().orElseThrow();
        String transformed = new SourceTransformer(SourceMigrationPlan.fromRulePack(pack))
                .transformAst(modUnit.ast().clone()).toString();

        assertTrue(transformed.contains("Api.newCall(1)"), transformed);
        assertTrue(transformed.contains("Api.oldCall(\"x\")"), transformed);
        assertTrue(transformed.contains("Api.readValue(Api.create(2))"), transformed);
        assertFalse(transformed.contains("new Api(2)"), transformed);
    }

    @Test
    void unresolvedNameOnlyCallsAreNotGuessed() {
        String code = """
                package example;
                public class Mod {
                    public void run() {
                        first.oldMethod();
                        second.oldMethod();
                    }
                    Object first;
                    Object second;
                }
                """;
        var unit = new SourceParser(List.of(), List.of()).parseString("Mod.java", code);
        var plan = SourceMigrationPlan.builder()
                .addMethodRename("missing.Owner", "oldMethod", "newMethod")
                .build();

        String transformed = new SourceTransformer(plan).transformAst(unit.ast()).toString();

        assertEquals(2, transformed.split("oldMethod", -1).length - 1, transformed);
        assertFalse(transformed.contains("newMethod"), transformed);
    }
}
