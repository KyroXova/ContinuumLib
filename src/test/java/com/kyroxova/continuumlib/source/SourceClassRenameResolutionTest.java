package com.kyroxova.continuumlib.source;

import com.kyroxova.continuumlib.pipeline.migration.CanonicalMigrationPlan;
import com.kyroxova.continuumlib.pipeline.migration.CanonicalMigrationRule;
import com.kyroxova.continuumlib.pipeline.migration.MigrationType;
import com.kyroxova.continuumlib.source.ast.SourceParser;
import com.kyroxova.continuumlib.source.ast.SourceUnit;
import com.kyroxova.continuumlib.source.transform.SourceTransformer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SourceClassRenameResolutionTest {
    @Test
    void samePackageTypeUsesResolvedIdentity(@TempDir Path root) throws Exception {
        Path legacy = root.resolve("example/Legacy.java");
        Path use = root.resolve("example/Use.java");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "package example; public class Legacy {}");
        Files.writeString(use, """
                package example;
                public class Use {
                    private Legacy value;
                    public Legacy create() {
                        return new Legacy();
                    }
                }
                """);

        SourceUnit unit = parse(root, "example/Use.java");
        new SourceTransformer(plan("example/Legacy", "target/Renamed"))
                .transformAst(unit.ast());

        String generated = unit.ast().toString();
        assertTrue(generated.contains("import target.Renamed;"), generated);
        assertTrue(generated.contains("private Renamed value;"), generated);
        assertTrue(generated.contains("public Renamed create()"), generated);
        assertTrue(generated.contains("new Renamed()"), generated);
        assertFalse(generated.contains("new Legacy()"), generated);
    }

    @Test
    void fullyQualifiedTypeDropsOldScope(@TempDir Path root) throws Exception {
        Path legacy = root.resolve("old/pkg/Legacy.java");
        Path use = root.resolve("example/Use.java");
        Files.createDirectories(legacy.getParent());
        Files.createDirectories(use.getParent());
        Files.writeString(legacy, "package old.pkg; public class Legacy {}");
        Files.writeString(use, """
                package example;
                public class Use {
                    private old.pkg.Legacy value;
                    public old.pkg.Legacy create() {
                        return new old.pkg.Legacy();
                    }
                }
                """);

        SourceUnit unit = parse(root, "example/Use.java");
        new SourceTransformer(plan("old/pkg/Legacy", "target/pkg/Renamed"))
                .transformAst(unit.ast());

        String generated = unit.ast().toString();
        assertTrue(generated.contains("import target.pkg.Renamed;"), generated);
        assertTrue(generated.contains("private Renamed value;"), generated);
        assertTrue(generated.contains("public Renamed create()"), generated);
        assertTrue(generated.contains("new Renamed()"), generated);
        assertFalse(generated.contains("old.pkg.Renamed"), generated);
        assertFalse(generated.contains("old.pkg.Legacy"), generated);
    }

    private static CanonicalMigrationPlan plan(String source, String target) {
        CanonicalMigrationRule rename = CanonicalMigrationRule.builder()
                .rulePackId("class-rename")
                .type(MigrationType.CLASS_RENAME)
                .sourceOwner(source)
                .targetOwner(target)
                .build();
        return new CanonicalMigrationPlan(List.of(rename));
    }

    private static SourceUnit parse(Path root, String relative) throws Exception {
        return new SourceParser(List.of(root), List.of())
                .parseDirectory(root)
                .stream()
                .filter(unit -> unit.relativePath().equals(relative))
                .findFirst()
                .orElseThrow();
    }
}
