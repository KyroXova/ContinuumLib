package com.kyroxova.bootstrapper.hooks;

/**
 * Standard interface for class transformation pipelines.
 */
public interface ContinuumTransformerHook {

    /**
     * Transforms the given class bytecode.
     *
     * @param className internal slash-separated or dot-separated class name
     * @param basicClass incoming bytecode array
     * @return transformed bytecode array, or original if untouched
     */
    byte[] transform(String className, byte[] basicClass);
}
