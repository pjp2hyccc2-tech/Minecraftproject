package com.sounan;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server ("common") and client settings. */
public final class SounanConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    private static final ForgeConfigSpec.DoubleValue MONSTER_SPEED;
    private static final ForgeConfigSpec.DoubleValue FEAR_RATE;
    private static final ForgeConfigSpec.BooleanValue AUTO_START;

    private static final ForgeConfigSpec.BooleanValue FLASH_REDUCTION;
    private static final ForgeConfigSpec.BooleanValue JUMPSCARES;
    private static final ForgeConfigSpec.DoubleValue SCREEN_EFFECTS;
    private static final ForgeConfigSpec.BooleanValue INSTALL_SHADERS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("game");
        MONSTER_SPEED = b.comment("Speed multiplier of the monsters (0.7 = easier, 1.3 = harder)").defineInRange("monsterSpeed", 1.0, 0.5, 1.6);
        FEAR_RATE = b.comment("How fast the heart rate rises (0.5 = calmer)").defineInRange("fearRate", 1.0, 0.2, 2.0);
        AUTO_START = b.comment("Start the night on the mountain as soon as a player joins a new world (otherwise: /sounan start)")
                .define("autoStart", true);
        b.pop();
        COMMON_SPEC = b.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        c.push("accessibility");
        FLASH_REDUCTION = c.comment("Disable flashing, strobing and glitch effects (photosensitive players)").define("flashReduction", false);
        JUMPSCARES = c.comment("Show the full-screen jump scare images").define("jumpscares", true);
        SCREEN_EFFECTS = c.comment("Strength of the fear screen effects (vignette, grain, shake)").defineInRange("screenEffects", 1.0, 0.0, 1.5);
        c.pop();
        c.push("shaders");
        INSTALL_SHADERS = c.comment("Copy the bundled SounanShaders pack into shaderpacks/ (and select it in Oculus/Iris/OptiFine if none is selected)")
                .define("installShaderPack", true);
        c.pop();
        CLIENT_SPEC = c.build();
    }

    private SounanConfig() {}

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

    public static double monsterSpeed() {
        return d(MONSTER_SPEED, 1.0);
    }

    public static double fearRate() {
        return d(FEAR_RATE, 1.0);
    }

    public static boolean autoStart() {
        return b(AUTO_START, true);
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
