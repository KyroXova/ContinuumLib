package com.kyroxova.continuumlib.gradle;

/** Separate target-JVM process for integration tests; never included in the library JAR. */
public final class RuntimeJarProbe {
    public static void main(String[] arguments) throws Exception {
        Object result = Class.forName(arguments[0]).getMethod(arguments[1]).invoke(null);
        if (!arguments[2].equals(result)) throw new AssertionError("Unexpected target execution result: " + result);
        System.out.println("TARGET_EXECUTION_OK:" + result);
    }
}
