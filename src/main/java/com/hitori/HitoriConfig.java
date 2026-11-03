package com.hitori;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server ("common") and client settings. */
public final class HitoriConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    private static final ForgeConfigSpec.DoubleValue DOLL_SPEED;
    private static final ForgeConfigSpec.DoubleValue AWARENESS_RATE;
    private static final ForgeConfigSpec.BooleanValue AUTO_START;
    private static final ForgeConfigSpec.DoubleValue PREP_CLOCK;
    private static final ForgeConfigSpec.DoubleValue NIGHT_CLOCK;
    private static final ForgeConfigSpec.DoubleValue MOUTH_MINUTES;

    private static final ForgeConfigSpec.BooleanValue FLASH_REDUCTION;
    private static final ForgeConfigSpec.BooleanValue JUMPSCARES;
    private static final ForgeConfigSpec.DoubleValue SCREEN_EFFECTS;
    private static final ForgeConfigSpec.BooleanValue INSTALL_SHADERS;
    private static final ForgeConfigSpec.BooleanValue ACOUSTICS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("game");
        DOLL_SPEED = b.comment("Speed multiplier of the doll when it searches and hunts (0.7 = easier, 1.3 = harder)").defineInRange("dollSpeed", 1.0, 0.5, 1.6);
        AWARENESS_RATE = b.comment("How fast the doll grows suspicious of your hiding place (0.5 = easier, 1.5 = harder)").defineInRange("awarenessRate", 1.0, 0.2, 2.0);
        AUTO_START = b.comment("Start the night in apartment 203 as soon as a player joins a new world (otherwise: /hitori start)")
                .define("autoStart", true);
        PREP_CLOCK = b.comment("How fast the night's clock runs before 3:00 (in-game seconds per real second; 02:20 -> 03:00)")
                .defineInRange("prepClockRate", 4.0, 1.0, 30.0);
        NIGHT_CLOCK = b.comment("How fast the night's clock runs from 3:00 (dawn at 5:00 ends an unfinished ritual)")
                .defineInRange("nightClockRate", 2.0, 1.0, 20.0);
        MOUTH_MINUTES = b.comment("Real minutes a mouthful of salt water lasts (holding your breath drains it three times faster)")
                .defineInRange("mouthMinutes", 10.0, 1.0, 120.0);
        b.pop();
        COMMON_SPEC = b.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        c.push("accessibility");
        FLASH_REDUCTION = c.comment("Disable flashing, strobing and glitch effects (photosensitive players)").define("flashReduction", false);
        JUMPSCARES = c.comment("Show the full-screen jump scare images").define("jumpscares", true);
        SCREEN_EFFECTS = c.comment("Strength of the fear screen effects (vignette, grain, shake)").defineInRange("screenEffects", 1.0, 0.0, 1.5);
        c.pop();
        c.push("shaders");
        INSTALL_SHADERS = c.comment("Copy the bundled HitoriShaders pack into shaderpacks/ (and select it in Oculus/Iris/OptiFine if none is selected)")
                .define("installShaderPack", true);
        c.pop();
        c.push("sound");
        ACOUSTICS = c.comment("Room reverb and occlusion (OpenAL EFX): sounds behind walls are muffled, the bathroom rings, the closet is close")
                .define("acoustics", true);
        c.pop();
        CLIENT_SPEC = c.build();
    }

    private HitoriConfig() {}

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

    public static double dollSpeed() { return d(DOLL_SPEED, 1.0); }
    public static double awarenessRate() { return d(AWARENESS_RATE, 1.0); }
    public static boolean autoStart() { return b(AUTO_START, true); }
    public static double prepClockRate() { return d(PREP_CLOCK, 4.0); }
    public static double nightClockRate() { return d(NIGHT_CLOCK, 2.0); }
    public static double mouthMinutes() { return d(MOUTH_MINUTES, 10.0); }
    public static boolean flashReduction() { return b(FLASH_REDUCTION, false); }
    public static boolean jumpscares() { return b(JUMPSCARES, true); }
    public static double screenEffects() { return d(SCREEN_EFFECTS, 1.0); }
    public static boolean installShaders() { return b(INSTALL_SHADERS, true); }
    public static boolean acoustics() { return b(ACOUSTICS, true); }
}
