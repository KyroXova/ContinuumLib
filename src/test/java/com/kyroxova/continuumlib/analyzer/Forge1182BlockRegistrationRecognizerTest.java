package com.kyroxova.continuumlib.analyzer;

import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.Loader;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;
import com.kyroxova.continuumlib.model.operation.DeclareRegistry;
import com.kyroxova.continuumlib.model.operation.RegisterBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Forge1182BlockRegistrationRecognizerTest {
    private static final EnvironmentId FORGE_1182 =
            new EnvironmentId("1.18.2", Loader.FORGE, MappingNamespace.MOJMAP, 17);

    @Test
    void recognizesBuildersWorkbenchRegistrationAndProperties() throws Exception {
        var project = new JavaProjectAnalyzer().analyze(
                Path.of("src/test/resources/fixtures/forge-1.18.2"), FORGE_1182);

        var declaration = project.operations(DeclareRegistry.class).get(0);
        assertEquals("BLOCK", declaration.registryKind());
        assertEquals("BuildScape.MODID", declaration.modIdExpression().source());

        var block = project.operations(RegisterBlock.class).get(0);
        assertEquals("builders_workbench", block.registrationId());
        assertEquals("BuildersWorkbenchBlock", block.implementationType());
        assertEquals("Material.WOOD", block.properties().material().source());
        assertEquals("MaterialColor.COLOR_BROWN", block.properties().mapColor().source());
        assertEquals(2.5, block.properties().strength());
        assertEquals("SoundType.WOOD", block.properties().sound().source());
        assertTrue(project.diagnostics().isEmpty());
    }

    @Test
    void reportsUnresolvedRegistrationInsteadOfGuessing(@TempDir Path sourceRoot) throws Exception {
        Files.writeString(sourceRoot.resolve("ModBlocks.java"), """
                class ModBlocks {
                    static final RegistryObject<Block> MYSTERY = BLOCKS.register(makeId(), factoryFromConfig());
                }
                """);

        var project = new JavaProjectAnalyzer().analyze(sourceRoot, FORGE_1182);

        assertTrue(project.operations(RegisterBlock.class).isEmpty());
        assertEquals("UNRESOLVED_OPERATION", project.diagnostics().get(0).code().name());
        assertTrue(project.diagnostics().get(0).message().contains("MYSTERY"));
    }
}
