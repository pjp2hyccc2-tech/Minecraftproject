package com.birdiecraft.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Mod configuration (common = gameplay rules, client = presentation). */
public final class GolfConfig {
    public enum BallCamMode {
        OFF, MINE, ALL;

        public BallCamMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec.IntValue TURN_TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_STROKES_OVER_PAR;
    public static final ForgeConfigSpec.BooleanValue NATURAL_GROUND_IS_OB;
    public static final ForgeConfigSpec.BooleanValue TELEPORT_TO_BALL;
    public static final ForgeConfigSpec.IntValue BUILD_COLUMNS_PER_TICK;
    public static final ForgeConfigSpec.BooleanValue ALLOW_BUILD_WITHOUT_OP;
    public static final ForgeConfigSpec.DoubleValue WIND_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue POWER_SHOTS_PER_ROUND;
    public static final ForgeConfigSpec.IntValue MAX_PLAYERS_PER_MATCH;

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.EnumValue<BallCamMode> BALL_CAM;
    public static final ForgeConfigSpec.BooleanValue SHOW_LANDING_PREVIEW;
    public static final ForgeConfigSpec.BooleanValue SHOW_PIN_BEACON;
    public static final ForgeConfigSpec.BooleanValue SHOW_HOLE_MAP;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("rules");
        TURN_TIMEOUT_SECONDS = b.comment("Seconds a player has for a shot in multiplayer before the CPU plays it for them.")
                .defineInRange("turnTimeoutSeconds", 90, 15, 600);
        MAX_STROKES_OVER_PAR = b.comment("A hole ends automatically after par + this many strokes.")
                .defineInRange("maxStrokesOverPar", 5, 2, 12);
        NATURAL_GROUND_IS_OB = b.comment("If true, a ball that stops on non-golf blocks (vanilla grass, stone...) is out of bounds.")
                .define("naturalGroundIsOB", true);
        TELEPORT_TO_BALL = b.comment("Teleport players to their ball when it is their turn.")
                .define("teleportToBall", true);
        WIND_MULTIPLIER = b.comment("Global wind strength multiplier.")
                .defineInRange("windMultiplier", 1.0, 0.0, 3.0);
        POWER_SHOTS_PER_ROUND = b.comment("Power shot charges each golfer starts a round with (perfect impacts earn more).")
                .defineInRange("powerShotsPerRound", 3, 0, 10);
        MAX_PLAYERS_PER_MATCH = b.comment("Maximum golfers (players + CPUs) in one match.")
                .defineInRange("maxPlayersPerMatch", 8, 2, 16);
        b.pop();
        b.push("building");
        BUILD_COLUMNS_PER_TICK = b.comment("How many terrain columns the course builder processes per server tick.")
                .defineInRange("columnsPerTick", 900, 50, 20000);
        ALLOW_BUILD_WITHOUT_OP = b.comment("Allow non-operators to build preset / generated courses.")
                .define("allowBuildWithoutOp", false);
        b.pop();
        COMMON_SPEC = b.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        BALL_CAM = c.comment("Ball camera: OFF, MINE (follow your own shots) or ALL (follow every shot).")
                .defineEnum("ballCam", BallCamMode.MINE);
        SHOW_LANDING_PREVIEW = c.comment("Show the landing point marker while aiming.")
                .define("showLandingPreview", true);
        SHOW_PIN_BEACON = c.comment("Show a light beam above the cup.")
                .define("showPinBeacon", true);
        SHOW_HOLE_MAP = c.comment("Show the hole overview map during matches.")
                .define("showHoleMap", true);
        CLIENT_SPEC = c.build();
    }

    private GolfConfig() {
    }
}
