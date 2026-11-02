package com.meikon;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server ("common") and client settings. */
public final class MeikonConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    private static final ForgeConfigSpec.DoubleValue BRIDE_SPEED;
    private static final ForgeConfigSpec.DoubleValue FEAR_RATE;
    private static final ForgeConfigSpec.BooleanValue GIVE_ENVELOPE;

    private static final ForgeConfigSpec.BooleanValue FLASH_REDUCTION;
    private static final ForgeConfigSpec.BooleanValue JUMPSCARES;
    private static final ForgeConfigSpec.DoubleValue SCREEN_EFFECTS;
    private static final ForgeConfigSpec.BooleanValue INSTALL_SHADERS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("game");
        BRIDE_SPEED = b.comment("Speed multiplier of the bride (0.6 = easier, 1.3 = harder)").defineInRange("brideSpeed", 1.0, 0.5, 1.6);
        FEAR_RATE = b.comment("How fast fear rises (0.5 = calmer)").defineInRange("fearRate", 1.0, 0.2, 2.0);
        GIVE_ENVELOPE = b.comment("Give every player the red envelope when they first join a world").define("giveEnvelope", true);
        b.pop();
        COMMON_SPEC = b.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        c.push("accessibility");
        FLASH_REDUCTION = c.comment("Disable flashing, strobing and glitch effects (photosensitive players)").define("flashReduction", false);
        JUMPSCARES = c.comment("Show the full-screen jump scare images").define("jumpscares", true);
        SCREEN_EFFECTS = c.comment("Strength of the fear screen effects (vignette, grain, shake)").defineInRange("screenEffects", 1.0, 0.0, 1.5);
        c.pop();
        c.push("shaders");
        INSTALL_SHADERS = c.comment("Copy the bundled MeikonShaders pack into shaderpacks/ (and select it in Oculus/Iris/OptiFine if none is selected)")
                .define("installShaderPack", true);
        c.pop();
        CLIENT_SPEC = c.build();
    }

    private MeikonConfig() {}

    private static double d(ForgeConfigSpec.DoubleValue v, double def) {
        try {
            return v.get();
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean b(ForgeConfigSpec.BooleanValue v, boolean def) {
        try {
            return v.get();
        } catch (Exception e) {
            return def;
        }
    }

    public static double brideSpeed() {
        return d(BRIDE_SPEED, 1.0);
    }

    public static double fearRate() {
        return d(FEAR_RATE, 1.0);
    }

    public static boolean giveEnvelope() {
        return b(GIVE_ENVELOPE, true);
    }

    public static boolean flashReduction() {
        return b(FLASH_REDUCTION, false);
    }

    public static boolean jumpscares() {
        return b(JUMPSCARES, true);
    }

    public static double screenEffects() {
        return d(SCREEN_EFFECTS, 1.0);
    }

    public static boolean installShaders() {
        return b(INSTALL_SHADERS, true);
    }
}
