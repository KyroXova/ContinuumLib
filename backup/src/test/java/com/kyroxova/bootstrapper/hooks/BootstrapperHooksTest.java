package com.kyroxova.bootstrapper.hooks;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class BootstrapperHooksTest {

    @AfterEach
    public void tearDown() {
        FabricPreLaunchHook.reset();
        ModLauncherPluginHook.reset();
        LegacyCoreModHook.reset();
    }

    @Test
    public void testFabricPreLaunchHook() {
        FabricPreLaunchHook hook = new FabricPreLaunchHook();
        assertFalse(FabricPreLaunchHook.isInitialized());

        hook.onPreLaunch();
        assertTrue(FabricPreLaunchHook.isInitialized());

        byte[] input = new byte[]{ 1, 2, 3 };
        byte[] output = hook.transform("com/example/MyClass", input);
        assertNotNull(output);
    }

    @Test
    public void testModLauncherPluginHook() {
        ModLauncherPluginHook hook = new ModLauncherPluginHook();
        assertFalse(ModLauncherPluginHook.isInitialized());
        assertEquals("continuumlib", hook.name());

        hook.initialize(new Object());
        assertTrue(ModLauncherPluginHook.isInitialized());
        assertFalse(hook.transformers().isEmpty());

        hook.onLoad(new Object(), Collections.singleton("fml"));

        byte[] input = new byte[]{ 1, 2, 3 };
        byte[] output = hook.transform("com/example/MyClass", input);
        assertNotNull(output);
    }

    @Test
    public void testLegacyCoreModHook() {
        LegacyCoreModHook hook = new LegacyCoreModHook();
        assertFalse(LegacyCoreModHook.isInitialized());

        String[] transformers = hook.getASMTransformerClass();
        assertNotNull(transformers);
        assertEquals(1, transformers.length);
        assertEquals(LegacyCoreModHook.class.getName(), transformers[0]);

        assertNull(hook.getModContainerClass());
        assertNull(hook.getSetupClass());
        assertNull(hook.getAccessTransformerClass());

        hook.injectData(Collections.emptyMap());
        assertTrue(LegacyCoreModHook.isInitialized());

        byte[] input = new byte[]{ 1, 2, 3 };
        byte[] output = hook.transform("com.example.MyClass", "com.example.MyClass", input);
        assertNotNull(output);
    }
}
