package com.kyroxova.continuumlib.resolver;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.cir.*;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.profile.*;

import java.util.Objects;

/**
 * Universal ContinuumLib Resolver Engine.
 * Takes base AST-analyzed CIR and resolves it against TargetProfile capabilities.
 */
public final class ContinuumResolver {

    private final ApiKnowledgeBase knowledgeBase;

    public ContinuumResolver(ApiKnowledgeBase knowledgeBase) {
        this.knowledgeBase = Objects.requireNonNull(knowledgeBase, "knowledgeBase cannot be null");
    }

    public ResolvedTargetModel resolve(CirCompilationUnit cir, TargetSpec baseSpec, TargetSpec targetSpec) {
        TargetProfile targetProfile = knowledgeBase.getTargetProfile(targetSpec);
        TargetProfile baseProfile = knowledgeBase.getTargetProfile(baseSpec);
        ResolvedTargetModel model = new ResolvedTargetModel(cir, baseSpec, targetProfile);

        // 1. Resolve Target Imports
        resolveImports(cir, baseProfile, targetProfile, model);

        // 2. Resolve Registries
        for (CirRegistryDeclaration reg : cir.getRegistries()) {
            resolveRegistryDeclaration(reg, baseProfile, targetProfile, model);
        }

        // 3. Resolve Registered Entries
        for (CirRegisteredEntry entry : cir.getRegisteredEntries()) {
            if (entry instanceof CirBlockDefinition blockDef) {
                resolveBlockDefinition(blockDef, baseProfile, targetProfile, model);
            } else if (entry instanceof CirBlockItemDefinition blockItemDef) {
                resolveBlockItemDefinition(blockItemDef, baseProfile, targetProfile, model);
            } else if (entry instanceof CirItemDefinition itemDef) {
                resolveItemDefinition(itemDef, baseProfile, targetProfile, model);
            } else if (entry instanceof CirBlockEntityDefinition beDef) {
                resolveBlockEntityDefinition(beDef, baseProfile, targetProfile, model);
            } else if (entry instanceof CirEntityDefinition entDef) {
                resolveEntityDefinition(entDef, baseProfile, targetProfile, model);
            } else if (entry instanceof CirSoundDefinition sndDef) {
                resolveSoundDefinition(sndDef, baseProfile, targetProfile, model);
            } else if (entry instanceof CirParticleDefinition partDef) {
                resolveParticleDefinition(partDef, baseProfile, targetProfile, model);
            } else if (entry instanceof CirMenuDefinition menuDef) {
                resolveMenuDefinition(menuDef, baseProfile, targetProfile, model);
            } else if (entry instanceof CirFluidDefinition fluidDef) {
                resolveFluidDefinition(fluidDef, baseProfile, targetProfile, model);
            } else if (entry instanceof CirRecipeSerializerDefinition recDef) {
                resolveRecipeSerializerDefinition(recDef, baseProfile, targetProfile, model);
            } else if (entry instanceof CirAliasDefinition aliasDef) {
                resolveAliasDefinition(aliasDef, baseProfile, targetProfile, model);
            } else if (entry instanceof CirCreativeTabDefinition tabDef) {
                resolveCreativeTabDefinition(tabDef, baseProfile, targetProfile, model);
            }
        }

        return model;
    }

    private void resolveImports(CirCompilationUnit cir, TargetProfile baseProfile, TargetProfile targetProfile, ResolvedTargetModel model) {
        // Base imports that should be adapted
        for (String imp : cir.getRawImports()) {
            if (targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.PAPER
                    || targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.SPIGOT) {
                if (imp.startsWith("net.minecraftforge.") || imp.startsWith("net.neoforged.") || imp.startsWith("net.minecraft.")) {
                    continue;
                }
            }

            if (imp.contains("net.minecraftforge.registries.ForgeRegistries")) {
                if (targetProfile.getTargetSpec().getLoader() != com.kyroxova.bootstrapper.environment.LoaderType.FORGE) {
                    continue;
                }
            } else if (imp.contains("net.minecraftforge.registries.RegistryObject")) {
                if (targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.NEOFORGE) {
                    boolean usedInUnmanaged = cir.getUnmanagedMethods().stream().anyMatch(m -> m.contains("RegistryObject"))
                            || cir.getUnmanagedFields().stream().anyMatch(f -> f.contains("RegistryObject"));
                    if (usedInUnmanaged) {
                        model.addTargetImport(imp);
                    }
                    continue;
                } else if (targetProfile.getTargetSpec().getLoader() != com.kyroxova.bootstrapper.environment.LoaderType.FORGE) {
                    continue;
                }
            } else if (imp.contains("net.minecraftforge.registries.DeferredRegister")) {
                if (targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.NEOFORGE) {
                    model.addTargetImport("net.neoforged.neoforge.registries.DeferredRegister");
                    continue;
                } else if (targetProfile.getTargetSpec().getLoader() != com.kyroxova.bootstrapper.environment.LoaderType.FORGE) {
                    continue;
                }
            } else if (imp.contains("net.minecraftforge.common.extensions.IForgeMenuType")) {
                if (targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.NEOFORGE) {
                    model.addTargetImport("net.neoforged.neoforge.common.extensions.IMenuTypeExtension");
                    continue;
                } else if (targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.FABRIC
                        || targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.QUILT) {
                    model.addTargetImport("net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType");
                    continue;
                } else if (targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.PAPER
                        || targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.SPIGOT) {
                    continue;
                }
            } else if (imp.contains("net.neoforged.neoforge.common.extensions.IMenuTypeExtension")) {
                if (targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.FORGE) {
                    model.addTargetImport("net.minecraftforge.common.extensions.IForgeMenuType");
                    continue;
                } else if (targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.FABRIC
                        || targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.QUILT) {
                    model.addTargetImport("net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType");
                    continue;
                }
            } else if (imp.contains("net.minecraft.world.level.material.Material")) {
                if (targetProfile.getBlockPropertiesCapability() == BlockPropertiesCapability.PROPERTIES_WITHOUT_MATERIAL) {
                    boolean usedInUnmanaged = cir.getUnmanagedMethods().stream().anyMatch(m -> m.contains("Material"))
                            || cir.getUnmanagedFields().stream().anyMatch(f -> f.contains("Material"));
                    if (!usedInUnmanaged) {
                        continue;
                    }
                }
            } else if (imp.contains("net.minecraft.resources.ResourceLocation")) {
                if (targetProfile.getIdentifierCapability() == IdentifierCapability.IDENTIFIER_FACTORY) {
                    model.addTargetImport("net.minecraft.resources.Identifier");
                    boolean usedInUnmanaged = cir.getUnmanagedMethods().stream().anyMatch(m -> m.contains("ResourceLocation"))
                            || cir.getUnmanagedFields().stream().anyMatch(f -> f.contains("ResourceLocation"));
                    if (!usedInUnmanaged) {
                        continue;
                    }
                }
            }

            // Loader-specific raw import filtering
            if (imp.startsWith("net.minecraftforge.") && targetProfile.getTargetSpec().getLoader() != com.kyroxova.bootstrapper.environment.LoaderType.FORGE) {
                continue;
            }
            if (imp.startsWith("net.neoforged.") && targetProfile.getTargetSpec().getLoader() != com.kyroxova.bootstrapper.environment.LoaderType.NEOFORGE) {
                continue;
            }

            model.addTargetImport(imp);
        }

        // Add loader-specific imports required for target capabilities
        if (targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.NEOFORGE) {
            model.addTargetImport("net.neoforged.neoforge.registries.DeferredRegister");
            model.addTargetImport("net.neoforged.neoforge.registries.DeferredBlock");
            model.addTargetImport("net.neoforged.neoforge.registries.DeferredItem");
            model.addTargetImport("net.neoforged.neoforge.registries.DeferredHolder");
            model.addTargetImport("net.minecraft.core.registries.Registries");
            if (targetProfile.getIdentifierCapability() == IdentifierCapability.IDENTIFIER_FACTORY) {
                model.addTargetImport("net.minecraft.resources.Identifier");
            } else {
                model.addTargetImport("net.minecraft.resources.ResourceLocation");
            }
        } else if (targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.FORGE) {
            model.addTargetImport("net.minecraftforge.registries.DeferredRegister");
            model.addTargetImport("net.minecraftforge.registries.ForgeRegistries");
            model.addTargetImport("net.minecraftforge.registries.RegistryObject");
            model.addTargetImport("net.minecraft.resources.ResourceLocation");
        } else if (targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.FABRIC
                || targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.QUILT) {
            model.addTargetImport("net.minecraft.core.Registry");
            model.addTargetImport("net.minecraft.resources.ResourceLocation");
            if (targetProfile.usesBuiltInRegistries()) {
                model.addTargetImport("net.minecraft.core.registries.BuiltInRegistries");
            }
            model.addTargetImport("net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder");
            model.addTargetImport("net.fabricmc.fabric.api.particle.v1.FabricParticleTypes");
            model.addTargetImport("net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType");
        } else if (targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.PAPER
                || targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.SPIGOT) {
            model.addTargetImport("org.bukkit.plugin.java.JavaPlugin");
            model.addTargetImport("org.bukkit.NamespacedKey");
            model.addTargetImport("org.bukkit.event.Listener");
            model.addTargetImport("org.bukkit.event.EventHandler");
            model.addTargetImport("org.bukkit.event.player.PlayerInteractEvent");
        }

        boolean hasBlocks = cir.getRegisteredEntries().stream().anyMatch(e -> e instanceof CirBlockDefinition);
        if (hasBlocks && targetProfile.getBlockPropertiesCapability() == BlockPropertiesCapability.PROPERTIES_WITH_MATERIAL) {
            model.addTargetImport("net.minecraft.world.level.material.Material");
        }

        boolean hasMenus = cir.getRegisteredEntries().stream().anyMatch(e -> e instanceof CirMenuDefinition);
        if (hasMenus) {
            if (targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.NEOFORGE) {
                model.addTargetImport("net.neoforged.neoforge.common.extensions.IMenuTypeExtension");
            } else if (targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.FORGE) {
                model.addTargetImport("net.minecraftforge.common.extensions.IForgeMenuType");
            } else if (targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.FABRIC
                    || targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.QUILT) {
                model.addTargetImport("net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType");
            }
        }

        boolean hasCreativeTabs = cir.getRegisteredEntries().stream().anyMatch(e -> e instanceof CirCreativeTabDefinition);
        if (hasCreativeTabs) {
            if (targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.NEOFORGE) {
                model.addTargetImport("net.minecraft.world.item.CreativeModeTab");
                model.addTargetImport("net.minecraft.network.chat.Component");
                model.addTargetImport("net.minecraft.world.item.ItemStack");
            } else if (targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.FABRIC
                    || targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.QUILT) {
                model.addTargetImport("net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup");
                model.addTargetImport("net.minecraft.world.item.CreativeModeTab");
                model.addTargetImport("net.minecraft.network.chat.Component");
                model.addTargetImport("net.minecraft.world.item.ItemStack");
            } else if (targetProfile.getTargetSpec().getLoader() == com.kyroxova.bootstrapper.environment.LoaderType.FORGE) {
                model.addTargetImport("net.minecraft.world.item.CreativeModeTab");
                model.addTargetImport("net.minecraft.world.item.ItemStack");
            }
        }
    }

    private void resolveRegistryDeclaration(CirRegistryDeclaration reg, TargetProfile baseProfile, TargetProfile targetProfile, ResolvedTargetModel model) {
        RegistryCapability baseCap = baseProfile.getRegistryCapability(reg.getRegistryType());
        RegistryCapability targetCap = targetProfile.getRegistryCapability(reg.getRegistryType());

        String location = reg.getEnclosingClassName() + "." + reg.getFieldName();

        if (baseCap == targetCap) {
            model.addResult(CirResolutionResult.exact("REGISTER_REGISTRY<" + reg.getRegistryType() + ">", location));
        } else {
            String msg = String.format("Adapted registry '%s' from %s to %s", reg.getFieldName(), baseCap, targetCap);
            model.addResult(CirResolutionResult.adapted("REGISTER_REGISTRY<" + reg.getRegistryType() + ">",
                    targetCap.name(), msg, location));
        }
    }

    private void resolveBlockDefinition(CirBlockDefinition blockDef, TargetProfile baseProfile, TargetProfile targetProfile, ResolvedTargetModel model) {
        String location = blockDef.getFieldName();

        // 1. Registered Reference Resolution
        ReferenceCapability baseRef = baseProfile.getReferenceCapability(CirRegistryType.BLOCK);
        ReferenceCapability targetRef = targetProfile.getReferenceCapability(CirRegistryType.BLOCK);
        if (baseRef == targetRef) {
            model.addResult(CirResolutionResult.exact("REGISTERED_REFERENCE<BLOCK>", location));
        } else {
            model.addResult(CirResolutionResult.adapted("REGISTERED_REFERENCE<BLOCK>", targetRef.name(),
                    "Translated reference handle to " + targetRef, location));
        }

        // 2. Block Properties Resolution
        BlockPropertiesCapability baseProps = baseProfile.getBlockPropertiesCapability();
        BlockPropertiesCapability targetProps = targetProfile.getBlockPropertiesCapability();
        if (baseProps == targetProps) {
            model.addResult(CirResolutionResult.exact("BLOCK_PROPERTIES", location));
        } else {
            model.addResult(CirResolutionResult.adapted("BLOCK_PROPERTIES", targetProps.name(),
                    "Adapted properties (Material migration to 1.20+ properties)", location));
        }

        // 3. Block Registration Method
        RegistryCapability targetRegCap = targetProfile.getRegistryCapability(CirRegistryType.BLOCK);
        if (targetRegCap == RegistryCapability.NEOFORGE_DEFERRED_BLOCKS) {
            model.addResult(CirResolutionResult.adapted("REGISTER_BLOCK", "registerBlock",
                    "Adapted registration to NeoForge registerBlock", location));
        } else if (targetRegCap == RegistryCapability.FABRIC_REGISTRY) {
            model.addResult(CirResolutionResult.adapted("REGISTER_BLOCK", "Registry.register",
                    "Adapted registration to Fabric Registry.register", location));
        } else if (targetRegCap == RegistryCapability.PAPER_PLUGIN_REGISTRY) {
            model.addResult(CirResolutionResult.adapted("REGISTER_BLOCK", "NamespacedKey",
                    "Adapted block registration to Paper NamespacedKey", location));
        } else {
            model.addResult(CirResolutionResult.exact("REGISTER_BLOCK", location));
        }
    }

    private void resolveBlockItemDefinition(CirBlockItemDefinition blockItemDef, TargetProfile baseProfile, TargetProfile targetProfile, ResolvedTargetModel model) {
        String location = blockItemDef.getFieldName();
        ReferenceCapability targetRef = targetProfile.getReferenceCapability(CirRegistryType.ITEM);
        model.addResult(CirResolutionResult.adapted("REGISTERED_REFERENCE<ITEM>", targetRef.name(),
                "Translated block item handle to " + targetRef, location));
        if (targetProfile.getRegistryCapability(CirRegistryType.ITEM) == RegistryCapability.FABRIC_REGISTRY) {
            model.addResult(CirResolutionResult.adapted("REGISTER_BLOCK_ITEM", "Registry.register",
                    "Adapted block item registration to Fabric Registry.register", location));
        } else if (targetProfile.getRegistryCapability(CirRegistryType.ITEM) == RegistryCapability.PAPER_PLUGIN_REGISTRY) {
            model.addResult(CirResolutionResult.adapted("REGISTER_BLOCK_ITEM", "NamespacedKey",
                    "Adapted block item registration to Paper NamespacedKey", location));
        } else {
            model.addResult(CirResolutionResult.adapted("REGISTER_BLOCK_ITEM", "registerSimpleBlockItem",
                    "Adapted block item registration", location));
        }
    }

    private void resolveItemDefinition(CirItemDefinition itemDef, TargetProfile baseProfile, TargetProfile targetProfile, ResolvedTargetModel model) {
        String location = itemDef.getFieldName();
        ReferenceCapability targetRef = targetProfile.getReferenceCapability(CirRegistryType.ITEM);
        if (baseProfile.getReferenceCapability(CirRegistryType.ITEM) == targetRef) {
            model.addResult(CirResolutionResult.exact("REGISTERED_REFERENCE<ITEM>", location));
        } else {
            model.addResult(CirResolutionResult.adapted("REGISTERED_REFERENCE<ITEM>", targetRef.name(),
                    "Translated item handle to " + targetRef, location));
        }
        if (targetProfile.getRegistryCapability(CirRegistryType.ITEM) == RegistryCapability.FABRIC_REGISTRY) {
            model.addResult(CirResolutionResult.adapted("REGISTER_ITEM", "Registry.register",
                    "Adapted item registration to Fabric Registry.register", location));
        } else if (targetProfile.getRegistryCapability(CirRegistryType.ITEM) == RegistryCapability.PAPER_PLUGIN_REGISTRY) {
            model.addResult(CirResolutionResult.adapted("REGISTER_ITEM", "NamespacedKey",
                    "Adapted item registration to Paper NamespacedKey", location));
        } else {
            model.addResult(CirResolutionResult.adapted("REGISTER_ITEM", "registerItem",
                    "Adapted item registration", location));
        }
    }

    private void resolveBlockEntityDefinition(CirBlockEntityDefinition beDef, TargetProfile baseProfile, TargetProfile targetProfile, ResolvedTargetModel model) {
        String location = beDef.getFieldName();
        ReferenceCapability targetRef = targetProfile.getReferenceCapability(CirRegistryType.BLOCK_ENTITY_TYPE);
        model.addResult(CirResolutionResult.adapted("REGISTERED_REFERENCE<BLOCK_ENTITY>", targetRef.name(),
                "Translated block entity handle to " + targetRef, location));
        model.addResult(CirResolutionResult.exact("REGISTER_BLOCK_ENTITY", location));
    }

    private void resolveEntityDefinition(CirEntityDefinition entDef, TargetProfile baseProfile, TargetProfile targetProfile, ResolvedTargetModel model) {
        String location = entDef.getFieldName();
        ReferenceCapability targetRef = targetProfile.getReferenceCapability(CirRegistryType.ENTITY_TYPE);
        model.addResult(CirResolutionResult.adapted("REGISTERED_REFERENCE<ENTITY>", targetRef.name(),
                "Translated entity handle to " + targetRef, location));
        model.addResult(CirResolutionResult.exact("REGISTER_ENTITY", location));
    }

    private void resolveSoundDefinition(CirSoundDefinition sndDef, TargetProfile baseProfile, TargetProfile targetProfile, ResolvedTargetModel model) {
        String location = sndDef.getFieldName();
        ReferenceCapability targetRef = targetProfile.getReferenceCapability(CirRegistryType.SOUND_EVENT);
        model.addResult(CirResolutionResult.adapted("REGISTERED_REFERENCE<SOUND>", targetRef.name(),
                "Translated sound event handle to " + targetRef, location));
        model.addResult(CirResolutionResult.exact("REGISTER_SOUND", location));
    }

    private void resolveParticleDefinition(CirParticleDefinition partDef, TargetProfile baseProfile, TargetProfile targetProfile, ResolvedTargetModel model) {
        String location = partDef.getFieldName();
        ReferenceCapability targetRef = targetProfile.getReferenceCapability(CirRegistryType.PARTICLE_TYPE);
        model.addResult(CirResolutionResult.adapted("REGISTERED_REFERENCE<PARTICLE>", targetRef.name(),
                "Translated particle handle to " + targetRef, location));
        model.addResult(CirResolutionResult.exact("REGISTER_PARTICLE", location));
    }

    private void resolveMenuDefinition(CirMenuDefinition menuDef, TargetProfile baseProfile, TargetProfile targetProfile, ResolvedTargetModel model) {
        String location = menuDef.getFieldName();
        ReferenceCapability targetRef = targetProfile.getReferenceCapability(CirRegistryType.MENU);
        model.addResult(CirResolutionResult.adapted("REGISTERED_REFERENCE<MENU>", targetRef.name(),
                "Translated menu handle to " + targetRef, location));
        model.addResult(CirResolutionResult.exact("REGISTER_MENU", location));
    }

    private void resolveFluidDefinition(CirFluidDefinition fluidDef, TargetProfile baseProfile, TargetProfile targetProfile, ResolvedTargetModel model) {
        String location = fluidDef.getFieldName();
        ReferenceCapability targetRef = targetProfile.getReferenceCapability(CirRegistryType.FLUID);
        model.addResult(CirResolutionResult.adapted("REGISTERED_REFERENCE<FLUID>", targetRef.name(),
                "Translated fluid handle to " + targetRef, location));
        model.addResult(CirResolutionResult.exact("REGISTER_FLUID", location));
    }

    private void resolveRecipeSerializerDefinition(CirRecipeSerializerDefinition recDef, TargetProfile baseProfile, TargetProfile targetProfile, ResolvedTargetModel model) {
        String location = recDef.getFieldName();
        ReferenceCapability targetRef = targetProfile.getReferenceCapability(CirRegistryType.RECIPE_SERIALIZER);
        model.addResult(CirResolutionResult.adapted("REGISTERED_REFERENCE<RECIPE_SERIALIZER>", targetRef.name(),
                "Translated recipe serializer handle to " + targetRef, location));
        model.addResult(CirResolutionResult.exact("REGISTER_RECIPE_SERIALIZER", location));
    }

    private void resolveAliasDefinition(CirAliasDefinition aliasDef, TargetProfile baseProfile, TargetProfile targetProfile, ResolvedTargetModel model) {
        String location = aliasDef.getFieldName();
        model.addResult(CirResolutionResult.exact("ALIAS_REFERENCE", location));
    }

    private void resolveCreativeTabDefinition(CirCreativeTabDefinition tabDef, TargetProfile baseProfile, TargetProfile targetProfile, ResolvedTargetModel model) {
        String location = tabDef.getFieldName();
        ReferenceCapability targetRef = targetProfile.getReferenceCapability(CirRegistryType.CREATIVE_MODE_TAB);
        model.addResult(CirResolutionResult.adapted("REGISTERED_REFERENCE<CREATIVE_MODE_TAB>", targetRef.name(),
                "Translated creative tab handle to " + targetRef, location));
        model.addResult(CirResolutionResult.exact("REGISTER_CREATIVE_TAB", location));
    }
}
