package com.kyroxova.continuumlib.source;

import com.kyroxova.continuumlib.pipeline.migration.CanonicalMigrationPlan;
import com.kyroxova.continuumlib.pipeline.migration.CanonicalMigrationRule;
import com.kyroxova.continuumlib.pipeline.migration.MigrationType;
import com.kyroxova.continuumlib.source.ast.SourceParser;
import com.kyroxova.continuumlib.source.transform.SourceTransformer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SourceAnnotationTransformationTest {
    @Test
    void classRenamesUpdateAllAnnotationForms() {
        String source = """
                package example;

                import legacy.OldMarker;
                import legacy.OldSingle;
                import legacy.OldNormal;

                @OldMarker
                @OldSingle("value")
                @OldNormal(value = "value")
                public class Demo {
                }
                """;

        var unit = new SourceParser(List.of(), List.of())
                .parseString("example/Demo.java", source);

        var plan = new CanonicalMigrationPlan(List.of(
                classRename("legacy.OldMarker", "modern.NewMarker"),
                classRename("legacy.OldSingle", "modern.NewSingle"),
                classRename("legacy.OldNormal", "modern.NewNormal")
        ));

        String transformed = new SourceTransformer(plan)
                .transformAst(unit.ast())
                .toString();

        assertTrue(transformed.contains("@NewMarker"), transformed);
        assertTrue(transformed.contains("@NewSingle(\"value\")"), transformed);
        assertTrue(transformed.contains("@NewNormal(value = \"value\")"), transformed);
        assertTrue(transformed.contains("import modern.NewMarker;"), transformed);
        assertTrue(transformed.contains("import modern.NewSingle;"), transformed);
        assertTrue(transformed.contains("import modern.NewNormal;"), transformed);
        assertFalse(transformed.contains("legacy.OldMarker"), transformed);
        assertFalse(transformed.contains("legacy.OldSingle"), transformed);
        assertFalse(transformed.contains("legacy.OldNormal"), transformed);
    }

    @Test
    void samePackageAnnotationRenameDoesNotRequireSourceResolution() {
        String source = """
                package legacy;

                @OldMarker
                public class Demo {
                }
                """;

        var unit = new SourceParser(List.of(), List.of())
                .parseString("legacy/Demo.java", source);

        var plan = new CanonicalMigrationPlan(List.of(
                classRename("legacy.OldMarker", "modern.NewMarker")
        ));

        String transformed = new SourceTransformer(plan)
                .transformAst(unit.ast())
                .toString();

        assertTrue(transformed.contains("@NewMarker"), transformed);
        assertTrue(transformed.contains("import modern.NewMarker;"), transformed);
        assertFalse(transformed.contains("@OldMarker"), transformed);
    }

    private static CanonicalMigrationRule classRename(String source, String target) {
        return CanonicalMigrationRule.builder()
                .type(MigrationType.CLASS_RENAME)
                .sourceOwner(source)
                .targetOwner(target)
                .build();
    }
}
