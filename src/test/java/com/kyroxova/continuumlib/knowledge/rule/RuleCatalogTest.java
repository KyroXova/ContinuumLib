package com.kyroxova.continuumlib.knowledge.rule;

import com.kyroxova.continuumlib.bytecode.MemberReference;
import com.kyroxova.continuumlib.model.environment.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RuleCatalogTest {
    @TempDir Path dir;
    private final EnvironmentId source = new EnvironmentId("1.18.2", Loader.FORGE, MappingNamespace.MOJMAP, 17);
    private final EnvironmentId target = new EnvironmentId("1.20.1", Loader.FORGE, MappingNamespace.MOJMAP, 17);
    public static class Api { public static int oldCall(int n) { return n * 2; } public static int newCall(int n) { return n * 3; } }
    public static class Mod { public int run() { return Api.oldCall(4) + 7; } }
    private byte[] bytes(Class<?> c) throws Exception {
        try (var in = c.getResourceAsStream("/" + c.getName().replace('.', '/') + ".class")) { return in.readAllBytes(); }
    }
    private Map<String, String> manifest(Path path) throws Exception {
        return Map.of("api", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
    }
    private RulePack pack(String id, Path artifact, String method) throws Exception {
        String owner = Api.class.getName().replace('.', '/');
        return new RulePack(id, "Executable test fixture; not Minecraft migration evidence", source, target,
                manifest(artifact), manifest(artifact), Map.of(),
                Map.of(new MemberReference(owner, "oldCall", "(I)I"), new MemberReference(owner, method, "(I)I")), List.of());
    }
    @Test void selectedAndArtifactBoundPackTransformsCustomCode() throws Exception {
        Path api = Files.write(dir.resolve("api.class"), bytes(Api.class));
        var plan = new RuleCatalog(List.of(pack("rename", api, "newCall"))).select(source, target);
        var bound = plan.bind(Map.of("api", api), Map.of("api", api));
        byte[] adapted = bound.adapt(bytes(Mod.class));
        Class<?> mod = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() { return defineClass(null, adapted, 0, adapted.length); }
        }.define();
        assertEquals(19, mod.getMethod("run").invoke(mod.getConstructor().newInstance()));
        assertEquals(List.of("rename"), plan.packIds());
    }
    @Test void wrongEnvironmentIsNotAnImplicitFallback() throws Exception {
        Path api = Files.write(dir.resolve("api.class"), bytes(Api.class));
        var catalog = new RuleCatalog(List.of(pack("rename", api, "newCall")));
        var wrong = new EnvironmentId("1.20.1", Loader.FABRIC, MappingNamespace.MOJMAP, 17);
        assertThrows(IllegalArgumentException.class, () -> catalog.select(source, wrong));
    }
    @Test void mutatedMissingAndExtraArtifactsAreRejected() throws Exception {
        Path api = Files.write(dir.resolve("api.class"), bytes(Api.class));
        var plan = new RuleCatalog(List.of(pack("rename", api, "newCall"))).select(source, target);
        assertThrows(IllegalArgumentException.class, () -> plan.bind(Map.of(), Map.of("api", api)));
        assertThrows(IllegalArgumentException.class, () -> plan.bind(Map.of("api", api, "extra", api), Map.of("api", api)));
        Files.write(api, new byte[]{1, 2, 3});
        assertThrows(IllegalArgumentException.class, () -> plan.bind(Map.of("api", api), Map.of("api", api)));
    }
    @Test void competingMemberRulesFailInsteadOfDependingOnPackOrder() throws Exception {
        Path api = Files.write(dir.resolve("api.class"), bytes(Api.class));
        var catalog = new RuleCatalog(List.of(pack("one", api, "newCall"), pack("two", api, "otherCall")));
        assertThrows(IllegalArgumentException.class, () -> catalog.select(source, target));
    }
    @Test void refusesJavaDowngradingAndPreviewBytecode() throws Exception {
        Path api = Files.write(dir.resolve("api.class"), bytes(Api.class));
        RulePack original = pack("rename", api, "newCall");
        var java8 = new EnvironmentId("1.7.10", Loader.FORGE, MappingNamespace.SRG, 8);
        var legacy = new RulePack(original.id(), original.evidence(), source, java8,
                original.sourceArtifacts(), original.targetArtifacts(), original.classes(), original.members(), original.bridges());
        var bound = new RuleCatalog(List.of(legacy)).select(source, java8).bind(Map.of("api", api), Map.of("api", api));
        byte[] modern = bytes(Mod.class);
        assertThrows(IllegalArgumentException.class, () -> bound.adapt(modern));
        var current = new RuleCatalog(List.of(original)).select(source, target).bind(Map.of("api", api), Map.of("api", api));
        modern[4] = (byte) 255; modern[5] = (byte) 255;
        assertThrows(IllegalArgumentException.class, () -> current.adapt(modern));
    }
}
