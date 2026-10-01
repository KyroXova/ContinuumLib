package com.example.buildscape;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(
            ForgeRegistries.BLOCKS,
            BuildScape.MODID);

    public static final RegistryObject<Block> BUILDERS_WORKBENCH = BLOCKS.register(
            "builders_workbench",
            () -> new BuildersWorkbenchBlock(
                    BlockBehaviour.Properties.of(Material.WOOD, MaterialColor.COLOR_BROWN)
                            .strength(2.5f)
                            .sound(SoundType.WOOD)));
}
