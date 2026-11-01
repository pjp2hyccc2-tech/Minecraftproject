package com.frostbound.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Guards the architecture rule: the simulation core never sees Minecraft or Forge. */
class CoreIsolationTest {

    @Test
    void minecraftIsNotOnTheCoreClasspath() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("net.minecraft.world.level.Level"));
    }

    @Test
    void forgeIsNotOnTheCoreClasspath() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("net.minecraftforge.common.MinecraftForge"));
    }

    @Test
    void modIdIsStable() {
        // The mod id is baked into save files and resource locations; changing it breaks worlds.
        assertEquals("frostbound", CoreInfo.MOD_ID);
    }
}
