package com.kyroxova.continuumlib.analyzer;

import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.Loader;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;
import com.kyroxova.continuumlib.model.operation.DeclareRegistry;
import com.kyroxova.continuumlib.model.operation.RegistryKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Forge1182RegistryFamilyRecognizerTest {
    private static final EnvironmentId FORGE_1182 =
            new EnvironmentId("1.18.2", Loader.FORGE, MappingNamespace.MOJMAP, 17);

    @Test
    void recognizesMinecraftRegistryFamilies(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("Registries.java"), """
                class Registries {
                  static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Mod.ID);
                  static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Mod.ID);
                  static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITIES, Mod.ID);
                  static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITIES, Mod.ID);
                  static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, Mod.ID);
                  static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Mod.ID);
                  static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Mod.ID);
                  static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.CONTAINERS, Mod.ID);
                  static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, Mod.ID);
                }
                """);

        var model = new JavaProjectAnalyzer().analyze(root, FORGE_1182);
        Set<RegistryKind> kinds = model.operations(DeclareRegistry.class).stream()
                .map(DeclareRegistry::registryKind).collect(Collectors.toSet());

        assertEquals(Set.of(
                RegistryKind.BLOCK, RegistryKind.ITEM, RegistryKind.BLOCK_ENTITY_TYPE,
                RegistryKind.ENTITY_TYPE, RegistryKind.FLUID, RegistryKind.SOUND_EVENT,
                RegistryKind.PARTICLE_TYPE, RegistryKind.MENU_TYPE, RegistryKind.RECIPE_SERIALIZER), kinds);
    }
}
