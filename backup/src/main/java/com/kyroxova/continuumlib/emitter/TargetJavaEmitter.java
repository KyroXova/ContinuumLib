package com.kyroxova.continuumlib.emitter;

import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.continuumlib.cir.*;
import com.kyroxova.continuumlib.knowledgebase.profile.*;
import com.kyroxova.continuumlib.resolver.ResolvedTargetModel;

import java.util.*;

/**
 * Emits clean, native, idiomatic target Java source code from a resolved model.
 */
public final class TargetJavaEmitter {

    public String emit(ResolvedTargetModel model) {
        CirCompilationUnit cir = model.getCir();
        TargetProfile profile = model.getTargetProfile();
        StringBuilder sb = new StringBuilder();

        // 1. Package
        if (cir.getPackageName() != null && !cir.getPackageName().isBlank()) {
            sb.append("package ").append(cir.getPackageName()).append(";\n\n");
        }

        // 2. Imports
        List<String> sortedImports = new ArrayList<>(model.getTargetImports());
        Collections.sort(sortedImports);
        for (String imp : sortedImports) {
            sb.append("import ").append(imp).append(";\n");
        }
        sb.append("\n");

        // 3. Class declaration
        String className = cir.getPrimaryClassName() != null ? cir.getPrimaryClassName() : "ModRegistries";
        boolean isPaperOrSpigot = profile.getTargetSpec().getLoader() == LoaderType.PAPER || profile.getTargetSpec().getLoader() == LoaderType.SPIGOT;
        if (isPaperOrSpigot) {
            sb.append("public class ").append(className).append(" extends JavaPlugin implements Listener {\n\n");
        } else {
            sb.append("public class ").append(className);
            if (cir.getSuperClass() != null && !cir.getSuperClass().isBlank()) {
                sb.append(" extends ").append(cir.getSuperClass());
            }
            if (cir.getImplementedInterfaces() != null && !cir.getImplementedInterfaces().isEmpty()) {
                sb.append(" implements ").append(String.join(", ", cir.getImplementedInterfaces()));
            }
            sb.append(" {\n\n");
        }

        // 4. Emit Unmanaged Fields (e.g. public static final String MOD_ID = "...")
        for (String field : cir.getUnmanagedFields()) {
            if (isPaperOrSpigot) {
                if (field.contains("Property") || field.contains("VoxelShape") || field.contains("BlockBehaviour") || field.contains("SoundType")) {
                    continue;
                }
            }
            sb.append("    ").append(field.replace("\n", "\n    ")).append("\n\n");
        }

        // 5. Emit Registries
        for (CirRegistryDeclaration reg : cir.getRegistries()) {
            emitRegistryDeclaration(reg, profile, sb);
            sb.append("\n");
        }

        // 6. Emit Registered Entries
        for (CirRegisteredEntry entry : cir.getRegisteredEntries()) {
            if (entry instanceof CirBlockDefinition blockDef) {
                emitBlockDefinition(blockDef, profile, sb);
            } else if (entry instanceof CirBlockItemDefinition blockItemDef) {
                emitBlockItemDefinition(blockItemDef, profile, sb);
            } else if (entry instanceof CirItemDefinition itemDef) {
                emitItemDefinition(itemDef, profile, sb);
            } else if (entry instanceof CirBlockEntityDefinition beDef) {
                emitBlockEntityDefinition(beDef, profile, sb);
            } else if (entry instanceof CirEntityDefinition entDef) {
                emitEntityDefinition(entDef, profile, sb);
            } else if (entry instanceof CirSoundDefinition sndDef) {
                emitSoundDefinition(sndDef, cir, profile, sb);
            } else if (entry instanceof CirParticleDefinition partDef) {
                emitParticleDefinition(partDef, profile, sb);
            } else if (entry instanceof CirMenuDefinition menuDef) {
                emitMenuDefinition(menuDef, profile, sb);
            } else if (entry instanceof CirFluidDefinition fluidDef) {
                emitFluidDefinition(fluidDef, profile, sb);
            } else if (entry instanceof CirRecipeSerializerDefinition recDef) {
                emitRecipeSerializerDefinition(recDef, profile, sb);
            } else if (entry instanceof CirAliasDefinition aliasDef) {
                emitAliasDefinition(aliasDef, profile, sb);
            } else if (entry instanceof CirCreativeTabDefinition tabDef) {
                emitCreativeTabDefinition(tabDef, profile, sb);
            }
            sb.append("\n");
        }

        // 7. Emit Unmanaged Methods / Constructors
        boolean hadBlockInteraction = false;
        for (String method : cir.getUnmanagedMethods()) {
            if (isPaperOrSpigot) {
                if (method.contains("RegistryObject") || method.contains(".register(")
                        || method.contains("getShape(") || method.contains("createBlockStateDefinition(")
                        || method.contains("getStateForPlacement(") || method.contains("registerDefaultState(")
                        || method.contains(className + "(") || method.contains("BlockBehaviour.Properties")) {
                    continue;
                }
                if (method.contains("use(") || method.contains("InteractionResult")) {
                    hadBlockInteraction = true;
                    continue;
                }
            } else if (profile.getTargetSpec().getLoader() == LoaderType.FABRIC || profile.getTargetSpec().getLoader() == LoaderType.QUILT) {
                if (method.contains("RegistryObject") || method.contains(".register(")) {
                    continue;
                }
            }

            // Adapt use(...) to modern useWithoutItem / useItemOn for NeoForge and Fabric/Quilt if applicable
            String emittedMethod = method;
            if (profile.getTargetSpec().getLoader() == LoaderType.NEOFORGE
                    || profile.getTargetSpec().getLoader() == LoaderType.FABRIC
                    || profile.getTargetSpec().getLoader() == LoaderType.QUILT) {
                if (emittedMethod.contains("InteractionResult use(") && emittedMethod.contains("InteractionHand")) {
                    int paramStart = emittedMethod.indexOf("use(");
                    int paramEnd = emittedMethod.indexOf(')', paramStart);
                    String body = paramEnd != -1 ? emittedMethod.substring(paramEnd + 1) : "";
                    boolean handUsedInBody = body.contains("hand");

                    if (handUsedInBody) {
                        emittedMethod = emittedMethod.replace("use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)",
                                "useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)");
                    } else {
                        emittedMethod = emittedMethod.replace("use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)",
                                "useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)");
                    }
                }
            }

            sb.append("    ").append(emittedMethod.replace("\n", "\n    ")).append("\n\n");
        }

        if (isPaperOrSpigot) {
            boolean hasModId = cir.getUnmanagedFields().stream().anyMatch(f -> f.contains("MOD_ID"));
            String idRef = hasModId ? "MOD_ID" : ("\"" + (cir.getPrimaryClassName() != null ? cir.getPrimaryClassName().toLowerCase() : "continuumlib") + "\"");
            if (hadBlockInteraction) {
                sb.append("    @EventHandler\n");
                sb.append("    public void onPlayerInteract(PlayerInteractEvent event) {\n");
                sb.append("        if (event.getClickedBlock() != null) {\n");
                sb.append("            // Block interaction adapted from use(...)\n");
                sb.append("        }\n");
                sb.append("    }\n\n");
            }
            sb.append("    @Override\n");
            sb.append("    public void onEnable() {\n");
            sb.append("        getServer().getPluginManager().registerEvents(this, this);\n");
            sb.append("        getLogger().info(\"[ContinuumLib] Plugin initialized for \" + ").append(idRef).append(");\n");
            sb.append("    }\n\n");
            sb.append("    @Override\n");
            sb.append("    public void onDisable() {\n");
            sb.append("        getLogger().info(\"[ContinuumLib] Plugin disabled for \" + ").append(idRef).append(");\n");
            sb.append("    }\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private void emitRegistryDeclaration(CirRegistryDeclaration reg, TargetProfile profile, StringBuilder sb) {
        if (profile.getTargetSpec().getLoader() == LoaderType.FABRIC || profile.getTargetSpec().getLoader() == LoaderType.QUILT
                || profile.getTargetSpec().getLoader() == LoaderType.PAPER || profile.getTargetSpec().getLoader() == LoaderType.SPIGOT
                || (profile.getTargetSpec().getLoader() == LoaderType.FORGE && reg.getRegistryType() == CirRegistryType.CREATIVE_MODE_TAB)) {
            return;
        }

        sb.append("    public static final ");

        String modIdArg = (reg.getRawModIdExpression() != null && !reg.getRawModIdExpression().isBlank())
                ? reg.getRawModIdExpression()
                : ("\"" + reg.getModId() + "\"");

        if (profile.getTargetSpec().getLoader() == LoaderType.NEOFORGE) {
            if (reg.getRegistryType() == CirRegistryType.BLOCK) {
                sb.append("DeferredRegister.Blocks ").append(reg.getFieldName())
                        .append(" = DeferredRegister.createBlocks(").append(modIdArg).append(");\n");
                return;
            } else if (reg.getRegistryType() == CirRegistryType.ITEM) {
                sb.append("DeferredRegister.Items ").append(reg.getFieldName())
                        .append(" = DeferredRegister.createItems(").append(modIdArg).append(");\n");
                return;
            } else {
                String neoforgeKey = switch (reg.getRegistryType()) {
                    case BLOCK_ENTITY_TYPE -> "Registries.BLOCK_ENTITY_TYPE";
                    case ENTITY_TYPE -> "Registries.ENTITY_TYPE";
                    case SOUND_EVENT -> "Registries.SOUND_EVENT";
                    case PARTICLE_TYPE -> "Registries.PARTICLE_TYPE";
                    case MENU -> "Registries.MENU";
                    case FLUID -> "Registries.FLUID";
                    case RECIPE_SERIALIZER -> "Registries.RECIPE_SERIALIZER";
                    case CREATIVE_MODE_TAB -> "Registries.CREATIVE_MODE_TAB";
                    default -> "Registries.BLOCK";
                };
                String typeParam = switch (reg.getRegistryType()) {
                    case BLOCK_ENTITY_TYPE -> "BlockEntityType<?>";
                    case ENTITY_TYPE -> "EntityType<?>";
                    case SOUND_EVENT -> "SoundEvent";
                    case PARTICLE_TYPE -> "ParticleType<?>";
                    case MENU -> "MenuType<?>";
                    case FLUID -> "Fluid";
                    case RECIPE_SERIALIZER -> "RecipeSerializer<?>";
                    case CREATIVE_MODE_TAB -> "CreativeModeTab";
                    default -> "Object";
                };
                sb.append("DeferredRegister<").append(typeParam).append("> ").append(reg.getFieldName())
                        .append(" = DeferredRegister.create(").append(neoforgeKey).append(", ").append(modIdArg).append(");\n");
                return;
            }
        }

        String typeParam = switch (reg.getRegistryType()) {
            case BLOCK -> "Block";
            case ITEM -> "Item";
            case BLOCK_ENTITY_TYPE -> "BlockEntityType<?>";
            case ENTITY_TYPE -> "EntityType<?>";
            case SOUND_EVENT -> "SoundEvent";
            case PARTICLE_TYPE -> "ParticleType<?>";
            case MENU -> "MenuType<?>";
            case FLUID -> "Fluid";
            case RECIPE_SERIALIZER -> "RecipeSerializer<?>";
            case CREATIVE_MODE_TAB -> "CreativeModeTab";
            default -> "Object";
        };

        String forgeRegistryConst = switch (reg.getRegistryType()) {
            case BLOCK -> "ForgeRegistries.BLOCKS";
            case ITEM -> "ForgeRegistries.ITEMS";
            case BLOCK_ENTITY_TYPE -> "ForgeRegistries.BLOCK_ENTITIES";
            case ENTITY_TYPE -> "ForgeRegistries.ENTITIES";
            case SOUND_EVENT -> "ForgeRegistries.SOUND_EVENTS";
            case PARTICLE_TYPE -> "ForgeRegistries.PARTICLE_TYPES";
            case MENU -> "ForgeRegistries.CONTAINERS";
            case FLUID -> "ForgeRegistries.FLUIDS";
            case RECIPE_SERIALIZER -> "ForgeRegistries.RECIPE_SERIALIZERS";
            case CREATIVE_MODE_TAB -> "ForgeRegistries.CREATIVE_MODE_TABS";
            default -> "ForgeRegistries.BLOCKS";
        };

        sb.append("DeferredRegister<").append(typeParam).append("> ").append(reg.getFieldName())
                .append(" = DeferredRegister.create(").append(forgeRegistryConst)
                .append(", ").append(modIdArg).append(");\n");
    }

    private void emitBlockDefinition(CirBlockDefinition blockDef, TargetProfile profile, StringBuilder sb) {
        String impl = blockDef.getImplementationType();
        sb.append("    public static final ");

        if (profile.getTargetSpec().getLoader() == LoaderType.PAPER || profile.getTargetSpec().getLoader() == LoaderType.SPIGOT) {
            sb.append("NamespacedKey ").append(blockDef.getFieldName())
                    .append("_KEY = new NamespacedKey(MOD_ID, \"").append(blockDef.getRegistrationId()).append("\");\n");
        } else if (profile.getTargetSpec().getLoader() == LoaderType.FABRIC || profile.getTargetSpec().getLoader() == LoaderType.QUILT) {
            sb.append(impl).append(" ").append(blockDef.getFieldName())
                    .append(" = Registry.register(").append(fabricReg("BLOCK", profile)).append(", new ResourceLocation(MOD_ID, \"")
                    .append(blockDef.getRegistrationId()).append("\"), new ")
                    .append(impl).append("(")
                    .append(emitBlockProperties(blockDef.getProperties(), profile))
                    .append("));\n");
        } else if (profile.getTargetSpec().getLoader() == LoaderType.NEOFORGE) {
            // NeoForge emission: DeferredBlock<T>
            sb.append("DeferredBlock<").append(impl).append("> ").append(blockDef.getFieldName())
                    .append(" = ").append(blockDef.getRegistryFieldName())
                    .append(".registerBlock(\"").append(blockDef.getRegistrationId()).append("\", ")
                    .append(impl).append("::new, ")
                    .append(emitBlockProperties(blockDef.getProperties(), profile))
                    .append(");\n");
        } else {
            // Forge emission: RegistryObject<T>
            sb.append("RegistryObject<").append(impl).append("> ").append(blockDef.getFieldName())
                    .append(" = ").append(blockDef.getRegistryFieldName())
                    .append(".register(\"").append(blockDef.getRegistrationId()).append("\", () -> new ")
                    .append(impl).append("(")
                    .append(emitBlockProperties(blockDef.getProperties(), profile))
                    .append("));\n");
        }
    }

    private void emitBlockItemDefinition(CirBlockItemDefinition blockItemDef, TargetProfile profile, StringBuilder sb) {
        sb.append("    public static final ");

        if (profile.getTargetSpec().getLoader() == LoaderType.PAPER || profile.getTargetSpec().getLoader() == LoaderType.SPIGOT) {
            sb.append("NamespacedKey ").append(blockItemDef.getFieldName())
                    .append("_KEY = new NamespacedKey(MOD_ID, \"").append(blockItemDef.getRegistrationId()).append("\");\n");
        } else if (profile.getTargetSpec().getLoader() == LoaderType.FABRIC || profile.getTargetSpec().getLoader() == LoaderType.QUILT) {
            sb.append("BlockItem ").append(blockItemDef.getFieldName())
                    .append(" = Registry.register(").append(fabricReg("ITEM", profile)).append(", new ResourceLocation(MOD_ID, \"")
                    .append(blockItemDef.getRegistrationId()).append("\"), new BlockItem(")
                    .append(blockItemDef.getBlockReference()).append(", ")
                    .append(emitItemProperties(blockItemDef.getProperties(), profile))
                    .append("));\n");
        } else if (profile.getTargetSpec().getLoader() == LoaderType.NEOFORGE) {
            // NeoForge emission: DeferredItem<BlockItem>
            sb.append("DeferredItem<BlockItem> ").append(blockItemDef.getFieldName())
                    .append(" = ").append(blockItemDef.getRegistryFieldName())
                    .append(".registerSimpleBlockItem(\"").append(blockItemDef.getRegistrationId()).append("\", ")
                    .append(blockItemDef.getBlockReference()).append(");\n");
        } else {
            // Forge emission: RegistryObject<Item>
            sb.append("RegistryObject<Item> ").append(blockItemDef.getFieldName())
                    .append(" = ").append(blockItemDef.getRegistryFieldName())
                    .append(".register(\"").append(blockItemDef.getRegistrationId()).append("\", () -> new BlockItem(")
                    .append(blockItemDef.getBlockReference()).append(".get(), ")
                    .append(emitItemProperties(blockItemDef.getProperties(), profile))
                    .append("));\n");
        }
    }

    private void emitItemDefinition(CirItemDefinition itemDef, TargetProfile profile, StringBuilder sb) {
        String impl = itemDef.getImplementationType();
        sb.append("    public static final ");

        if (profile.getTargetSpec().getLoader() == LoaderType.PAPER || profile.getTargetSpec().getLoader() == LoaderType.SPIGOT) {
            sb.append("NamespacedKey ").append(itemDef.getFieldName())
                    .append("_KEY = new NamespacedKey(MOD_ID, \"").append(itemDef.getRegistrationId()).append("\");\n");
        } else if (profile.getTargetSpec().getLoader() == LoaderType.FABRIC || profile.getTargetSpec().getLoader() == LoaderType.QUILT) {
            sb.append(impl).append(" ").append(itemDef.getFieldName())
                    .append(" = Registry.register(").append(fabricReg("ITEM", profile)).append(", new ResourceLocation(MOD_ID, \"")
                    .append(itemDef.getRegistrationId()).append("\"), new ")
                    .append(impl).append("(")
                    .append(emitItemProperties(itemDef.getProperties(), profile))
                    .append("));\n");
        } else if (profile.getTargetSpec().getLoader() == LoaderType.NEOFORGE) {
            // NeoForge emission: DeferredItem<T>
            if (itemDef.isStandardItem()) {
                sb.append("DeferredItem<Item> ").append(itemDef.getFieldName())
                        .append(" = ").append(itemDef.getRegistryFieldName())
                        .append(".registerSimpleItem(\"").append(itemDef.getRegistrationId()).append("\", ")
                        .append(emitItemProperties(itemDef.getProperties(), profile))
                        .append(");\n");
            } else {
                sb.append("DeferredItem<").append(impl).append("> ").append(itemDef.getFieldName())
                        .append(" = ").append(itemDef.getRegistryFieldName())
                        .append(".registerItem(\"").append(itemDef.getRegistrationId()).append("\", ")
                        .append(impl).append("::new, ")
                        .append(emitItemProperties(itemDef.getProperties(), profile))
                        .append(");\n");
            }
        } else {
            // Forge emission: RegistryObject<T>
            sb.append("RegistryObject<").append(impl).append("> ").append(itemDef.getFieldName())
                    .append(" = ").append(itemDef.getRegistryFieldName())
                    .append(".register(\"").append(itemDef.getRegistrationId()).append("\", () -> new ")
                    .append(impl).append("(")
                    .append(emitItemProperties(itemDef.getProperties(), profile))
                    .append("));\n");
        }
    }

    private void emitBlockEntityDefinition(CirBlockEntityDefinition beDef, TargetProfile profile, StringBuilder sb) {
        String entityClass = beDef.getBlockEntityClass();
        sb.append("    public static final ");

        if (profile.getTargetSpec().getLoader() == LoaderType.PAPER || profile.getTargetSpec().getLoader() == LoaderType.SPIGOT) {
            sb.append("NamespacedKey ").append(beDef.getFieldName())
                    .append("_KEY = new NamespacedKey(MOD_ID, \"").append(beDef.getRegistrationId()).append("\");\n");
        } else if (profile.getTargetSpec().getLoader() == LoaderType.FABRIC || profile.getTargetSpec().getLoader() == LoaderType.QUILT) {
            String fabricBlockRefs = String.join(", ", beDef.getValidBlockReferences());
            sb.append("BlockEntityType<").append(entityClass).append("> ").append(beDef.getFieldName())
                    .append(" = Registry.register(").append(fabricReg("BLOCK_ENTITY_TYPE", profile)).append(", new ResourceLocation(MOD_ID, \"")
                    .append(beDef.getRegistrationId()).append("\"), FabricBlockEntityTypeBuilder.create(")
                    .append(entityClass).append("::new, ").append(fabricBlockRefs).append(").build());\n");
        } else if (profile.getTargetSpec().getLoader() == LoaderType.NEOFORGE) {
            // NeoForge emission: DeferredHolder<BlockEntityType<?>, BlockEntityType<T>>
            String blockRefs = String.join(", ", beDef.getValidBlockReferences().stream().map(b -> b + ".get()").toList());
            sb.append("DeferredHolder<BlockEntityType<?>, BlockEntityType<").append(entityClass).append(">> ")
                    .append(beDef.getFieldName())
                    .append(" = ").append(beDef.getRegistryFieldName())
                    .append(".register(\"").append(beDef.getRegistrationId()).append("\", () -> BlockEntityType.Builder.of(")
                    .append(entityClass).append("::new, ").append(blockRefs).append(").build(null));\n");
        } else {
            // Forge emission: RegistryObject<BlockEntityType<T>>
            String blockRefs = String.join(", ", beDef.getValidBlockReferences().stream().map(b -> b + ".get()").toList());
            sb.append("RegistryObject<BlockEntityType<").append(entityClass).append(">> ")
                    .append(beDef.getFieldName())
                    .append(" = ").append(beDef.getRegistryFieldName())
                    .append(".register(\"").append(beDef.getRegistrationId()).append("\", () -> BlockEntityType.Builder.of(")
                    .append(entityClass).append("::new, ").append(blockRefs).append(").build(null));\n");
        }
    }

    private String emitBlockProperties(CirBlockProperties props, TargetProfile profile) {
        StringBuilder sb = new StringBuilder();
        if (profile.getBlockPropertiesCapability() == BlockPropertiesCapability.PROPERTIES_WITH_MATERIAL) {
            String mat = props.getMaterial() != null ? "Material." + props.getMaterial() : "Material.STONE";
            sb.append("BlockBehaviour.Properties.of(").append(mat).append(")");
        } else {
            sb.append("BlockBehaviour.Properties.of()");
        }

        if (props.getDestroyTime() != null) {
            if (props.getExplosionResistance() != null && !props.getExplosionResistance().equals(props.getDestroyTime())) {
                sb.append(".strength(").append(props.getDestroyTime()).append("F, ").append(props.getExplosionResistance()).append("F)");
            } else {
                sb.append(".strength(").append(props.getDestroyTime()).append("F)");
            }
        }

        if (Boolean.TRUE.equals(props.getRequiresCorrectToolForDrops())) {
            sb.append(".requiresCorrectToolForDrops()");
        }
        if (Boolean.TRUE.equals(props.getNoOcclusion())) {
            sb.append(".noOcclusion()");
        }
        if (props.getSoundType() != null) {
            sb.append(".sound(SoundType.").append(props.getSoundType()).append(")");
        }

        for (String extra : props.getAdditionalChainedCalls()) {
            sb.append(".").append(extra);
        }

        return sb.toString();
    }

    private String emitItemProperties(CirItemProperties props, TargetProfile profile) {
        StringBuilder sb = new StringBuilder("new Item.Properties()");
        if (props.getCreativeTab() != null) {
            // On modern 1.20+, tabs are handled by CreativeModeTabEvent / deferred holders, or preserved if valid
            sb.append(".tab(").append(props.getCreativeTab()).append(")");
        }
        if (props.getMaxStackSize() != null) {
            sb.append(".stacksTo(").append(props.getMaxStackSize()).append(")");
        }
        if (props.getMaxDamage() != null) {
            sb.append(".durability(").append(props.getMaxDamage()).append(")");
        }
        for (String extra : props.getAdditionalChainedCalls()) {
            sb.append(".").append(extra);
        }
        return sb.toString();
    }

    private void emitEntityDefinition(CirEntityDefinition entDef, TargetProfile profile, StringBuilder sb) {
        String entityClass = entDef.getEntityClass();
        String cat = entDef.getMobCategory() != null ? entDef.getMobCategory() : "MISC";
        sb.append("    public static final ");

        StringBuilder builderCall = new StringBuilder();
        builderCall.append("EntityType.Builder.<").append(entityClass).append(">of(")
                .append(entityClass).append("::new, MobCategory.").append(cat).append(")");
        if (entDef.getWidth() != null && entDef.getHeight() != null) {
            builderCall.append(".sized(").append(entDef.getWidth()).append("F, ").append(entDef.getHeight()).append("F)");
        }
        if (entDef.getClientTrackingRange() != null) {
            builderCall.append(".clientTrackingRange(").append(entDef.getClientTrackingRange()).append(")");
        }
        if (entDef.getUpdateInterval() != null) {
            builderCall.append(".updateInterval(").append(entDef.getUpdateInterval()).append(")");
        }
        builderCall.append(".build(\"").append(entDef.getRegistrationId()).append("\")");

        if (profile.getTargetSpec().getLoader() == LoaderType.PAPER || profile.getTargetSpec().getLoader() == LoaderType.SPIGOT) {
            sb.append("NamespacedKey ").append(entDef.getFieldName())
                    .append("_KEY = new NamespacedKey(MOD_ID, \"").append(entDef.getRegistrationId()).append("\");\n");
        } else if (profile.getTargetSpec().getLoader() == LoaderType.FABRIC || profile.getTargetSpec().getLoader() == LoaderType.QUILT) {
            sb.append("EntityType<").append(entityClass).append("> ").append(entDef.getFieldName())
                    .append(" = Registry.register(").append(fabricReg("ENTITY_TYPE", profile)).append(", new ResourceLocation(MOD_ID, \"")
                    .append(entDef.getRegistrationId()).append("\"), ")
                    .append(builderCall).append(");\n");
        } else if (profile.getTargetSpec().getLoader() == LoaderType.NEOFORGE) {
            sb.append("DeferredHolder<EntityType<?>, EntityType<").append(entityClass).append(">> ")
                    .append(entDef.getFieldName())
                    .append(" = ").append(entDef.getRegistryFieldName())
                    .append(".register(\"").append(entDef.getRegistrationId()).append("\", () -> ")
                    .append(builderCall).append(");\n");
        } else {
            sb.append("RegistryObject<EntityType<").append(entityClass).append(">> ")
                    .append(entDef.getFieldName())
                    .append(" = ").append(entDef.getRegistryFieldName())
                    .append(".register(\"").append(entDef.getRegistrationId()).append("\", () -> ")
                    .append(builderCall).append(");\n");
        }
    }

    private void emitSoundDefinition(CirSoundDefinition sndDef, CirCompilationUnit cir, TargetProfile profile, StringBuilder sb) {
        sb.append("    public static final ");
        String regField = sndDef.getRegistryFieldName();
        Optional<CirRegistryDeclaration> regOpt = cir.findRegistryByField(regField);
        String modIdArg = regOpt.map(r -> (r.getRawModIdExpression() != null && !r.getRawModIdExpression().isBlank())
                ? r.getRawModIdExpression()
                : ("\"" + r.getModId() + "\"")).orElse("\"modid\"");

        if (profile.getTargetSpec().getLoader() == LoaderType.PAPER || profile.getTargetSpec().getLoader() == LoaderType.SPIGOT) {
            sb.append("NamespacedKey ").append(sndDef.getFieldName())
                    .append("_KEY = new NamespacedKey(MOD_ID, \"").append(sndDef.getRegistrationId()).append("\");\n");
        } else if (profile.getTargetSpec().getLoader() == LoaderType.FABRIC || profile.getTargetSpec().getLoader() == LoaderType.QUILT) {
            sb.append("SoundEvent ").append(sndDef.getFieldName())
                    .append(" = Registry.register(").append(fabricReg("SOUND_EVENT", profile)).append(", new ResourceLocation(MOD_ID, \"")
                    .append(sndDef.getRegistrationId()).append("\"), SoundEvent.createVariableRangeEvent(new ResourceLocation(MOD_ID, \"")
                    .append(sndDef.getSoundResourcePath()).append("\")));\n");
        } else if (profile.getTargetSpec().getLoader() == LoaderType.NEOFORGE) {
            String rlExpr = (profile.getIdentifierCapability() == IdentifierCapability.IDENTIFIER_FACTORY)
                    ? "Identifier.fromNamespaceAndPath(" + modIdArg + ", \"" + sndDef.getSoundResourcePath() + "\")"
                    : "ResourceLocation.fromNamespaceAndPath(" + modIdArg + ", \"" + sndDef.getSoundResourcePath() + "\")";
            sb.append("DeferredHolder<SoundEvent, SoundEvent> ").append(sndDef.getFieldName())
                    .append(" = ").append(sndDef.getRegistryFieldName())
                    .append(".register(\"").append(sndDef.getRegistrationId()).append("\", () -> ")
                    .append("SoundEvent.createVariableRangeEvent(").append(rlExpr).append("));\n");
        } else {
            sb.append("RegistryObject<SoundEvent> ").append(sndDef.getFieldName())
                    .append(" = ").append(sndDef.getRegistryFieldName())
                    .append(".register(\"").append(sndDef.getRegistrationId()).append("\", () -> ")
                    .append("new SoundEvent(new ResourceLocation(").append(modIdArg).append(", \"").append(sndDef.getSoundResourcePath()).append("\")));\n");
        }
    }

    private void emitParticleDefinition(CirParticleDefinition partDef, TargetProfile profile, StringBuilder sb) {
        String pClass = partDef.getImplementationType() != null ? partDef.getImplementationType() : "SimpleParticleType";
        sb.append("    public static final ");
        if (profile.getTargetSpec().getLoader() == LoaderType.PAPER || profile.getTargetSpec().getLoader() == LoaderType.SPIGOT) {
            sb.append("NamespacedKey ").append(partDef.getFieldName())
                    .append("_KEY = new NamespacedKey(MOD_ID, \"").append(partDef.getRegistrationId()).append("\");\n");
        } else if (profile.getTargetSpec().getLoader() == LoaderType.FABRIC || profile.getTargetSpec().getLoader() == LoaderType.QUILT) {
            sb.append("SimpleParticleType ").append(partDef.getFieldName())
                    .append(" = Registry.register(").append(fabricReg("PARTICLE_TYPE", profile)).append(", new ResourceLocation(MOD_ID, \"")
                    .append(partDef.getRegistrationId()).append("\"), FabricParticleTypes.simple(")
                    .append(partDef.isAlwaysShow()).append("));\n");
        } else if (profile.getTargetSpec().getLoader() == LoaderType.NEOFORGE) {
            sb.append("DeferredHolder<ParticleType<?>, ").append(pClass).append("> ").append(partDef.getFieldName())
                    .append(" = ").append(partDef.getRegistryFieldName())
                    .append(".register(\"").append(partDef.getRegistrationId()).append("\", () -> ")
                    .append("new ").append(pClass).append("(").append(partDef.isAlwaysShow()).append("));\n");
        } else {
            sb.append("RegistryObject<").append(pClass).append("> ").append(partDef.getFieldName())
                    .append(" = ").append(partDef.getRegistryFieldName())
                    .append(".register(\"").append(partDef.getRegistrationId()).append("\", () -> ")
                    .append("new ").append(pClass).append("(").append(partDef.isAlwaysShow()).append("));\n");
        }
    }

    private void emitMenuDefinition(CirMenuDefinition menuDef, TargetProfile profile, StringBuilder sb) {
        String menuClass = menuDef.getMenuClass();
        sb.append("    public static final ");
        if (profile.getTargetSpec().getLoader() == LoaderType.PAPER || profile.getTargetSpec().getLoader() == LoaderType.SPIGOT) {
            sb.append("NamespacedKey ").append(menuDef.getFieldName())
                    .append("_KEY = new NamespacedKey(MOD_ID, \"").append(menuDef.getRegistrationId()).append("\");\n");
        } else if (profile.getTargetSpec().getLoader() == LoaderType.FABRIC || profile.getTargetSpec().getLoader() == LoaderType.QUILT) {
            sb.append("MenuType<").append(menuClass).append("> ").append(menuDef.getFieldName())
                    .append(" = Registry.register(").append(fabricReg("MENU", profile)).append(", new ResourceLocation(MOD_ID, \"")
                    .append(menuDef.getRegistrationId()).append("\"), new ExtendedScreenHandlerType<>(")
                    .append(menuClass).append("::new));\n");
        } else if (profile.getTargetSpec().getLoader() == LoaderType.NEOFORGE) {
            sb.append("DeferredHolder<MenuType<?>, MenuType<").append(menuClass).append(">> ").append(menuDef.getFieldName())
                    .append(" = ").append(menuDef.getRegistryFieldName())
                    .append(".register(\"").append(menuDef.getRegistrationId()).append("\", () -> ")
                    .append("IMenuTypeExtension.create(").append(menuClass).append("::new));\n");
        } else {
            sb.append("RegistryObject<MenuType<").append(menuClass).append(">> ").append(menuDef.getFieldName())
                    .append(" = ").append(menuDef.getRegistryFieldName())
                    .append(".register(\"").append(menuDef.getRegistrationId()).append("\", () -> ")
                    .append("IForgeMenuType.create(").append(menuClass).append("::new));\n");
        }
    }

    private void emitFluidDefinition(CirFluidDefinition fluidDef, TargetProfile profile, StringBuilder sb) {
        String fClass = fluidDef.getImplementationType();
        sb.append("    public static final ");
        if (profile.getTargetSpec().getLoader() == LoaderType.PAPER || profile.getTargetSpec().getLoader() == LoaderType.SPIGOT) {
            sb.append("NamespacedKey ").append(fluidDef.getFieldName())
                    .append("_KEY = new NamespacedKey(MOD_ID, \"").append(fluidDef.getRegistrationId()).append("\");\n");
        } else if (profile.getTargetSpec().getLoader() == LoaderType.FABRIC || profile.getTargetSpec().getLoader() == LoaderType.QUILT) {
            sb.append(fClass).append(" ").append(fluidDef.getFieldName())
                    .append(" = Registry.register(").append(fabricReg("FLUID", profile)).append(", new ResourceLocation(MOD_ID, \"")
                    .append(fluidDef.getRegistrationId()).append("\"), new ")
                    .append(fClass).append("(PROPERTIES));\n");
        } else if (profile.getTargetSpec().getLoader() == LoaderType.NEOFORGE) {
            sb.append("DeferredHolder<Fluid, ").append(fClass).append("> ").append(fluidDef.getFieldName())
                    .append(" = ").append(fluidDef.getRegistryFieldName())
                    .append(".register(\"").append(fluidDef.getRegistrationId()).append("\", () -> ")
                    .append("new ").append(fClass).append("(PROPERTIES));\n");
        } else {
            sb.append("RegistryObject<").append(fClass).append("> ").append(fluidDef.getFieldName())
                    .append(" = ").append(fluidDef.getRegistryFieldName())
                    .append(".register(\"").append(fluidDef.getRegistrationId()).append("\", () -> ")
                    .append("new ").append(fClass).append("(PROPERTIES));\n");
        }
    }

    private void emitRecipeSerializerDefinition(CirRecipeSerializerDefinition recDef, TargetProfile profile, StringBuilder sb) {
        sb.append("    public static final ");
        String rClass = recDef.getRecipeClass();
        if (recDef.isSimple()) {
            if (profile.getTargetSpec().getLoader() == LoaderType.PAPER || profile.getTargetSpec().getLoader() == LoaderType.SPIGOT) {
                sb.append("NamespacedKey ").append(recDef.getFieldName())
                        .append("_KEY = new NamespacedKey(MOD_ID, \"").append(recDef.getRegistrationId()).append("\");\n");
            } else if (profile.getTargetSpec().getLoader() == LoaderType.FABRIC || profile.getTargetSpec().getLoader() == LoaderType.QUILT) {
                sb.append("SimpleRecipeSerializer<").append(rClass).append("> ").append(recDef.getFieldName())
                        .append(" = Registry.register(").append(fabricReg("RECIPE_SERIALIZER", profile)).append(", new ResourceLocation(MOD_ID, \"")
                        .append(recDef.getRegistrationId()).append("\"), new SimpleRecipeSerializer<>(")
                        .append(rClass).append("::new));\n");
            } else if (profile.getTargetSpec().getLoader() == LoaderType.NEOFORGE) {
                sb.append("DeferredHolder<RecipeSerializer<?>, SimpleRecipeSerializer<").append(rClass).append(">> ").append(recDef.getFieldName())
                        .append(" = ").append(recDef.getRegistryFieldName())
                        .append(".register(\"").append(recDef.getRegistrationId()).append("\", () -> ")
                        .append("new SimpleRecipeSerializer<>(").append(rClass).append("::new));\n");
            } else {
                sb.append("RegistryObject<SimpleRecipeSerializer<").append(rClass).append(">> ").append(recDef.getFieldName())
                        .append(" = ").append(recDef.getRegistryFieldName())
                        .append(".register(\"").append(recDef.getRegistrationId()).append("\", () -> ")
                        .append("new SimpleRecipeSerializer<>(").append(rClass).append("::new));\n");
            }
        } else {
            if (profile.getTargetSpec().getLoader() == LoaderType.PAPER || profile.getTargetSpec().getLoader() == LoaderType.SPIGOT) {
                sb.append("NamespacedKey ").append(recDef.getFieldName())
                        .append("_KEY = new NamespacedKey(MOD_ID, \"").append(recDef.getRegistrationId()).append("\");\n");
            } else if (profile.getTargetSpec().getLoader() == LoaderType.FABRIC || profile.getTargetSpec().getLoader() == LoaderType.QUILT) {
                sb.append("RecipeSerializer<").append(rClass).append("> ").append(recDef.getFieldName())
                        .append(" = Registry.register(").append(fabricReg("RECIPE_SERIALIZER", profile)).append(", new ResourceLocation(MOD_ID, \"")
                        .append(recDef.getRegistrationId()).append("\"), ")
                        .append(recDef.getSerializerReference()).append(");\n");
            } else if (profile.getTargetSpec().getLoader() == LoaderType.NEOFORGE) {
                sb.append("DeferredHolder<RecipeSerializer<?>, RecipeSerializer<").append(rClass).append(">> ").append(recDef.getFieldName())
                        .append(" = ").append(recDef.getRegistryFieldName())
                        .append(".register(\"").append(recDef.getRegistrationId()).append("\", () -> ")
                        .append(recDef.getSerializerReference()).append(");\n");
            } else {
                sb.append("RegistryObject<RecipeSerializer<").append(rClass).append(">> ").append(recDef.getFieldName())
                        .append(" = ").append(recDef.getRegistryFieldName())
                        .append(".register(\"").append(recDef.getRegistrationId()).append("\", () -> ")
                        .append(recDef.getSerializerReference()).append(");\n");
            }
        }
    }

    private void emitAliasDefinition(CirAliasDefinition aliasDef, TargetProfile profile, StringBuilder sb) {
        sb.append("    public static final ");
        String impl = aliasDef.getImplementationType();
        if (profile.getTargetSpec().getLoader() == LoaderType.PAPER || profile.getTargetSpec().getLoader() == LoaderType.SPIGOT) {
            sb.append("NamespacedKey ").append(aliasDef.getFieldName())
                    .append("_KEY = ").append(aliasDef.getTargetFieldName()).append("_KEY;\n");
        } else if (profile.getTargetSpec().getLoader() == LoaderType.FABRIC || profile.getTargetSpec().getLoader() == LoaderType.QUILT) {
            sb.append(impl).append(" ")
                    .append(aliasDef.getFieldName()).append(" = ").append(aliasDef.getTargetFieldName()).append(";\n");
        } else if (profile.getTargetSpec().getLoader() == LoaderType.NEOFORGE) {
            sb.append("DeferredHolder<").append(impl).append(", ").append(impl).append("> ")
                    .append(aliasDef.getFieldName()).append(" = ").append(aliasDef.getTargetFieldName()).append(";\n");
        } else {
            sb.append("RegistryObject<").append(impl).append("> ")
                    .append(aliasDef.getFieldName()).append(" = ").append(aliasDef.getTargetFieldName()).append(";\n");
        }
    }

    private void emitCreativeTabDefinition(CirCreativeTabDefinition tabDef, TargetProfile profile, StringBuilder sb) {
        sb.append("    public static final ");

        String iconRef = tabDef.getIconItemExpression();
        boolean isEntryRef = !iconRef.contains("Items.") && !iconRef.endsWith(".get()");
        String refWithGet = isEntryRef ? (iconRef + ".get()") : iconRef;

        if (profile.getTargetSpec().getLoader() == LoaderType.PAPER || profile.getTargetSpec().getLoader() == LoaderType.SPIGOT) {
            sb.append("NamespacedKey ").append(tabDef.getFieldName())
                    .append("_KEY = new NamespacedKey(MOD_ID, \"").append(tabDef.getRegistrationId()).append("\");\n");
        } else if (profile.getTargetSpec().getLoader() == LoaderType.FABRIC || profile.getTargetSpec().getLoader() == LoaderType.QUILT) {
            sb.append("CreativeModeTab ").append(tabDef.getFieldName())
                    .append(" = Registry.register(").append(fabricReg("CREATIVE_MODE_TAB", profile)).append(", new ResourceLocation(MOD_ID, \"")
                    .append(tabDef.getRegistrationId()).append("\"), FabricItemGroup.builder()")
                    .append(".icon(() -> new ItemStack(").append(iconRef).append("))")
                    .append(".title(Component.translatable(\"").append(tabDef.getTitleKey()).append("\"))")
                    .append(".build());\n");
        } else if (profile.getTargetSpec().getLoader() == LoaderType.NEOFORGE) {
            sb.append("DeferredHolder<CreativeModeTab, CreativeModeTab> ").append(tabDef.getFieldName())
                    .append(" = ").append(tabDef.getRegistryFieldName())
                    .append(".register(\"").append(tabDef.getRegistrationId()).append("\", () -> CreativeModeTab.builder()")
                    .append(".icon(() -> new ItemStack(").append(refWithGet).append("))")
                    .append(".title(Component.translatable(\"").append(tabDef.getTitleKey()).append("\"))")
                    .append(".build());\n");
        } else {
            sb.append("CreativeModeTab ").append(tabDef.getFieldName())
                    .append(" = new CreativeModeTab(MOD_ID) {\n")
                    .append("        @Override\n")
                    .append("        public ItemStack makeIcon() {\n")
                    .append("            return new ItemStack(").append(refWithGet).append(");\n")
                    .append("        }\n")
                    .append("    };\n");
        }
    }

    private String fabricReg(String member, TargetProfile profile) {
        return (profile.usesBuiltInRegistries() ? "BuiltInRegistries." : "Registry.") + member;
    }
}

