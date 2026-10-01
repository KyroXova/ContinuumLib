package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Pre-Flattening and Legacy Forge Transformation Rules (1.7.9 / 1.7.10 / 1.12.2 -> 26.3+).
 * Redirects legacy cpw.mods.fml.common.registry.GameRegistry and net.minecraftforge.fml.common.registry.GameRegistry
 * calls to LegacyGameRegistryShim, and bridges 4-bit numeric metadata / legacy block IDs to LegacyMetadataShim.
 */
public final class PreFlatteningCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_8 = MCVersion.of("1.8");
        MCVersion v1_13 = MCVersion.V1_13;

        // =========================================================================
        // 1. cpw.mods.fml.common.registry.GameRegistry Redirects (1.7.9 - 1.7.10)
        // =========================================================================

        // Class Redirect: cpw/mods/fml/common/registry/GameRegistry -> LegacyGameRegistryShim (1.8+)
        kb.registerRule(new ClassRedirectRule(
                "cpw/mods/fml/common/registry/GameRegistry",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim",
                v1_8, null, null,
                "cpw.mods.fml.common.registry.GameRegistry -> LegacyGameRegistryShim"
        ));

        // Polyfill: registerBlock(Block, String)
        kb.registerRule(new PolyfillRule(
                "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)Lnet/minecraft/block/Block;",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim", "registerBlock",
                "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",
                v1_8, null, null,
                "GameRegistry.registerBlock(Block, String) -> LegacyGameRegistryShim.registerBlock"
        ));

        // Polyfill: registerBlock(Block, Class, String)
        kb.registerRule(new PolyfillRule(
                "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/Class;Ljava/lang/String;)Lnet/minecraft/block/Block;",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim", "registerBlock",
                "(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Object;",
                v1_8, null, null,
                "GameRegistry.registerBlock(Block, Class, String) -> LegacyGameRegistryShim.registerBlock"
        ));

        // Polyfill: registerBlock(Block, Class, String, Object...)
        kb.registerRule(new PolyfillRule(
                "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/block/Block;",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim", "registerBlock",
                "(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;",
                v1_8, null, null,
                "GameRegistry.registerBlock(Block, Class, String, Object...) -> LegacyGameRegistryShim.registerBlock"
        ));

        // Polyfill: registerBlock(Block, String, Object...)
        kb.registerRule(new PolyfillRule(
                "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/block/Block;",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim", "registerBlock",
                "(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;",
                v1_8, null, null,
                "GameRegistry.registerBlock(Block, String, Object...) -> LegacyGameRegistryShim.registerBlock"
        ));

        // Polyfill: registerItem(Item, String)
        kb.registerRule(new PolyfillRule(
                "cpw/mods/fml/common/registry/GameRegistry", "registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)Lnet/minecraft/item/Item;",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim", "registerItem",
                "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",
                v1_8, null, null,
                "GameRegistry.registerItem(Item, String) -> LegacyGameRegistryShim.registerItem"
        ));

        // Polyfill: registerItem(Item, String, String)
        kb.registerRule(new PolyfillRule(
                "cpw/mods/fml/common/registry/GameRegistry", "registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/item/Item;",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim", "registerItem",
                "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;",
                v1_8, null, null,
                "GameRegistry.registerItem(Item, String, String) -> LegacyGameRegistryShim.registerItem"
        ));

        // Polyfill: registerTileEntity(Class, String)
        kb.registerRule(new PolyfillRule(
                "cpw/mods/fml/common/registry/GameRegistry", "registerTileEntity",
                "(Ljava/lang/Class;Ljava/lang/String;)V",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim", "registerTileEntity",
                "(Ljava/lang/Class;Ljava/lang/String;)V",
                v1_8, null, null,
                "GameRegistry.registerTileEntity(Class, String) -> LegacyGameRegistryShim.registerTileEntity"
        ));

        // Polyfill: registerTileEntityWithAlternatives(Class, String, String...)
        kb.registerRule(new PolyfillRule(
                "cpw/mods/fml/common/registry/GameRegistry", "registerTileEntityWithAlternatives",
                "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/String;)V",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim", "registerTileEntityWithAlternatives",
                "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/String;)V",
                v1_8, null, null,
                "GameRegistry.registerTileEntityWithAlternatives -> LegacyGameRegistryShim"
        ));

        // Polyfill: registerWorldGenerator
        kb.registerRule(new PolyfillRule(
                "cpw/mods/fml/common/registry/GameRegistry", "registerWorldGenerator",
                "(Lcpw/mods/fml/common/IWorldGenerator;I)V",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim", "registerWorldGenerator",
                "(Ljava/lang/Object;I)V",
                v1_8, null, null,
                "GameRegistry.registerWorldGenerator -> LegacyGameRegistryShim.registerWorldGenerator"
        ));

        // Polyfill: findBlock(String, String)
        kb.registerRule(new PolyfillRule(
                "cpw/mods/fml/common/registry/GameRegistry", "findBlock",
                "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/block/Block;",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim", "findBlock",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;",
                v1_8, null, null,
                "GameRegistry.findBlock -> LegacyGameRegistryShim.findBlock"
        ));

        // Polyfill: findItem(String, String)
        kb.registerRule(new PolyfillRule(
                "cpw/mods/fml/common/registry/GameRegistry", "findItem",
                "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/item/Item;",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim", "findItem",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;",
                v1_8, null, null,
                "GameRegistry.findItem -> LegacyGameRegistryShim.findItem"
        ));

        // Recipes: addRecipe, addShapedRecipe, addShapelessRecipe, addSmelting
        kb.registerRule(new PolyfillRule(
                "cpw/mods/fml/common/registry/GameRegistry", "addRecipe",
                "(Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)V",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim", "addRecipe",
                "(Ljava/lang/Object;[Ljava/lang/Object;)V",
                v1_8, null, null,
                "GameRegistry.addRecipe -> LegacyGameRegistryShim.addRecipe"
        ));

        kb.registerRule(new PolyfillRule(
                "cpw/mods/fml/common/registry/GameRegistry", "addShapedRecipe",
                "(Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)V",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim", "addShapedRecipe",
                "(Ljava/lang/Object;[Ljava/lang/Object;)V",
                v1_8, null, null,
                "GameRegistry.addShapedRecipe -> LegacyGameRegistryShim.addShapedRecipe"
        ));

        kb.registerRule(new PolyfillRule(
                "cpw/mods/fml/common/registry/GameRegistry", "addShapelessRecipe",
                "(Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)V",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim", "addShapelessRecipe",
                "(Ljava/lang/Object;[Ljava/lang/Object;)V",
                v1_8, null, null,
                "GameRegistry.addShapelessRecipe -> LegacyGameRegistryShim.addShapelessRecipe"
        ));

        kb.registerRule(new PolyfillRule(
                "cpw/mods/fml/common/registry/GameRegistry", "addSmelting",
                "(Lnet/minecraft/item/Item;Lnet/minecraft/item/ItemStack;F)V",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim", "addSmelting",
                "(Ljava/lang/Object;Ljava/lang/Object;F)V",
                v1_8, null, null,
                "GameRegistry.addSmelting(Item, ...) -> LegacyGameRegistryShim.addSmelting"
        ));

        kb.registerRule(new PolyfillRule(
                "cpw/mods/fml/common/registry/GameRegistry", "addSmelting",
                "(Lnet/minecraft/block/Block;Lnet/minecraft/item/ItemStack;F)V",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim", "addSmelting",
                "(Ljava/lang/Object;Ljava/lang/Object;F)V",
                v1_8, null, null,
                "GameRegistry.addSmelting(Block, ...) -> LegacyGameRegistryShim.addSmelting"
        ));

        kb.registerRule(new PolyfillRule(
                "cpw/mods/fml/common/registry/GameRegistry", "addSmelting",
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;F)V",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim", "addSmelting",
                "(Ljava/lang/Object;Ljava/lang/Object;F)V",
                v1_8, null, null,
                "GameRegistry.addSmelting(ItemStack, ...) -> LegacyGameRegistryShim.addSmelting"
        ));

        // =========================================================================
        // 2. net.minecraftforge.fml.common.registry.GameRegistry (1.8 - 1.12.2)
        // =========================================================================

        kb.registerRule(new ClassRedirectRule(
                "net/minecraftforge/fml/common/registry/GameRegistry",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim",
                v1_13, null, null,
                "net.minecraftforge.fml.common.registry.GameRegistry -> LegacyGameRegistryShim (1.13+)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)Lnet/minecraft/block/Block;",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim", "registerBlock",
                "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",
                v1_13, null, null,
                "GameRegistry.registerBlock(Block, String) -> LegacyGameRegistryShim.registerBlock (1.13+)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/Class;Ljava/lang/String;)Lnet/minecraft/block/Block;",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim", "registerBlock",
                "(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Object;",
                v1_13, null, null,
                "GameRegistry.registerBlock(Block, Class, String) -> LegacyGameRegistryShim.registerBlock (1.13+)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/fml/common/registry/GameRegistry", "registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)Lnet/minecraft/item/Item;",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim", "registerItem",
                "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",
                v1_13, null, null,
                "GameRegistry.registerItem(Item, String) -> LegacyGameRegistryShim.registerItem (1.13+)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/fml/common/registry/GameRegistry", "registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/item/Item;",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim", "registerItem",
                "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;",
                v1_13, null, null,
                "GameRegistry.registerItem(Item, String, String) -> LegacyGameRegistryShim.registerItem (1.13+)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/fml/common/registry/GameRegistry", "registerTileEntity",
                "(Ljava/lang/Class;Ljava/lang/String;)V",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim", "registerTileEntity",
                "(Ljava/lang/Class;Ljava/lang/String;)V",
                v1_13, null, null,
                "GameRegistry.registerTileEntity(Class, String) -> LegacyGameRegistryShim.registerTileEntity (1.13+)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/fml/common/registry/GameRegistry", "findBlock",
                "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/block/Block;",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim", "findBlock",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;",
                v1_13, null, null,
                "GameRegistry.findBlock -> LegacyGameRegistryShim.findBlock (1.13+)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/fml/common/registry/GameRegistry", "findItem",
                "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/item/Item;",
                "com/kyroxova/continuumlib/shims/LegacyGameRegistryShim", "findItem",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;",
                v1_13, null, null,
                "GameRegistry.findItem -> LegacyGameRegistryShim.findItem (1.13+)"
        ));

        // =========================================================================
        // 3. Block Metadata & Numeric ID Polyfill Rules (1.13+)
        // =========================================================================

        // Block.getStateFromMeta(int)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/block/Block", "getStateFromMeta",
                "(I)Lnet/minecraft/block/state/IBlockState;",
                "com/kyroxova/continuumlib/shims/LegacyMetadataShim", "toBlockState",
                "(Ljava/lang/Object;I)Ljava/lang/Object;",
                v1_13, null, null,
                "Block.getStateFromMeta(int) -> LegacyMetadataShim.toBlockState (1.13+)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/level/block/Block", "getStateFromMeta",
                "(I)Lnet/minecraft/world/level/block/state/BlockState;",
                "com/kyroxova/continuumlib/shims/LegacyMetadataShim", "toBlockState",
                "(Ljava/lang/Object;I)Ljava/lang/Object;",
                v1_13, null, null,
                "Block.getStateFromMeta(int) -> LegacyMetadataShim.toBlockState (1.13+)"
        ));

        // Block.getMetaFromState(IBlockState)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/block/Block", "getMetaFromState",
                "(Lnet/minecraft/block/state/IBlockState;)I",
                "com/kyroxova/continuumlib/shims/LegacyMetadataShim", "toMetadata",
                "(Ljava/lang/Object;Ljava/lang/Object;)I",
                v1_13, null, null,
                "Block.getMetaFromState(IBlockState) -> LegacyMetadataShim.toMetadata (1.13+)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/level/block/Block", "getMetaFromState",
                "(Lnet/minecraft/world/level/block/state/BlockState;)I",
                "com/kyroxova/continuumlib/shims/LegacyMetadataShim", "toMetadata",
                "(Ljava/lang/Object;Ljava/lang/Object;)I",
                v1_13, null, null,
                "Block.getMetaFromState(BlockState) -> LegacyMetadataShim.toMetadata (1.13+)"
        ));

        // Block.getIdFromBlock(Block) / Block.getId(BlockState)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/block/Block", "getIdFromBlock",
                "(Lnet/minecraft/block/Block;)I",
                "com/kyroxova/continuumlib/shims/LegacyMetadataShim", "getIdFromBlock",
                "(Ljava/lang/Object;)I",
                v1_13, null, null,
                "Block.getIdFromBlock(Block) -> LegacyMetadataShim.getIdFromBlock (1.13+)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/level/block/Block", "getId",
                "(Lnet/minecraft/world/level/block/state/BlockState;)I",
                "com/kyroxova/continuumlib/shims/LegacyMetadataShim", "getIdFromBlock",
                "(Ljava/lang/Object;)I",
                v1_13, null, null,
                "Block.getId(BlockState) -> LegacyMetadataShim.getIdFromBlock (1.13+)"
        ));

        // Block.getBlockById(int) / Block.stateById(int)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/block/Block", "getBlockById",
                "(I)Lnet/minecraft/block/Block;",
                "com/kyroxova/continuumlib/shims/LegacyMetadataShim", "getBlockById",
                "(I)Ljava/lang/Object;",
                v1_13, null, null,
                "Block.getBlockById(int) -> LegacyMetadataShim.getBlockById (1.13+)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/level/block/Block", "stateById",
                "(I)Lnet/minecraft/world/level/block/state/BlockState;",
                "com/kyroxova/continuumlib/shims/LegacyMetadataShim", "getBlockById",
                "(I)Ljava/lang/Object;",
                v1_13, null, null,
                "Block.stateById(int) -> LegacyMetadataShim.getBlockById (1.13+)"
        ));

        // Item.getIdFromItem(Item) / Item.getId(ItemStack)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/item/Item", "getIdFromItem",
                "(Lnet/minecraft/item/Item;)I",
                "com/kyroxova/continuumlib/shims/LegacyMetadataShim", "getIdFromItem",
                "(Ljava/lang/Object;)I",
                v1_13, null, null,
                "Item.getIdFromItem(Item) -> LegacyMetadataShim.getIdFromItem (1.13+)"
        ));

        // Item.getItemById(int) / Item.byId(int)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/item/Item", "getItemById",
                "(I)Lnet/minecraft/item/Item;",
                "com/kyroxova/continuumlib/shims/LegacyMetadataShim", "getItemById",
                "(I)Ljava/lang/Object;",
                v1_13, null, null,
                "Item.getItemById(int) -> LegacyMetadataShim.getItemById (1.13+)"
        ));

        // =========================================================================
        // 4. Pre-Flattening World (x, y, z) Coordinates & Metadata (1.8+)
        // =========================================================================

        // World.getBlock(int, int, int)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/World", "getBlock",
                "(III)Lnet/minecraft/block/Block;",
                "com/kyroxova/continuumlib/shims/LegacyMetadataShim", "getBlockAt",
                "(Ljava/lang/Object;III)Ljava/lang/Object;",
                v1_8, null, null,
                "World.getBlock(x,y,z) -> LegacyMetadataShim.getBlockAt (1.8+)"
        ));

        // World.getBlockMetadata(int, int, int)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/World", "getBlockMetadata",
                "(III)I",
                "com/kyroxova/continuumlib/shims/LegacyMetadataShim", "getMetadataAt",
                "(Ljava/lang/Object;III)I",
                v1_8, null, null,
                "World.getBlockMetadata(x,y,z) -> LegacyMetadataShim.getMetadataAt (1.8+)"
        ));

        // World.setBlock(int, int, int, Block, int, int)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/World", "setBlock",
                "(IIILnet/minecraft/block/Block;II)Z",
                "com/kyroxova/continuumlib/shims/LegacyMetadataShim", "setBlockAt",
                "(Ljava/lang/Object;IIILjava/lang/Object;II)Z",
                v1_8, null, null,
                "World.setBlock(x,y,z,...) -> LegacyMetadataShim.setBlockAt (1.8+)"
        ));

        // World.setBlockMetadataWithNotify(int, int, int, int, int)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/World", "setBlockMetadataWithNotify",
                "(IIIII)Z",
                "com/kyroxova/continuumlib/shims/LegacyMetadataShim", "setMetadataAt",
                "(Ljava/lang/Object;IIIII)Z",
                v1_8, null, null,
                "World.setBlockMetadataWithNotify(x,y,z,...) -> LegacyMetadataShim.setMetadataAt (1.8+)"
        ));
    }
}
