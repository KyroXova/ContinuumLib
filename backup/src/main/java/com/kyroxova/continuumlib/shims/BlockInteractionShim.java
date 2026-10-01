package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Block interactions across Minecraft versions.
 * Supports bidirectional conversions between:
 * - 1.7.9: onBlockActivated(World, int, int, int, EntityPlayer, int, float, float, float)
 * - 1.18.2: use(BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult)
 * - 1.20.5/26.3+: useItemOn(ItemStack, BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult)
 */
public final class BlockInteractionShim {

    private static final Logger LOGGER = Logger.getLogger(BlockInteractionShim.class.getName());

    private static final ThreadLocal<Set<Object>> INTERACTING = ThreadLocal.withInitial(() -> Collections.newSetFromMap(new WeakHashMap<>()));

    private BlockInteractionShim() {}

    public static boolean pushInteracting(Object block) {
        if (block == null) return false;
        return INTERACTING.get().add(block);
    }

    public static void popInteracting(Object block) {
        if (block != null) {
            INTERACTING.get().remove(block);
        }
    }

    public static boolean isInteracting(Object block) {
        return block != null && INTERACTING.get().contains(block);
    }

    /**
     * Intercepts and bridges legacy Block.use(...) invocations on modern runtimes.
     */
    public static Object useBlock(Object block, Object state, Object level, Object pos, Object player, Object hand, Object hitResult) {
        if (block == null) return null;

        try {
            Class<?> blockClass = block.getClass();

            // Attempt 1: Modern 1.20.5+ useItemOn(...)
            try {
                Object itemStack = resolveHeldItem(player);

                // Look for useItemOn
                for (Method m : blockClass.getMethods()) {
                    if ("useItemOn".equals(m.getName()) && m.getParameterCount() == 7) {
                        Object res = m.invoke(block, itemStack, state, level, pos, player, hand, hitResult);
                        return toInteractionResult(res);
                    }
                }
            } catch (Throwable ignored) {}

            // Attempt 2: Modern 1.20.5+ useWithoutItem(...)
            try {
                for (Method m : blockClass.getMethods()) {
                    if ("useWithoutItem".equals(m.getName()) && m.getParameterCount() == 5) {
                        return m.invoke(block, state, level, pos, player, hitResult);
                    }
                }
            } catch (Throwable ignored) {}

            // Attempt 3: Legacy <= 1.20.4 use(...)
            try {
                for (Method m : blockClass.getMethods()) {
                    if ("use".equals(m.getName()) && m.getParameterCount() == 6) {
                        return m.invoke(block, state, level, pos, player, hand, hitResult);
                    }
                }
            } catch (Throwable ignored) {}

        } catch (Throwable t) {
            LOGGER.fine("[BlockInteractionShim] Error invoking block use: " + t.getMessage());
        }

        return null;
    }

    /**
     * Bidirectional Bridge: Dispatches a 1.7.9 onBlockActivated call to modern use or useItemOn.
     */
    public static boolean bridgeOnBlockActivated(Object block, Object world, int x, int y, int z, Object player, int side, float hitX, float hitY, float hitZ) {
        if (block == null) return false;
        if (isInteracting(block)) {
            return false;
        }

        pushInteracting(block);
        try {
            Class<?> clazz = block.getClass();

            // 1. Attempt useItemOn (1.20.5+ / 26.3+)
            for (Method m : clazz.getMethods()) {
                if ("useItemOn".equals(m.getName()) && m.getParameterCount() == 7 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Object stack = resolveHeldItem(player);
                    Object pos = resolveBlockPos(x, y, z);
                    Object state = resolveBlockState(block, world, pos);
                    Object hand = resolveMainHand();
                    Object hitResult = resolveBlockHitResult(x, y, z, side, hitX, hitY, hitZ);
                    Object res = m.invoke(block, stack, state, world, pos, player, hand, hitResult);
                    return isSuccess(res);
                }
            }

            // 2. Attempt use (1.18.2)
            for (Method m : clazz.getMethods()) {
                if ("use".equals(m.getName()) && m.getParameterCount() == 6 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Object pos = resolveBlockPos(x, y, z);
                    Object state = resolveBlockState(block, world, pos);
                    Object hand = resolveMainHand();
                    Object hitResult = resolveBlockHitResult(x, y, z, side, hitX, hitY, hitZ);
                    Object res = m.invoke(block, state, world, pos, player, hand, hitResult);
                    return isSuccess(res);
                }
            }

            // 3. Attempt direct onBlockActivated (if defined directly on mod class or super)
            for (Method m : clazz.getMethods()) {
                if ("onBlockActivated".equals(m.getName()) && m.getParameterCount() == 9 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Object res = m.invoke(block, world, x, y, z, player, side, hitX, hitY, hitZ);
                    return (res instanceof Boolean b) ? b : (res != null);
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[BlockInteractionShim] Error in bridgeOnBlockActivated: " + t.getMessage());
        } finally {
            popInteracting(block);
        }
        return false;
    }

    /**
     * Bidirectional Bridge: Dispatches a 1.18.2 use(...) call to useItemOn or onBlockActivated.
     */
    public static Object bridgeUse(Object block, Object state, Object level, Object pos, Object player, Object hand, Object hitResult) {
        if (block == null) return resolveInteractionResultEnum("PASS");
        if (isInteracting(block)) {
            return resolveInteractionResultEnum("PASS");
        }

        pushInteracting(block);
        try {
            Class<?> clazz = block.getClass();

            // 1. Attempt useItemOn (1.20.5+ / 26.3+)
            for (Method m : clazz.getMethods()) {
                if ("useItemOn".equals(m.getName()) && m.getParameterCount() == 7 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Object stack = resolveHeldItem(player);
                    Object res = m.invoke(block, stack, state, level, pos, player, hand, hitResult);
                    return toInteractionResult(res);
                }
            }

            // 2. Attempt onBlockActivated (1.7.9)
            for (Method m : clazz.getMethods()) {
                if ("onBlockActivated".equals(m.getName()) && m.getParameterCount() == 9 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    int x = getPosX(pos);
                    int y = getPosY(pos);
                    int z = getPosZ(pos);
                    int side = getHitSide(hitResult);
                    float hitX = getHitX(hitResult, pos);
                    float hitY = getHitY(hitResult, pos);
                    float hitZ = getHitZ(hitResult, pos);
                    Object res = m.invoke(block, level, x, y, z, player, side, hitX, hitY, hitZ);
                    boolean success = (res instanceof Boolean b) ? b : (res != null);
                    return resolveInteractionResultEnum(success ? "SUCCESS" : "PASS");
                }
            }

            // 3. Attempt direct use (if defined directly on mod class or super)
            for (Method m : clazz.getMethods()) {
                if ("use".equals(m.getName()) && m.getParameterCount() == 6 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    return m.invoke(block, state, level, pos, player, hand, hitResult);
                }
            }

            // 4. Attempt useWithoutItem (5 params)
            for (Method m : clazz.getMethods()) {
                if ("useWithoutItem".equals(m.getName()) && m.getParameterCount() == 5 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    return m.invoke(block, state, level, pos, player, hitResult);
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[BlockInteractionShim] Error in bridgeUse: " + t.getMessage());
        } finally {
            popInteracting(block);
        }
        return resolveInteractionResultEnum("PASS");
    }

    /**
     * Bidirectional Bridge: Dispatches a modern useItemOn(...) call to 1.18.2 use(...) or 1.7.9 onBlockActivated.
     */
    public static Object bridgeUseItemOn(Object block, Object itemStack, Object state, Object level, Object pos, Object player, Object hand, Object hitResult, boolean returnItemResult) {
        if (block == null) {
            Object pass = resolveInteractionResultEnum("PASS");
            return returnItemResult ? toItemInteractionResult(pass) : pass;
        }
        if (isInteracting(block)) {
            Object pass = resolveInteractionResultEnum("PASS");
            return returnItemResult ? toItemInteractionResult(pass) : pass;
        }

        pushInteracting(block);
        try {
            Class<?> clazz = block.getClass();

            // 1. Attempt use (1.18.2)
            for (Method m : clazz.getMethods()) {
                if ("use".equals(m.getName()) && m.getParameterCount() == 6 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Object res = m.invoke(block, state, level, pos, player, hand, hitResult);
                    return returnItemResult ? toItemInteractionResult(res) : toInteractionResult(res);
                }
            }

            // 2. Attempt onBlockActivated (1.7.9)
            for (Method m : clazz.getMethods()) {
                if ("onBlockActivated".equals(m.getName()) && m.getParameterCount() == 9 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    int x = getPosX(pos);
                    int y = getPosY(pos);
                    int z = getPosZ(pos);
                    int side = getHitSide(hitResult);
                    float hitX = getHitX(hitResult, pos);
                    float hitY = getHitY(hitResult, pos);
                    float hitZ = getHitZ(hitResult, pos);
                    Object res = m.invoke(block, level, x, y, z, player, side, hitX, hitY, hitZ);
                    boolean success = (res instanceof Boolean b) ? b : (res != null);
                    Object ir = resolveInteractionResultEnum(success ? "SUCCESS" : "PASS");
                    return returnItemResult ? toItemInteractionResult(ir) : ir;
                }
            }

            // 3. Attempt direct useItemOn (if defined directly on mod class or super)
            for (Method m : clazz.getMethods()) {
                if ("useItemOn".equals(m.getName()) && m.getParameterCount() == 7 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Object res = m.invoke(block, itemStack, state, level, pos, player, hand, hitResult);
                    return returnItemResult ? toItemInteractionResult(res) : toInteractionResult(res);
                }
            }

            // 4. Attempt useWithoutItem (5 params)
            for (Method m : clazz.getMethods()) {
                if ("useWithoutItem".equals(m.getName()) && m.getParameterCount() == 5 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Object res = m.invoke(block, state, level, pos, player, hitResult);
                    return returnItemResult ? toItemInteractionResult(res) : toInteractionResult(res);
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[BlockInteractionShim] Error in bridgeUseItemOn: " + t.getMessage());
        } finally {
            popInteracting(block);
        }
        Object pass = resolveInteractionResultEnum("PASS");
        return returnItemResult ? toItemInteractionResult(pass) : pass;
    }

    /**
     * Converts InteractionResult -> ItemInteractionResult on 1.20.5 - 1.21.1.
     */
    public static Object toItemInteractionResult(Object interactionResult) {
        if (interactionResult == null) return null;

        try {
            Class<?> itemResultClass = Class.forName("net.minecraft.world.ItemInteractionResult");
            String resultName = (interactionResult instanceof Enum<?> e) ? e.name() : interactionResult.toString();

            if ("SUCCESS".equalsIgnoreCase(resultName)) {
                try {
                    return itemResultClass.getField("SUCCESS").get(null);
                } catch (NoSuchFieldException e) {
                    return itemResultClass.getField("CONSUME").get(null);
                }
            } else if ("CONSUME".equalsIgnoreCase(resultName) || "CONSUME_PARTIAL".equalsIgnoreCase(resultName)) {
                return itemResultClass.getField("CONSUME").get(null);
            } else if ("PASS".equalsIgnoreCase(resultName)) {
                return itemResultClass.getField("PASS_TO_DEFAULT_BLOCK_INTERACTION").get(null);
            } else if ("FAIL".equalsIgnoreCase(resultName)) {
                return itemResultClass.getField("FAIL").get(null);
            }

            return itemResultClass.getField("PASS_TO_DEFAULT_BLOCK_INTERACTION").get(null);
        } catch (Throwable t) {
            // In 1.21.2+ / 26.3+, ItemInteractionResult doesn't exist, so return interactionResult directly
            return interactionResult;
        }
    }

    /**
     * Converts ItemInteractionResult -> InteractionResult on 1.20.5 - 1.21.1.
     */
    public static Object toInteractionResult(Object itemInteractionResult) {
        if (itemInteractionResult == null) return resolveInteractionResultEnum("PASS");

        // Support InteractionResultHolder.getResult()
        try {
            for (Method m : itemInteractionResult.getClass().getMethods()) {
                if ("getResult".equals(m.getName()) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    return m.invoke(itemInteractionResult);
                }
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> interResultClass = Class.forName("net.minecraft.world.InteractionResult");
            String resultName = (itemInteractionResult instanceof Enum<?> e) ? e.name() : itemInteractionResult.toString();

            if ("SUCCESS".equalsIgnoreCase(resultName)) {
                return interResultClass.getField("SUCCESS").get(null);
            } else if ("CONSUME".equalsIgnoreCase(resultName) || "CONSUME_PARTIAL".equalsIgnoreCase(resultName)) {
                return interResultClass.getField("CONSUME").get(null);
            } else if ("PASS_TO_DEFAULT_BLOCK_INTERACTION".equalsIgnoreCase(resultName) || "PASS".equalsIgnoreCase(resultName)) {
                return interResultClass.getField("PASS").get(null);
            } else if ("FAIL".equalsIgnoreCase(resultName)) {
                return interResultClass.getField("FAIL").get(null);
            }

            return interResultClass.getField("PASS").get(null);
        } catch (Throwable t) {
            return itemInteractionResult;
        }
    }

    public static Object resolveHeldItem(Object player) {
        if (player == null) return null;
        try {
            for (Method m : player.getClass().getMethods()) {
                if ("getMainHandItem".equals(m.getName()) && m.getParameterCount() == 0) {
                    return m.invoke(player);
                }
            }
        } catch (Throwable ignored) {}
        try {
            for (Method m : player.getClass().getMethods()) {
                if ("getHeldItemMainhand".equals(m.getName()) && m.getParameterCount() == 0) {
                    return m.invoke(player);
                }
            }
        } catch (Throwable ignored) {}
        try {
            for (Method m : player.getClass().getMethods()) {
                if ("getHeldItem".equals(m.getName()) && m.getParameterCount() == 0) {
                    return m.invoke(player);
                }
            }
        } catch (Throwable ignored) {}
        try {
            Class<?> isClass = Class.forName("net.minecraft.world.item.ItemStack");
            return isClass.getField("EMPTY").get(null);
        } catch (Throwable ignored) {}
        return null;
    }

    public static Object resolveBlockPos(int x, int y, int z) {
        try {
            Class<?> bpClass = Class.forName("net.minecraft.core.BlockPos");
            return bpClass.getConstructor(int.class, int.class, int.class).newInstance(x, y, z);
        } catch (Throwable ignored) {}
        try {
            Class<?> bpClass = Class.forName("net.minecraft.util.math.BlockPos");
            return bpClass.getConstructor(int.class, int.class, int.class).newInstance(x, y, z);
        } catch (Throwable ignored) {}
        return null;
    }

    public static Object resolveBlockState(Object block, Object world, Object pos) {
        if (world != null && pos != null) {
            try {
                for (Method m : world.getClass().getMethods()) {
                    if ("getBlockState".equals(m.getName()) && m.getParameterCount() == 1) {
                        return m.invoke(world, pos);
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (block != null) {
            try {
                for (Method m : block.getClass().getMethods()) {
                    if ("defaultBlockState".equals(m.getName()) && m.getParameterCount() == 0) {
                        return m.invoke(block);
                    }
                }
            } catch (Throwable ignored) {}
            try {
                for (Method m : block.getClass().getMethods()) {
                    if ("getDefaultState".equals(m.getName()) && m.getParameterCount() == 0) {
                        return m.invoke(block);
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static Object resolveMainHand() {
        try {
            Class<?> handClass = Class.forName("net.minecraft.world.InteractionHand");
            return handClass.getField("MAIN_HAND").get(null);
        } catch (Throwable ignored) {}
        try {
            Class<?> handClass = Class.forName("net.minecraft.util.EnumHand");
            return handClass.getField("MAIN_HAND").get(null);
        } catch (Throwable ignored) {}
        return null;
    }

    public static Object resolveBlockHitResult(int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
        try {
            Class<?> bhrClass = Class.forName("net.minecraft.world.phys.BlockHitResult");
            Class<?> vec3Class = Class.forName("net.minecraft.world.phys.Vec3");
            Class<?> dirClass = Class.forName("net.minecraft.core.Direction");
            Class<?> bpClass = Class.forName("net.minecraft.core.BlockPos");

            Object vec3 = vec3Class.getConstructor(double.class, double.class, double.class)
                    .newInstance(x + (double) hitX, y + (double) hitY, z + (double) hitZ);
            Object pos = bpClass.getConstructor(int.class, int.class, int.class).newInstance(x, y, z);
            Object dir = null;
            try {
                Method from3D = dirClass.getMethod("from3DDataValue", int.class);
                dir = from3D.invoke(null, side);
            } catch (Throwable t) {
                Object[] dirs = dirClass.getEnumConstants();
                if (dirs != null && side >= 0 && side < dirs.length) {
                    dir = dirs[side];
                }
            }
            return bhrClass.getConstructor(vec3Class, dirClass, bpClass, boolean.class)
                    .newInstance(vec3, dir, pos, false);
        } catch (Throwable ignored) {}
        return null;
    }

    public static int getPosX(Object pos) {
        if (pos == null) return 0;
        try {
            return (int) pos.getClass().getMethod("getX").invoke(pos);
        } catch (Throwable ignored) {}
        return 0;
    }

    public static int getPosY(Object pos) {
        if (pos == null) return 0;
        try {
            return (int) pos.getClass().getMethod("getY").invoke(pos);
        } catch (Throwable ignored) {}
        return 0;
    }

    public static int getPosZ(Object pos) {
        if (pos == null) return 0;
        try {
            return (int) pos.getClass().getMethod("getZ").invoke(pos);
        } catch (Throwable ignored) {}
        return 0;
    }

    public static int getHitSide(Object hitResult) {
        if (hitResult == null) return 0;
        try {
            Object dir = hitResult.getClass().getMethod("getDirection").invoke(hitResult);
            if (dir != null) {
                try {
                    return (int) dir.getClass().getMethod("get3DDataValue").invoke(dir);
                } catch (Throwable ignored) {}
                if (dir instanceof Enum<?> e) return e.ordinal();
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    public static float getHitX(Object hitResult, Object pos) {
        if (hitResult == null) return 0.5f;
        try {
            Object loc = hitResult.getClass().getMethod("getLocation").invoke(hitResult);
            if (loc != null) {
                double x = (double) loc.getClass().getField("x").get(loc);
                int px = getPosX(pos);
                return (float) (x - px);
            }
        } catch (Throwable ignored) {}
        return 0.5f;
    }

    public static float getHitY(Object hitResult, Object pos) {
        if (hitResult == null) return 0.5f;
        try {
            Object loc = hitResult.getClass().getMethod("getLocation").invoke(hitResult);
            if (loc != null) {
                double y = (double) loc.getClass().getField("y").get(loc);
                int py = getPosY(pos);
                return (float) (y - py);
            }
        } catch (Throwable ignored) {}
        return 0.5f;
    }

    public static float getHitZ(Object hitResult, Object pos) {
        if (hitResult == null) return 0.5f;
        try {
            Object loc = hitResult.getClass().getMethod("getLocation").invoke(hitResult);
            if (loc != null) {
                double z = (double) loc.getClass().getField("z").get(loc);
                int pz = getPosZ(pos);
                return (float) (z - pz);
            }
        } catch (Throwable ignored) {}
        return 0.5f;
    }

    public static boolean isSuccess(Object result) {
        if (result == null) return false;
        if (result instanceof Boolean b) return b;
        String name = (result instanceof Enum<?> e) ? e.name() : result.toString();
        return name.equalsIgnoreCase("SUCCESS")
                || name.equalsIgnoreCase("CONSUME")
                || name.equalsIgnoreCase("CONSUME_PARTIAL")
                || name.equalsIgnoreCase("SUCCESS_NO_ITEM_USED");
    }

    public static Object resolveInteractionResultEnum(String name) {
        try {
            Class<?> irClass = Class.forName("net.minecraft.world.InteractionResult");
            if (irClass.isEnum()) {
                for (Object constant : irClass.getEnumConstants()) {
                    if (((Enum<?>) constant).name().equalsIgnoreCase(name)) {
                        return constant;
                    }
                }
            }
        } catch (Throwable ignored) {}
        try {
            Class<?> arClass = Class.forName("net.minecraft.util.ActionResultType");
            if (arClass.isEnum()) {
                for (Object constant : arClass.getEnumConstants()) {
                    if (((Enum<?>) constant).name().equalsIgnoreCase(name)) {
                        return constant;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return name;
    }
}
