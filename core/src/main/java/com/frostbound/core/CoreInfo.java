package com.frostbound.core;

/**
 * Identity of the FROSTBOUND simulation core.
 *
 * <p>Everything under {@code com.frostbound.core} must stay free of Minecraft and Forge classes.
 * The Gradle module has no Minecraft dependency, and {@code CoreIsolationTest} checks it at test time.
 */
public final class CoreInfo {
    /** Mod id shared by the core and the Forge layer. */
    public static final String MOD_ID = "frostbound";

    private CoreInfo() {
    }
}
