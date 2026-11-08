package com.eternalrealm.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client-side preferences ({@code config/eternalrealm-client.toml}). Contains no client-only classes, so it is safe
 * to register from common code.
 */
public final class ERClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue HUD_ENABLED;
    public static final ForgeConfigSpec.IntValue HUD_X;
    public static final ForgeConfigSpec.IntValue HUD_Y;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Character HUD (level / EXP / unspent points)").push("hud");
        HUD_ENABLED = builder.define("enabled", true);
        HUD_X = builder.comment("Left offset in GUI pixels").defineInRange("x", 4, 0, 4096);
        HUD_Y = builder.comment("Top offset in GUI pixels").defineInRange("y", 4, 0, 4096);
        builder.pop();
        SPEC = builder.build();
    }

    private ERClientConfig() {
    }
}
