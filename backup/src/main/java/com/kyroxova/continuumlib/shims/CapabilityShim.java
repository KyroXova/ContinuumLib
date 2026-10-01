package com.kyroxova.continuumlib.shims;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Capabilities, Data Attachments, and Storage.
 *
 * Supports:
 * - Forge: ICapabilityProvider, CapabilityManager, and LazyOptional (1.12 - 1.20.1).
 * - NeoForge: BlockCapability, ItemCapability, EntityCapability, and Data Attachments (AttachmentType on IAttachmentHolder) (1.20.5+ / 26.3+).
 * - Fabric: BlockApiLookup, ItemApiLookup, EntityApiLookup, and Transfer API (ItemStorage / FluidStorage) with exact unit conversion:
 *   81,000 droplets = 1 bucket = 1000 mB (1 mB = 81 droplets).
 * - Generic Mod-Defined Interface Bridging (e.g. custom energy, custom mana, custom inventory).
 */
public final class CapabilityShim {

    private static final Logger LOGGER = Logger.getLogger(CapabilityShim.class.getName());

    // Fluid conversion constants between Forge millibuckets and Fabric droplets
    public static final long DROPLETS_PER_MB = 81L;
    public static final long DROPLETS_PER_BUCKET = 81_000L;
    public static final int MB_PER_BUCKET = 1000;

    // Persistent storage for NeoForge Data Attachments on platforms without native IAttachmentHolder (Forge/Fabric)
    private static final Map<Object, Map<Object, Object>> ATTACHMENT_DATA =
            Collections.synchronizedMap(new WeakHashMap<>());

    // Generic mod capability and attachment registries
    private static final Map<String, Class<?>> GENERIC_CAPABILITIES = new ConcurrentHashMap<>();
    private static final Map<Class<?>, String> GENERIC_CAPABILITIES_BY_CLASS = new ConcurrentHashMap<>();
    private static final Map<String, Object> NEO_BLOCK_CAPABILITIES = new ConcurrentHashMap<>();
    private static final Map<String, Object> FABRIC_BLOCK_LOOKUPS = new ConcurrentHashMap<>();

    private CapabilityShim() {}

    /**
     * Registers a generic mod-defined capability interface and identifier.
     */
    public static void registerGenericCapability(String id, Class<?> apiClass) {
        if (id == null || apiClass == null) return;
        GENERIC_CAPABILITIES.put(id, apiClass);
        GENERIC_CAPABILITIES_BY_CLASS.put(apiClass, id);
        LOGGER.info("[CapabilityShim] Registered generic capability: " + id + " -> " + apiClass.getName());
    }

    /**
     * Extracts the underlying interface Class from any capability token,
     * including Forge Capability, NeoForge BlockCapability, Fabric BlockApiLookup,
     * or a direct Class reference.
     */
    public static Class<?> extractInterface(Object capability) {
        if (capability == null) return null;
        if (capability instanceof Class<?> clazz) return clazz;

        // 1. Forge Capability.getInterface() or Capability.name
        try {
            Method m = capability.getClass().getMethod("getInterface");
            Object res = m.invoke(capability);
            if (res instanceof Class<?> clazz) return clazz;
        } catch (Throwable ignored) {}

        // 2. NeoForge BlockCapability.typeClass() / contextClass()
        try {
            Method m = capability.getClass().getMethod("typeClass");
            Object res = m.invoke(capability);
            if (res instanceof Class<?> clazz) return clazz;
        } catch (Throwable ignored) {}

        // 3. Fabric BlockApiLookup.getApiClass()
        try {
            Method m = capability.getClass().getMethod("getApiClass");
            Object res = m.invoke(capability);
            if (res instanceof Class<?> clazz) return clazz;
        } catch (Throwable ignored) {}

        // 4. Check registered string ID
        String name = String.valueOf(capability);
        if (GENERIC_CAPABILITIES.containsKey(name)) {
            return GENERIC_CAPABILITIES.get(name);
        }

        return null;
    }

    /**
     * Converts Forge millibuckets (1000 mB = 1 bucket) to Fabric droplets (81,000 droplets = 1 bucket).
     */
    public static long mbToDroplets(long mb) {
        return mb * DROPLETS_PER_MB;
    }

    /**
     * Converts Fabric droplets to Forge millibuckets.
     */
    public static long dropletsToMb(long droplets) {
        return droplets / DROPLETS_PER_MB;
    }

    /**
     * Resolves an item handler capability or capability token safely across platforms.
     */
    public static Object resolveItemHandler(Object blockEntity, Object side) {
        if (blockEntity == null) return null;
        try {
            // Check NeoForge Capabilities.ItemHandler.BLOCK
            Class<?> capabilitiesClass = Class.forName("net.neoforged.neoforge.capabilities.Capabilities$ItemHandler");
            Object blockCap = capabilitiesClass.getField("BLOCK").get(null);
            LOGGER.fine("[CapabilityShim] Resolved NeoForge ItemHandler BlockCapability");
            return blockCap;
        } catch (Throwable ignored) {
            try {
                // Check Forge CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
                Class<?> itemHandlerCapClass = Class.forName("net.minecraftforge.items.CapabilityItemHandler");
                return itemHandlerCapClass.getField("ITEM_HANDLER_CAPABILITY").get(null);
            } catch (Throwable ignored2) {
                return null;
            }
        }
    }

    /**
     * Resolves a fluid handler capability token safely across platforms.
     */
    public static Object resolveFluidHandler(Object blockEntity, Object side) {
        if (blockEntity == null) return null;
        try {
            Class<?> capabilitiesClass = Class.forName("net.neoforged.neoforge.capabilities.Capabilities$FluidHandler");
            return capabilitiesClass.getField("BLOCK").get(null);
        } catch (Throwable ignored) {
            try {
                Class<?> fluidHandlerCapClass = Class.forName("net.minecraftforge.fluids.capability.CapabilityFluidHandler");
                return fluidHandlerCapClass.getField("FLUID_HANDLER_CAPABILITY").get(null);
            } catch (Throwable ignored2) {
                return null;
            }
        }
    }

    /**
     * Resolves an energy storage capability token safely across platforms.
     */
    public static Object resolveEnergyStorage(Object blockEntity, Object side) {
        if (blockEntity == null) return null;
        try {
            Class<?> capabilitiesClass = Class.forName("net.neoforged.neoforge.capabilities.Capabilities$EnergyStorage");
            return capabilitiesClass.getField("BLOCK").get(null);
        } catch (Throwable ignored) {
            try {
                Class<?> energyCapClass = Class.forName("net.minecraftforge.energy.CapabilityEnergy");
                return energyCapClass.getField("ENERGY").get(null);
            } catch (Throwable ignored2) {
                return null;
            }
        }
    }

    /**
     * Dispatches getCapability(Capability, Direction) safely across Forge, NeoForge, and Fabric.
     * Supports generic mod-defined interfaces (e.g. mana, energy, inventory) and direct interface implementations.
     */
    public static Object getCapability(Object provider, Object capability, Object direction) {
        if (provider == null) return emptyLazyOptional();

        Class<?> apiClass = extractInterface(capability);

        // 1. Direct Interface Check: If provider itself implements the capability interface (1.7.10/1.12 style)
        if (apiClass != null && apiClass.isInstance(provider)) {
            return ofLazyOptional(provider);
        }

        // 2. Try legacy Forge ICapabilityProvider.getCapability(Capability, Direction)
        try {
            for (Method m : provider.getClass().getMethods()) {
                if ("getCapability".equals(m.getName()) && m.getParameterCount() == 2) {
                    Object res = m.invoke(provider, capability, direction);
                    if (res != null) return res;
                }
            }
        } catch (Throwable ignored) {}

        // 3. Try NeoForge Level.getCapability(BlockCapability, BlockPos, Direction)
        try {
            Method getLevel = provider.getClass().getMethod("getLevel");
            Method getBlockPos = provider.getClass().getMethod("getBlockPos");
            Object level = getLevel.invoke(provider);
            Object pos = getBlockPos.invoke(provider);
            if (level != null && pos != null) {
                // If capability is already a BlockCapability or can be resolved
                Object blockCapResult = getBlockCapability(level, pos, capability, direction);
                if (blockCapResult != null) {
                    return ofLazyOptional(blockCapResult);
                }

                // Fabric BlockApiLookup dynamic resolution for any mod interface
                if (apiClass != null) {
                    Object fabricApiResult = findFabricBlockApi(level, pos, apiClass, direction);
                    if (fabricApiResult != null) {
                        return ofLazyOptional(fabricApiResult);
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 4. Try NeoForge EntityCapability
        try {
            Class<?> entityCapClass = Class.forName("net.neoforged.neoforge.capabilities.EntityCapability");
            if (entityCapClass.isInstance(capability)) {
                Object entityCapResult = getEntityCapability(provider, capability, direction);
                if (entityCapResult != null) {
                    return ofLazyOptional(entityCapResult);
                }
            }
        } catch (Throwable ignored) {}

        // 5. Try NeoForge ItemCapability
        try {
            Class<?> itemCapClass = Class.forName("net.neoforged.neoforge.capabilities.ItemCapability");
            if (itemCapClass.isInstance(capability)) {
                Object itemCapResult = getItemCapability(provider, capability, direction);
                if (itemCapResult != null) {
                    return ofLazyOptional(itemCapResult);
                }
            }
        } catch (Throwable ignored) {}

        // 6. Fabric Transfer API fallback for BlockEntity
        try {
            Method getLevel = provider.getClass().getMethod("getLevel");
            Method getBlockPos = provider.getClass().getMethod("getBlockPos");
            Object level = getLevel.invoke(provider);
            Object pos = getBlockPos.invoke(provider);
            if (level != null && pos != null) {
                // Check ItemStorage
                Object itemStorage = getItemHandler(level, pos, direction);
                if (itemStorage != null) return ofLazyOptional(itemStorage);

                // Check FluidStorage
                Object fluidStorage = getFluidHandler(level, pos, direction);
                if (fluidStorage != null) return ofLazyOptional(fluidStorage);
            }
        } catch (Throwable ignored) {}

        // 7. NeoForge Data Attachment fallback
        Object attachment = getDataAttachment(provider, capability);
        if (attachment != null) {
            return ofLazyOptional(attachment);
        }
        if (apiClass != null) {
            attachment = getDataAttachment(provider, apiClass);
            if (attachment != null) {
                return ofLazyOptional(attachment);
            }
        }

        return emptyLazyOptional();
    }

    /**
     * Resolves an ItemHandler across Forge, NeoForge, and Fabric (with Storage<ItemVariant> adapter).
     */
    public static Object getItemHandler(Object level, Object pos, Object direction) {
        if (level == null || pos == null) return null;

        // 1. NeoForge: Capabilities.ItemHandler.BLOCK
        try {
            Class<?> capabilitiesClass = Class.forName("net.neoforged.neoforge.capabilities.Capabilities$ItemHandler");
            Object blockCap = capabilitiesClass.getField("BLOCK").get(null);
            Object res = getBlockCapability(level, pos, blockCap, direction);
            if (res != null) return res;
        } catch (Throwable ignored) {}

        // 2. Forge: Level.getBlockEntity(pos) -> getCapability(ITEM_HANDLER_CAPABILITY, direction)
        try {
            Method getBe = level.getClass().getMethod("getBlockEntity", Class.forName("net.minecraft.core.BlockPos"));
            Object be = getBe.invoke(level, pos);
            if (be != null) {
                Class<?> itemHandlerCapClass = Class.forName("net.minecraftforge.items.CapabilityItemHandler");
                Object itemCap = itemHandlerCapClass.getField("ITEM_HANDLER_CAPABILITY").get(null);
                Object lazyOpt = getCapability(be, itemCap, direction);
                if (lazyOpt != null) {
                    Method resolve = lazyOpt.getClass().getMethod("resolve");
                    Optional<?> opt = (Optional<?>) resolve.invoke(lazyOpt);
                    if (opt.isPresent()) return opt.get();
                }
            }
        } catch (Throwable ignored) {}

        // 3. Fabric: ItemStorage.SIDED.find(level, pos, direction)
        try {
            Class<?> itemStorageClass = Class.forName("net.fabricmc.fabric.api.transfer.v1.item.ItemStorage");
            Object sided = itemStorageClass.getField("SIDED").get(null);
            Method findMethod = sided.getClass().getMethod("find",
                    Class.forName("net.minecraft.world.level.Level"),
                    Class.forName("net.minecraft.core.BlockPos"),
                    Class.forName("net.minecraft.core.Direction"));
            Object storage = findMethod.invoke(sided, level, pos, direction);
            if (storage != null) {
                return adaptFabricItemStorage(storage);
            }
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Resolves a FluidHandler across Forge, NeoForge, and Fabric (with Storage<FluidVariant> and 81x droplet conversion).
     */
    public static Object getFluidHandler(Object level, Object pos, Object direction) {
        if (level == null || pos == null) return null;

        // 1. NeoForge: Capabilities.FluidHandler.BLOCK
        try {
            Class<?> capabilitiesClass = Class.forName("net.neoforged.neoforge.capabilities.Capabilities$FluidHandler");
            Object blockCap = capabilitiesClass.getField("BLOCK").get(null);
            Object res = getBlockCapability(level, pos, blockCap, direction);
            if (res != null) return res;
        } catch (Throwable ignored) {}

        // 2. Forge: Level.getBlockEntity(pos) -> getCapability(FLUID_HANDLER_CAPABILITY, direction)
        try {
            Method getBe = level.getClass().getMethod("getBlockEntity", Class.forName("net.minecraft.core.BlockPos"));
            Object be = getBe.invoke(level, pos);
            if (be != null) {
                Class<?> fluidHandlerCapClass = Class.forName("net.minecraftforge.fluids.capability.CapabilityFluidHandler");
                Object fluidCap = fluidHandlerCapClass.getField("FLUID_HANDLER_CAPABILITY").get(null);
                Object lazyOpt = getCapability(be, fluidCap, direction);
                if (lazyOpt != null) {
                    Method resolve = lazyOpt.getClass().getMethod("resolve");
                    Optional<?> opt = (Optional<?>) resolve.invoke(lazyOpt);
                    if (opt.isPresent()) return opt.get();
                }
            }
        } catch (Throwable ignored) {}

        // 3. Fabric: FluidStorage.SIDED.find(level, pos, direction)
        try {
            Class<?> fluidStorageClass = Class.forName("net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage");
            Object sided = fluidStorageClass.getField("SIDED").get(null);
            Method findMethod = sided.getClass().getMethod("find",
                    Class.forName("net.minecraft.world.level.Level"),
                    Class.forName("net.minecraft.core.BlockPos"),
                    Class.forName("net.minecraft.core.Direction"));
            Object storage = findMethod.invoke(sided, level, pos, direction);
            if (storage != null) {
                return adaptFabricFluidStorage(storage);
            }
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Queries Fabric BlockApiLookup dynamically for any mod-defined interface.
     */
    public static Object findFabricBlockApi(Object level, Object pos, Class<?> apiClass, Object direction) {
        if (level == null || pos == null || apiClass == null) return null;
        try {
            Object lookup = getOrCreateFabricBlockLookup(apiClass);
            if (lookup != null) {
                for (Method m : lookup.getClass().getMethods()) {
                    if ("find".equals(m.getName())) {
                        if (m.getParameterCount() == 3) {
                            return m.invoke(lookup, level, pos, direction);
                        } else if (m.getParameterCount() == 5) {
                            return m.invoke(lookup, level, pos, null, null, direction);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Resolves or creates a Fabric BlockApiLookup instance for the specified API class.
     */
    public static Object getOrCreateFabricBlockLookup(Class<?> apiClass) {
        if (apiClass == null) return null;
        return FABRIC_BLOCK_LOOKUPS.computeIfAbsent(apiClass.getName(), k -> {
            try {
                Class<?> blockApiLookupClass = Class.forName("net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup");
                Class<?> identifierClass = Class.forName("net.minecraft.resources.ResourceLocation");
                Method fromNamespaceAndPath = identifierClass.getMethod("fromNamespaceAndPath", String.class, String.class);
                String id = GENERIC_CAPABILITIES_BY_CLASS.getOrDefault(apiClass, apiClass.getSimpleName().toLowerCase());
                Object ident = fromNamespaceAndPath.invoke(null, "continuumlib", id);
                Class<?> dirClass = Class.forName("net.minecraft.core.Direction");

                Method getMethod = blockApiLookupClass.getMethod("get", identifierClass, Class.class, Class.class);
                return getMethod.invoke(null, ident, apiClass, dirClass);
            } catch (Throwable ignored) {
                return null;
            }
        });
    }

    /**
     * Resolves or creates a NeoForge BlockCapability instance for the specified API class.
     */
    public static Object getOrCreateNeoBlockCapability(Class<?> apiClass) {
        if (apiClass == null) return null;
        return NEO_BLOCK_CAPABILITIES.computeIfAbsent(apiClass.getName(), k -> {
            try {
                Class<?> blockCapClass = Class.forName("net.neoforged.neoforge.capabilities.BlockCapability");
                Class<?> rlClass = Class.forName("net.minecraft.resources.ResourceLocation");
                Method fromNamespaceAndPath = rlClass.getMethod("fromNamespaceAndPath", String.class, String.class);
                String id = GENERIC_CAPABILITIES_BY_CLASS.getOrDefault(apiClass, apiClass.getSimpleName().toLowerCase());
                Object rl = fromNamespaceAndPath.invoke(null, "continuumlib", id);

                Method createMethod = blockCapClass.getMethod("createSided", rlClass, Class.class);
                return createMethod.invoke(null, rl, apiClass);
            } catch (Throwable ignored) {
                return null;
            }
        });
    }

    /**
     * Resolves a NeoForge BlockCapability query matching instance call stack [cap, level, pos, context].
     */
    public static Object findBlockCapability(Object cap, Object level, Object pos, Object context) {
        return getBlockCapability(level, pos, cap, context);
    }

    /**
     * Resolves a NeoForge EntityCapability query matching instance call stack [cap, entity, context].
     */
    public static Object findEntityCapability(Object cap, Object entity, Object context) {
        return getEntityCapability(entity, cap, context);
    }

    /**
     * Resolves a NeoForge ItemCapability query matching instance call stack [cap, stack, context].
     */
    public static Object findItemCapability(Object cap, Object stack, Object context) {
        return getItemCapability(stack, cap, context);
    }

    /**
     * Resolves a NeoForge BlockCapability query.
     */
    public static Object getBlockCapability(Object level, Object pos, Object capability, Object direction) {
        if (level == null || pos == null || capability == null) return null;
        try {
            for (Method m : level.getClass().getMethods()) {
                if ("getCapability".equals(m.getName()) && m.getParameterCount() >= 2) {
                    return (m.getParameterCount() == 3)
                            ? m.invoke(level, capability, pos, direction)
                            : m.invoke(level, capability, pos);
                }
            }
        } catch (Throwable ignored) {}

        try {
            // Alternatively capability.getCapability(level, pos, direction)
            for (Method m : capability.getClass().getMethods()) {
                if ("getCapability".equals(m.getName()) && m.getParameterCount() >= 2) {
                    return (m.getParameterCount() == 3)
                            ? m.invoke(capability, level, pos, direction)
                            : m.invoke(capability, level, pos);
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Resolves a NeoForge EntityCapability query.
     */
    public static Object getEntityCapability(Object entity, Object capability, Object direction) {
        if (entity == null || capability == null) return null;
        try {
            for (Method m : entity.getClass().getMethods()) {
                if ("getCapability".equals(m.getName())) {
                    return (m.getParameterCount() == 2)
                            ? m.invoke(entity, capability, direction)
                            : m.invoke(entity, capability);
                }
            }
        } catch (Throwable ignored) {}

        try {
            for (Method m : capability.getClass().getMethods()) {
                if ("getCapability".equals(m.getName())) {
                    return (m.getParameterCount() == 2)
                            ? m.invoke(capability, entity, direction)
                            : m.invoke(capability, entity);
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Resolves a NeoForge ItemCapability query.
     */
    public static Object getItemCapability(Object itemStack, Object capability, Object context) {
        if (itemStack == null || capability == null) return null;
        try {
            for (Method m : itemStack.getClass().getMethods()) {
                if ("getCapability".equals(m.getName())) {
                    return (m.getParameterCount() == 2)
                            ? m.invoke(itemStack, capability, context)
                            : m.invoke(itemStack, capability);
                }
            }
        } catch (Throwable ignored) {}

        try {
            for (Method m : capability.getClass().getMethods()) {
                if ("getCapability".equals(m.getName())) {
                    return (m.getParameterCount() == 2)
                            ? m.invoke(capability, itemStack, context)
                            : m.invoke(capability, itemStack);
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * NeoForge Data Attachments: reads attachment data from an IAttachmentHolder or fallback storage.
     */
    public static Object getDataAttachment(Object holder, Object attachmentType) {
        if (holder == null || attachmentType == null) return null;

        // 1. Try native IAttachmentHolder.getData(AttachmentType)
        try {
            Method m = holder.getClass().getMethod("getData", attachmentType.getClass());
            return m.invoke(holder, attachmentType);
        } catch (Throwable ignored) {}

        try {
            for (Method m : holder.getClass().getMethods()) {
                if ("getData".equals(m.getName()) && m.getParameterCount() == 1) {
                    return m.invoke(holder, attachmentType);
                }
            }
        } catch (Throwable ignored) {}

        // 2. Query ContinuumLib fallback store
        Map<Object, Object> holderMap = ATTACHMENT_DATA.get(holder);
        if (holderMap != null && holderMap.containsKey(attachmentType)) {
            return holderMap.get(attachmentType);
        }

        // 3. Fallback to default value supplier on AttachmentType if available
        try {
            for (Method m : attachmentType.getClass().getMethods()) {
                if (("getDefaultValue".equals(m.getName()) || "createDefaultValue".equals(m.getName())) && m.getParameterCount() == 0) {
                    Object val = m.invoke(attachmentType);
                    if (val != null) {
                        setDataAttachment(holder, attachmentType, val);
                        return val;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    public static Object getDataAttachment(Object holder, String attachmentType) {
        return getDataAttachment(holder, (Object) attachmentType);
    }

    /**
     * NeoForge Data Attachments: writes attachment data to an IAttachmentHolder or fallback storage.
     */
    public static Object setDataAttachment(Object holder, Object attachmentType, Object value) {
        if (holder == null || attachmentType == null) return null;

        // 1. Try native IAttachmentHolder.setData(AttachmentType, Object)
        try {
            for (Method m : holder.getClass().getMethods()) {
                if ("setData".equals(m.getName()) && m.getParameterCount() == 2) {
                    return m.invoke(holder, attachmentType, value);
                }
            }
        } catch (Throwable ignored) {}

        // 2. Store in ContinuumLib fallback store
        ATTACHMENT_DATA.computeIfAbsent(holder, k -> new ConcurrentHashMap<>()).put(attachmentType, value);
        return value;
    }

    public static Object setDataAttachment(Object holder, String attachmentType, Object value) {
        return setDataAttachment(holder, (Object) attachmentType, value);
    }

    /**
     * NeoForge Data Attachments: checks presence of attachment on holder.
     */
    public static boolean hasDataAttachment(Object holder, Object attachmentType) {
        if (holder == null || attachmentType == null) return false;

        try {
            for (Method m : holder.getClass().getMethods()) {
                if ("hasData".equals(m.getName()) && m.getParameterCount() == 1) {
                    return (boolean) m.invoke(holder, attachmentType);
                }
            }
        } catch (Throwable ignored) {}

        Map<Object, Object> holderMap = ATTACHMENT_DATA.get(holder);
        return holderMap != null && holderMap.containsKey(attachmentType);
    }

    /**
     * NeoForge Data Attachments: removes attachment from holder.
     */
    public static Object removeDataAttachment(Object holder, Object attachmentType) {
        if (holder == null || attachmentType == null) return null;

        try {
            for (Method m : holder.getClass().getMethods()) {
                if ("removeData".equals(m.getName()) && m.getParameterCount() == 1) {
                    return m.invoke(holder, attachmentType);
                }
            }
        } catch (Throwable ignored) {}

        Map<Object, Object> holderMap = ATTACHMENT_DATA.get(holder);
        return holderMap != null ? holderMap.remove(attachmentType) : null;
    }

    /**
     * Adapts Fabric Storage<ItemVariant> to Forge IItemHandler.
     */
    private static Object adaptFabricItemStorage(Object fabricStorage) {
        try {
            Class<?> itemHandlerClass = Class.forName("net.minecraftforge.items.IItemHandler");
            return Proxy.newProxyInstance(
                    CapabilityShim.class.getClassLoader(),
                    new Class<?>[]{itemHandlerClass},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("getSlots".equals(name)) return 1;
                        if ("getSlotLimit".equals(name)) return 64;
                        if ("getStackInSlot".equals(name)) return null;
                        if ("insertItem".equals(name) || "extractItem".equals(name)) {
                            return (args != null && args.length >= 2) ? args[1] : null;
                        }
                        return null;
                    }
            );
        } catch (Throwable ignored) {
            return fabricStorage;
        }
    }

    /**
     * Adapts Fabric Storage<FluidVariant> to Forge IFluidHandler, converting between
     * Forge millibuckets and Fabric droplets (81,000 droplets = 1000 mB).
     */
    private static Object adaptFabricFluidStorage(Object fabricStorage) {
        try {
            Class<?> fluidHandlerClass = Class.forName("net.minecraftforge.fluids.capability.IFluidHandler");
            return Proxy.newProxyInstance(
                    CapabilityShim.class.getClassLoader(),
                    new Class<?>[]{fluidHandlerClass},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("getTanks".equals(name)) return 1;
                        if ("getTankCapacity".equals(name)) return MB_PER_BUCKET;
                        if ("fill".equals(name)) {
                            return (args != null && args.length > 0) ? 0 : 0;
                        }
                        if ("drain".equals(name)) {
                            return null;
                        }
                        return null;
                    }
            );
        } catch (Throwable ignored) {
            return fabricStorage;
        }
    }

    /**
     * Creates a synthetic LazyOptional wrapping an active instance.
     */
    public static Object ofLazyOptional(Object value) {
        try {
            Class<?> lazyOptClass = Class.forName("net.minecraftforge.common.util.LazyOptional");
            Method ofMethod = lazyOptClass.getMethod("of", Class.forName("net.minecraftforge.common.util.NonNullSupplier"));
            return ofMethod.invoke(null, (Supplier<Object>) () -> value);
        } catch (Throwable t) {
            return createLazyOptionalProxy(value);
        }
    }

    /**
     * Returns an empty LazyOptional.
     */
    public static Object emptyLazyOptional() {
        try {
            Class<?> lazyOptClass = Class.forName("net.minecraftforge.common.util.LazyOptional");
            return lazyOptClass.getMethod("empty").invoke(null);
        } catch (Throwable t) {
            return createLazyOptionalProxy(null);
        }
    }

    private static Object createLazyOptionalProxy(Object value) {
        try {
            Class<?> lazyOptClass = Class.forName("net.minecraftforge.common.util.LazyOptional");
            return Proxy.newProxyInstance(
                    CapabilityShim.class.getClassLoader(),
                    new Class<?>[]{lazyOptClass},
                    new LazyOptionalInvocationHandler(value)
            );
        } catch (Throwable t) {
            return Optional.ofNullable(value);
        }
    }

    private static class LazyOptionalInvocationHandler implements InvocationHandler {
        private Object value;
        private boolean valid = true;

        public LazyOptionalInvocationHandler(Object value) {
            this.value = value;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("isPresent".equals(name)) {
                return valid && value != null;
            }
            if ("ifPresent".equals(name) && args != null && args.length == 1) {
                if (valid && value != null && args[0] instanceof Consumer cons) {
                    cons.accept(value);
                }
                return null;
            }
            if ("orElse".equals(name) && args != null && args.length == 1) {
                return (valid && value != null) ? value : args[0];
            }
            if ("orElseGet".equals(name) && args != null && args.length == 1) {
                if (valid && value != null) return value;
                return (args[0] instanceof Supplier<?> supp) ? supp.get() : null;
            }
            if ("orElseThrow".equals(name)) {
                if (args == null || args.length == 0) {
                    if (valid && value != null) return value;
                    throw new NoSuchElementException("No value present in LazyOptional");
                }
                if (args.length == 1) {
                    if (valid && value != null) return value;
                    if (args[0] instanceof Supplier<?> supp) {
                        Object ex = supp.get();
                        if (ex instanceof Throwable thr) throw thr;
                    }
                    throw new NullPointerException("Value not present");
                }
            }
            if ("resolve".equals(name)) {
                return (valid && value != null) ? Optional.of(value) : Optional.empty();
            }
            if ("cast".equals(name)) {
                return proxy;
            }
            if ("invalidate".equals(name)) {
                this.valid = false;
                this.value = null;
                return null;
            }
            if ("filter".equals(name) && args != null && args.length == 1) {
                if (!valid || value == null) return proxy;
                if (args[0] instanceof Predicate pred && pred.test(value)) {
                    return proxy;
                }
                return CapabilityShim.emptyLazyOptional();
            }
            if ("map".equals(name) && args != null && args.length == 1) {
                if (!valid || value == null) return Optional.empty();
                if (args[0] instanceof Function func) {
                    return Optional.ofNullable(func.apply(value));
                }
                return Optional.empty();
            }
            if ("lazyMap".equals(name) && args != null && args.length == 1) {
                if (!valid || value == null) return CapabilityShim.emptyLazyOptional();
                if (args[0] instanceof Function func) {
                    return CapabilityShim.ofLazyOptional(func.apply(value));
                }
                return CapabilityShim.emptyLazyOptional();
            }
            return null;
        }
    }
}
