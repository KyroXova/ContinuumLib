package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.continuumlib.artifact.JarTransformer;
import com.kyroxova.continuumlib.bytecode.CallBridge;
import com.kyroxova.continuumlib.bytecode.MemberReference;
import com.kyroxova.continuumlib.bytecode.ReferenceScanner;
import com.kyroxova.continuumlib.pipeline.migration.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class BytecodeMigrationExecutor {
    private record BridgeKey(MemberReference source, int opcode) {}

    public record Result(int adaptedClasses, List<AppliedMigration> appliedMigrations) {
        public Result {
            appliedMigrations = List.copyOf(appliedMigrations);
        }
    }

    public Result apply(
            Path jar,
            CanonicalMigrationPlan plan,
            Collection<AppliedMigration> alreadyApplied
    ) throws IOException {
        Objects.requireNonNull(jar, "jar");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(alreadyApplied, "alreadyApplied");

        // Source-level accounting is occurrence-based, while a canonical rule may match
        // multiple call sites. Always run every bytecode-layer rule against the compiled JAR:
        // source-rewritten sites no longer contain the old exact reference, and any remaining
        // sites still need the bytecode safety net.
        List<CanonicalMigrationRule> remaining = plan.rulesForLayer(MigrationLayer.BYTECODE);
        if (remaining.isEmpty()) {
            return new Result(0, List.of());
        }

        Map<BridgeKey, CanonicalMigrationRule> indexed = new HashMap<>();
        List<CallBridge.Rule> bridges = new ArrayList<>();

        for (CanonicalMigrationRule rule : remaining) {
            if (rule.type() != MigrationType.CALL_BRIDGE) {
                throw new IllegalArgumentException("Unsupported bytecode migration type: " + rule.type());
            }
            MemberReference source = member(rule.sourceOwner(), rule.sourceName(), rule.sourceDescriptor());
            MemberReference target = member(rule.targetOwner(), rule.targetName(), rule.targetDescriptor());
            BridgeKey key = new BridgeKey(source, rule.opcode());
            CanonicalMigrationRule previous = indexed.putIfAbsent(key, rule);
            if (previous != null && !previous.equals(rule)) {
                throw new IllegalArgumentException("Conflicting bytecode bridge: " + source + " opcode=" + rule.opcode());
            }
            bridges.add(new CallBridge.Rule(source, rule.opcode(), target));
        }

        CallBridge bridge = new CallBridge(bridges);
        ReferenceScanner scanner = new ReferenceScanner();
        AtomicInteger adaptedClasses = new AtomicInteger();
        List<AppliedMigration> applied = new ArrayList<>();

        Path transformed = jar.resolveSibling(jar.getFileName() + ".bytecode");
        Files.deleteIfExists(transformed);

        try {
            new JarTransformer().transform(jar, transformed, original -> {
                List<ReferenceScanner.Use> uses = scanner.scan(original);
                byte[] result = bridge.adapt(original);
                if (!Arrays.equals(original, result)) {
                    adaptedClasses.incrementAndGet();
                }

                for (ReferenceScanner.Use use : uses) {
                    CanonicalMigrationRule rule = indexed.get(new BridgeKey(use.target(), use.opcode()));
                    if (rule != null) {
                        applied.add(AppliedMigration.from(
                                rule,
                                MigrationLayer.BYTECODE,
                                MigrationConfidence.SEMANTICALLY_RESOLVED,
                                use.caller().owner() + ".class",
                                use.line()
                        ));
                    }
                }
                return result;
            });

            replace(transformed, jar);
            return new Result(adaptedClasses.get(), applied);
        } finally {
            Files.deleteIfExists(transformed);
        }
    }

    private static MemberReference member(String owner, String name, String descriptor) {
        if (owner == null || name == null || descriptor == null) {
            throw new IllegalArgumentException("Bytecode migration requires full member identity");
        }
        return new MemberReference(owner.replace('.', '/'), name, descriptor);
    }

    private static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
