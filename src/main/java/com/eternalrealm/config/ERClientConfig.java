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
    public static final ForgeConfigSpec.BooleanValue DAMAGE_NUMBERS;
    public static final ForgeConfigSpec.BooleanValue COMBAT_HUD;
    public static final ForgeConfigSpec.BooleanValue CAMERA_SHAKE;
    public static final ForgeConfigSpec.BooleanValue WEAPON_ANIMATIONS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Character HUD (level / EXP / unspent points)").push("hud");
        HUD_ENABLED = builder.define("enabled", true);
        HUD_X = builder.comment("Left offset in GUI pixels").defineInRange("x", 4, 0, 4096);
        HUD_Y = builder.comment("Top offset in GUI pixels").defineInRange("y", 4, 0, 4096);
        builder.pop();
        builder.comment("Combat feedback").push("combat");
        DAMAGE_NUMBERS = builder.comment("Show the damage you deal as floating numbers").define("damageNumbers", true);
        COMBAT_HUD = builder.comment("Show stamina, charge, skill cooldown and defense popups").define("combatHud", true);
        CAMERA_SHAKE = builder.comment("Shake the camera on heavy hits, skills and boss slams").define("cameraShake", true);
        WEAPON_ANIMATIONS = builder.comment("Animate weapons and arms for combo steps, skills and charges (first and third person)")
                .define("weaponAnimations", true);
        builder.pop();
        SPEC = builder.build();
    }

    private ERClientConfig() {
    }
}
