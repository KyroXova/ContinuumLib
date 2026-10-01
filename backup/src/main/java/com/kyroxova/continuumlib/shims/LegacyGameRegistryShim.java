package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Legacy Forge FML GameRegistry Compatibility Shim.
 * Bridges pre-flattening (1.7.9 / 1.7.10 / 1.12.2) cpw.mods.fml.common.registry.GameRegistry
 * and net.minecraftforge.fml.common.registry.GameRegistry calls to the modern / current registry pipeline.
 */
public final class LegacyGameRegistryShim {

    private static final Logger LOGGER = Logger.getLogger(LegacyGameRegistryShim.class.getName());

    private static final Map<String, Object> REGISTERED_BLOCKS = new ConcurrentHashMap<>();
    private static final Map<String, Object> REGISTERED_ITEMS = new ConcurrentHashMap<>();
    private static final Map<String, Class<?>> REGISTERED_TILE_ENTITIES = new ConcurrentHashMap<>();
    private static final List<Object> REGISTERED_WORLD_GENERATORS = Collections.synchronizedList(new ArrayList<>());
    private static final List<LegacyRecipeEntry> REGISTERED_RECIPES = Collections.synchronizedList(new ArrayList<>());

    private LegacyGameRegistryShim() {}

    /**
     * Intercepts: GameRegistry.registerBlock(Block block, String name)
     */
    public static Object registerBlock(Object block, String name) {
        return registerBlock(block, null, name);
    }

    /**
     * Intercepts: GameRegistry.registerBlock(Block block, Class<? extends ItemBlock> itemclass, String name)
     */
    public static Object registerBlock(Object block, Class<?> itemclass, String name) {
        return registerBlock(block, itemclass, name, new Object[0]);
    }

    /**
     * Intercepts: GameRegistry.registerBlock(Block block, String name, Object... itemCtorArgs)
     */
    public static Object registerBlock(Object block, String name, Object... itemCtorArgs) {
        return registerBlock(block, null, name, itemCtorArgs);
    }

    /**
     * Intercepts: GameRegistry.registerBlock(Block block, Class<? extends ItemBlock> itemclass, String name, Object... itemCtorArgs)
     */
    public static Object registerBlock(Object block, Class<?> itemclass, String name, Object... itemCtorArgs) {
        if (block == null || name == null) return block;

        String[] parts = parseIdentifier(name);
        String modId = parts[0];
        String path = parts[1];
        String fullKey = modId + ":" + path;

        LOGGER.fine(() -> String.format("[LegacyGameRegistryShim] Registering block: %s", fullKey));

        // 1. Register block into modern / current registry and LegacyMetadataShim
        Object blockRegistry = getBlockRegistry();
        RegistryShim.registerFabric(blockRegistry, modId, path, () -> block);
        LegacyMetadataShim.registerDynamicBlock(modId, path, block);
        REGISTERED_BLOCKS.put(fullKey, block);
        REGISTERED_BLOCKS.put(path, block);

        // 2. Automatically instantiate and register corresponding BlockItem / ItemBlock
        try {
            Object itemInstance = null;
            if (itemclass != null) {
                itemInstance = instantiateItemBlock(itemclass, block, itemCtorArgs);
            } else {
                itemInstance = createDefaultBlockItem(block);
            }

            if (itemInstance != null) {
                registerItem(itemInstance, path, modId);
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[LegacyGameRegistryShim] Could not automatically create BlockItem for " + fullKey, t);
        }

        return block;
    }

    /**
     * Intercepts: GameRegistry.registerItem(Item item, String name)
     */
    public static Object registerItem(Object item, String name) {
        if (item == null || name == null) return item;
        String[] parts = parseIdentifier(name);
        return registerItem(item, parts[1], parts[0]);
    }

    /**
     * Intercepts: GameRegistry.registerItem(Item item, String name, String modId)
     */
    public static Object registerItem(Object item, String name, String modId) {
        if (item == null || name == null) return item;

        String effectiveModId = (modId != null && !modId.trim().isEmpty()) ? modId.trim() : detectActiveModId();
        String path = name;
        if (name.contains(":")) {
            String[] parts = name.split(":", 2);
            effectiveModId = parts[0];
            path = parts[1];
        }

        String fullKey = effectiveModId + ":" + path;
        LOGGER.fine(() -> String.format("[LegacyGameRegistryShim] Registering item: %s", fullKey));

        Object itemRegistry = getItemRegistry();
        RegistryShim.registerFabric(itemRegistry, effectiveModId, path, () -> item);
        LegacyMetadataShim.registerDynamicItem(effectiveModId, path, item);
        REGISTERED_ITEMS.put(fullKey, item);
        REGISTERED_ITEMS.put(path, item);

        return item;
    }

    /**
     * Intercepts: GameRegistry.registerTileEntity(Class<? extends TileEntity> tileEntityClass, String id)
     */
    public static void registerTileEntity(Class<?> tileEntityClass, String id) {
        if (tileEntityClass == null || id == null) return;
        LOGGER.fine(() -> String.format("[LegacyGameRegistryShim] Registering tile entity: %s -> %s", id, tileEntityClass.getName()));
        REGISTERED_TILE_ENTITIES.put(id, tileEntityClass);

        // Also register with BlockEntityShim / BuiltInRegistries if possible
        try {
            Class<?> beShim = Class.forName("com.kyroxova.continuumlib.shims.BlockEntityShim");
            Method regMethod = beShim.getMethod("registerLegacyTileEntity", Class.class, String.class);
            regMethod.invoke(null, tileEntityClass, id);
        } catch (Throwable ignored) {}
    }

    /**
     * Intercepts: GameRegistry.registerTileEntityWithAlternatives(Class<? extends TileEntity> tileEntityClass, String id, String... alternatives)
     */
    public static void registerTileEntityWithAlternatives(Class<?> tileEntityClass, String id, String... alternatives) {
        registerTileEntity(tileEntityClass, id);
        if (alternatives != null) {
            for (String alt : alternatives) {
                if (alt != null) {
                    REGISTERED_TILE_ENTITIES.put(alt, tileEntityClass);
                }
            }
        }
    }

    /**
     * Intercepts: GameRegistry.registerWorldGenerator(IWorldGenerator generator, int modGenerationWeight)
     */
    public static void registerWorldGenerator(Object generator, int modGenerationWeight) {
        if (generator == null) return;
        LOGGER.fine(() -> String.format("[LegacyGameRegistryShim] Registered WorldGenerator: %s (weight: %d)", generator.getClass().getName(), modGenerationWeight));
        REGISTERED_WORLD_GENERATORS.add(generator);
    }

    /**
     * Intercepts: GameRegistry.findBlock(String modId, String name)
     */
    public static Object findBlock(String modId, String name) {
        if (name == null) return null;
        String key = (modId != null ? modId + ":" : "") + name;

        // 1. Check local shim registry
        if (REGISTERED_BLOCKS.containsKey(key)) {
            return REGISTERED_BLOCKS.get(key);
        }
        if (REGISTERED_BLOCKS.containsKey(name)) {
            return REGISTERED_BLOCKS.get(name);
        }
        if (REGISTERED_BLOCKS.containsKey("minecraft:" + name)) {
            return REGISTERED_BLOCKS.get("minecraft:" + name);
        }

        // 2. Check LegacyMetadataShim dynamic blocks
        Object dynamicBlock = LegacyMetadataShim.getDynamicBlock(modId, name);
        if (dynamicBlock != null) {
            return dynamicBlock;
        }

        // 3. Check modern BuiltInRegistries.BLOCK
        try {
            Object blockRegistry = getBlockRegistry();
            if (blockRegistry != null) {
                Object idObj = ResourceLocationShim.create(modId != null ? modId : "minecraft", name);
                Method getMethod = blockRegistry.getClass().getMethod("get", idObj.getClass());
                Object res = getMethod.invoke(blockRegistry, idObj);
                if (res != null) return res;
            }
        } catch (Throwable ignored) {}

        // 4. Fallback to legacy cpw.mods.fml.common.registry.GameRegistry.findBlock
        try {
            Class<?> legacyClass = Class.forName("cpw.mods.fml.common.registry.GameRegistry");
            Method find = legacyClass.getMethod("findBlock", String.class, String.class);
            return find.invoke(null, modId, name);
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Intercepts: GameRegistry.findItem(String modId, String name)
     */
    public static Object findItem(String modId, String name) {
        if (name == null) return null;
        String key = (modId != null ? modId + ":" : "") + name;

        // 1. Check local shim registry
        if (REGISTERED_ITEMS.containsKey(key)) {
            return REGISTERED_ITEMS.get(key);
        }
        if (REGISTERED_ITEMS.containsKey(name)) {
            return REGISTERED_ITEMS.get(name);
        }
        if (REGISTERED_ITEMS.containsKey("minecraft:" + name)) {
            return REGISTERED_ITEMS.get("minecraft:" + name);
        }

        // 2. Check LegacyMetadataShim dynamic items
        Object dynamicItem = LegacyMetadataShim.getDynamicItem(modId, name);
        if (dynamicItem != null) {
            return dynamicItem;
        }

        // 2. Check modern BuiltInRegistries.ITEM
        try {
            Object itemRegistry = getItemRegistry();
            if (itemRegistry != null) {
                Object idObj = ResourceLocationShim.create(modId != null ? modId : "minecraft", name);
                Method getMethod = itemRegistry.getClass().getMethod("get", idObj.getClass());
                Object res = getMethod.invoke(itemRegistry, idObj);
                if (res != null) return res;
            }
        } catch (Throwable ignored) {}

        // 3. Fallback to legacy cpw.mods.fml.common.registry.GameRegistry.findItem
        try {
            Class<?> legacyClass = Class.forName("cpw.mods.fml.common.registry.GameRegistry");
            Method find = legacyClass.getMethod("findItem", String.class, String.class);
            return find.invoke(null, modId, name);
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Intercepts: GameRegistry.addRecipe(ItemStack output, Object... params)
     */
    public static void addRecipe(Object output, Object... params) {
        REGISTERED_RECIPES.add(new LegacyRecipeEntry(RecipeType.SHAPED, output, params));
        LOGGER.fine(() -> "[LegacyGameRegistryShim] Registered shaped crafting recipe for: " + output);
    }

    /**
     * Intercepts: GameRegistry.addShapedRecipe(ItemStack output, Object... params)
     */
    public static void addShapedRecipe(Object output, Object... params) {
        addRecipe(output, params);
    }

    /**
     * Intercepts: GameRegistry.addShapelessRecipe(ItemStack output, Object... params)
     */
    public static void addShapelessRecipe(Object output, Object... params) {
        REGISTERED_RECIPES.add(new LegacyRecipeEntry(RecipeType.SHAPELESS, output, params));
        LOGGER.fine(() -> "[LegacyGameRegistryShim] Registered shapeless crafting recipe for: " + output);
    }

    /**
     * Intercepts: GameRegistry.addSmelting(Block/Item/ItemStack input, ItemStack output, float xp)
     */
    public static void addSmelting(Object input, Object output, float xp) {
        REGISTERED_RECIPES.add(new LegacyRecipeEntry(RecipeType.SMELTING, output, new Object[]{input, xp}));
        LOGGER.fine(() -> "[LegacyGameRegistryShim] Registered smelting recipe: " + input + " -> " + output);
    }

    // --- Helper Methods ---

    private static String[] parseIdentifier(String name) {
        if (name.contains(":")) {
            String[] parts = name.split(":", 2);
            return new String[]{parts[0], parts[1]};
        }
        return new String[]{detectActiveModId(), name};
    }

    private static String detectActiveModId() {
        // 1. Try modern NeoForge / Forge ModLoadingContext
        try {
            Class<?> mlcClass = Class.forName("net.neoforged.fml.ModLoadingContext");
            Method getMethod = mlcClass.getMethod("get");
            Object mlc = getMethod.invoke(null);
            if (mlc != null) {
                Method getContainer = mlc.getClass().getMethod("getActiveContainer");
                Object container = getContainer.invoke(mlc);
                if (container != null) {
                    Method getModId = container.getClass().getMethod("getModId");
                    Object modId = getModId.invoke(container);
                    if (modId != null) return modId.toString();
                }
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> mlcClass = Class.forName("net.minecraftforge.fml.ModLoadingContext");
            Method getMethod = mlcClass.getMethod("get");
            Object mlc = getMethod.invoke(null);
            if (mlc != null) {
                Method getContainer = mlc.getClass().getMethod("getActiveContainer");
                Object container = getContainer.invoke(mlc);
                if (container != null) {
                    Method getModId = container.getClass().getMethod("getModId");
                    Object modId = getModId.invoke(container);
                    if (modId != null) return modId.toString();
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try legacy Forge 1.7.10 / 1.12.2 Loader
        try {
            Class<?> loaderClass = Class.forName("cpw.mods.fml.common.Loader");
            Method instanceMethod = loaderClass.getMethod("instance");
            Object loader = instanceMethod.invoke(null);
            Method activeContainerMethod = loader.getClass().getMethod("activeModContainer");
            Object container = activeContainerMethod.invoke(loader);
            if (container != null) {
                Method getModId = container.getClass().getMethod("getModId");
                Object modId = getModId.invoke(container);
                if (modId != null) return modId.toString();
            }
        } catch (Throwable ignored) {}

        return "continuumlib";
    }

    private static Object getBlockRegistry() {
        // 1. 1.19.3+ BuiltInRegistries.BLOCK
        try {
            Class<?> registriesClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            Field blockRegField = registriesClass.getField("BLOCK");
            return blockRegField.get(null);
        } catch (Throwable ignored) {}

        // 2. 1.13 - 1.19.2 Registry.BLOCK
        try {
            Class<?> registryClass = Class.forName("net.minecraft.core.Registry");
            Field blockRegField = registryClass.getField("BLOCK");
            return blockRegField.get(null);
        } catch (Throwable ignored) {}

        // 3. Forge ForgeRegistries.BLOCKS
        try {
            Class<?> forgeRegistriesClass = Class.forName("net.minecraftforge.registries.ForgeRegistries");
            Field blockRegField = forgeRegistriesClass.getField("BLOCKS");
            return blockRegField.get(null);
        } catch (Throwable ignored) {}

        return null;
    }

    private static Object getItemRegistry() {
        // 1. 1.19.3+ BuiltInRegistries.ITEM
        try {
            Class<?> registriesClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            Field itemRegField = registriesClass.getField("ITEM");
            return itemRegField.get(null);
        } catch (Throwable ignored) {}

        // 2. 1.13 - 1.19.2 Registry.ITEM
        try {
            Class<?> registryClass = Class.forName("net.minecraft.core.Registry");
            Field itemRegField = registryClass.getField("ITEM");
            return itemRegField.get(null);
        } catch (Throwable ignored) {}

        // 3. Forge ForgeRegistries.ITEMS
        try {
            Class<?> forgeRegistriesClass = Class.forName("net.minecraftforge.registries.ForgeRegistries");
            Field itemRegField = forgeRegistriesClass.getField("ITEMS");
            return itemRegField.get(null);
        } catch (Throwable ignored) {}

        return null;
    }

    private static Object createDefaultBlockItem(Object block) {
        // Modern BlockItem
        try {
            Class<?> blockItemClass = Class.forName("net.minecraft.world.item.BlockItem");
            Class<?> blockClass = Class.forName("net.minecraft.world.level.block.Block");
            Class<?> propClass = Class.forName("net.minecraft.world.item.Item$Properties");
            Object props = propClass.getConstructor().newInstance();
            Constructor<?> ctor = blockItemClass.getConstructor(blockClass, propClass);
            return ctor.newInstance(block, props);
        } catch (Throwable ignored) {}

        // Legacy ItemBlock
        try {
            Class<?> itemBlockClass = Class.forName("net.minecraft.item.ItemBlock");
            Class<?> blockClass = Class.forName("net.minecraft.block.Block");
            Constructor<?> ctor = itemBlockClass.getConstructor(blockClass);
            return ctor.newInstance(block);
        } catch (Throwable ignored) {}

        return null;
    }

    private static Object instantiateItemBlock(Class<?> itemclass, Object block, Object[] ctorArgs) {
        try {
            // 1. Try (block, ctorArgs...)
            List<Object> args = new ArrayList<>();
            args.add(block);
            if (ctorArgs != null) {
                args.addAll(Arrays.asList(ctorArgs));
            }
            for (Constructor<?> ctor : itemclass.getConstructors()) {
                if (ctor.getParameterCount() == args.size()) {
                    try {
                        return ctor.newInstance(args.toArray());
                    } catch (Throwable ignored) {}
                }
            }

            // 2. Try (block)
            for (Constructor<?> ctor : itemclass.getConstructors()) {
                if (ctor.getParameterCount() == 1) {
                    try {
                        return ctor.newInstance(block);
                    } catch (Throwable ignored) {}
                }
            }

            // 3. Try default no-arg
            return itemclass.getConstructor().newInstance();
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[LegacyGameRegistryShim] Failed to instantiate " + itemclass.getName(), t);
            return createDefaultBlockItem(block);
        }
    }

    public enum RecipeType {
        SHAPED,
        SHAPELESS,
        SMELTING
    }

    public record LegacyRecipeEntry(RecipeType type, Object output, Object[] params) {}
}
