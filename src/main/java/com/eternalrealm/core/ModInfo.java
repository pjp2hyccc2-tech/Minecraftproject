package com.eternalrealm.core;

/**
 * Mod-wide constants usable from both pure (Minecraft-independent) code and Forge integration code.
 */
public final class ModInfo {
    /** Must match {@code mod_id} in gradle.properties and the {@code @Mod} annotation. */
    public static final String MOD_ID = "eternalrealm";
    public static final String DISPLAY_NAME = "ETERNAL REALM";

    private ModInfo() {
    }
}
