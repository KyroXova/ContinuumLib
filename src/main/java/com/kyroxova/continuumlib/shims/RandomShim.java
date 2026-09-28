package com.kyroxova.continuumlib.shims;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Random and RandomSource across Minecraft versions.
 * In Minecraft 1.19+, Mojang transitioned from java.util.Random to net.minecraft.util.RandomSource.
 * This shim enables bidirectional wrapping and synthetic method dispatch.
 */
public final class RandomShim {

    private static final Logger LOGGER = Logger.getLogger(RandomShim.class.getName());

    private RandomShim() {}

    /**
     * Adapts modern RandomSource to legacy java.util.Random.
     */
    public static Random toLegacyRandom(Object randomSource) {
        if (randomSource == null) return new Random();
        if (randomSource instanceof Random r) return r;
        return new RandomSourceWrapper(randomSource);
    }

    /**
     * Adapts legacy java.util.Random to modern RandomSource interface.
     */
    public static Object toRandomSource(Random random) {
        if (random == null) random = new Random();
        try {
            Class<?> randomSourceClass = Class.forName("net.minecraft.util.RandomSource");
            if (randomSourceClass.isInstance(random)) return random;

            Random finalRandom = random;
            return Proxy.newProxyInstance(
                    RandomShim.class.getClassLoader(),
                    new Class<?>[]{randomSourceClass},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            String name = method.getName();
                            if ("nextInt".equals(name)) {
                                if (args != null && args.length == 1) {
                                    return finalRandom.nextInt((Integer) args[0]);
                                }
                                return finalRandom.nextInt();
                            } else if ("nextLong".equals(name)) {
                                return finalRandom.nextLong();
                            } else if ("nextBoolean".equals(name)) {
                                return finalRandom.nextBoolean();
                            } else if ("nextFloat".equals(name)) {
                                return finalRandom.nextFloat();
                            } else if ("nextDouble".equals(name)) {
                                return finalRandom.nextDouble();
                            } else if ("nextGaussian".equals(name)) {
                                return finalRandom.nextGaussian();
                            }
                            return null;
                        }
                    }
            );
        } catch (Throwable t) {
            return random;
        }
    }

    /**
     * Intercepts and bridges legacy Block.animateTick(..., Random) on modern 1.19+ runtimes.
     */
    public static void animateTick(Object block, Object state, Object level, Object pos, Object random) {
        if (block == null) return;
        try {
            Class<?> blockClass = block.getClass();
            // Try modern 1.19+ animateTick(..., RandomSource)
            for (Method m : blockClass.getMethods()) {
                if ("animateTick".equals(m.getName()) && m.getParameterCount() == 4) {
                    Object randomSource = (random instanceof Random r) ? toRandomSource(r) : random;
                    m.invoke(block, state, level, pos, randomSource);
                    return;
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[RandomShim] Error dispatching animateTick: " + t.getMessage());
        }
    }

    /**
     * Intercepts and bridges legacy Block.randomTick(..., Random) on modern 1.19+ runtimes.
     */
    public static void randomTick(Object block, Object state, Object level, Object pos, Object random) {
        if (block == null) return;
        try {
            Class<?> blockClass = block.getClass();
            for (Method m : blockClass.getMethods()) {
                if ("randomTick".equals(m.getName()) && m.getParameterCount() == 4) {
                    Object randomSource = (random instanceof Random r) ? toRandomSource(r) : random;
                    m.invoke(block, state, level, pos, randomSource);
                    return;
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[RandomShim] Error dispatching randomTick: " + t.getMessage());
        }
    }

    /**
     * Custom java.util.Random wrapper that delegates to a modern RandomSource instance.
     */
    public static class RandomSourceWrapper extends Random {
        private final Object source;

        public RandomSourceWrapper(Object source) {
            this.source = source;
        }

        @Override
        public int nextInt() {
            try {
                return (int) source.getClass().getMethod("nextInt").invoke(source);
            } catch (Throwable t) {
                return super.nextInt();
            }
        }

        @Override
        public int nextInt(int bound) {
            try {
                return (int) source.getClass().getMethod("nextInt", int.class).invoke(source, bound);
            } catch (Throwable t) {
                return super.nextInt(bound);
            }
        }

        @Override
        public long nextLong() {
            try {
                return (long) source.getClass().getMethod("nextLong").invoke(source);
            } catch (Throwable t) {
                return super.nextLong();
            }
        }

        @Override
        public boolean nextBoolean() {
            try {
                return (boolean) source.getClass().getMethod("nextBoolean").invoke(source);
            } catch (Throwable t) {
                return super.nextBoolean();
            }
        }

        @Override
        public float nextFloat() {
            try {
                return (float) source.getClass().getMethod("nextFloat").invoke(source);
            } catch (Throwable t) {
                return super.nextFloat();
            }
        }

        @Override
        public double nextDouble() {
            try {
                return (double) source.getClass().getMethod("nextDouble").invoke(source);
            } catch (Throwable t) {
                return super.nextDouble();
            }
        }

        @Override
        public synchronized double nextGaussian() {
            try {
                return (double) source.getClass().getMethod("nextGaussian").invoke(source);
            } catch (Throwable t) {
                return super.nextGaussian();
            }
        }

        @Override
        protected int next(int bits) {
            return nextInt() >>> (32 - bits);
        }
    }
}
