package com.frostbound.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Settings (config/frostbound-common.toml). */
public final class FrostboundConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue PAUSE_IN_MANAGEMENT;
    public static final ForgeConfigSpec.BooleanValue NPC_ENABLED;
    public static final ForgeConfigSpec.IntValue NPC_PER_PLAYER;
    public static final ForgeConfigSpec.IntValue NPC_RADIUS;
    public static final ForgeConfigSpec.IntValue DAILY_BACKUPS;
    public static final ForgeConfigSpec.BooleanValue AUTO_OPEN_EVENTS;
    public static final ForgeConfigSpec.IntValue DEFAULT_DAY_LENGTH;
    public static final ForgeConfigSpec.ConfigValue<String> SERVER_DIFFICULTY;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("game");
        PAUSE_IN_MANAGEMENT = b.comment("Pause city time while the management screen is open (single player, or when every player is managing).",
                "Off by default: the management screen has its own pause and speed buttons.")
                .define("pauseInManagement", false);
        AUTO_OPEN_EVENTS = b.comment("Open the event dialog automatically when an event happens").define("autoOpenEvents", true);
        DEFAULT_DAY_LENGTH = b.comment("Default real-time minutes per game day for new cities").defineInRange("defaultDayLength", 20, 5, 120);
        SERVER_DIFFICULTY = b.comment("Difficulty used when a dedicated server founds the city automatically (EASY, NORMAL, HARD)")
                .define("serverDifficulty", "NORMAL");
        b.pop();
        b.push("performance");
        NPC_ENABLED = b.comment("Show citizens as NPCs near players").define("npcEnabled", true);
        NPC_PER_PLAYER = b.comment("Maximum citizen NPCs around one player").defineInRange("npcPerPlayer", 40, 0, 200);
        NPC_RADIUS = b.comment("Radius around a player in which citizens appear as NPCs").defineInRange("npcRadius", 64, 16, 160);
        b.pop();
        b.push("save");
        DAILY_BACKUPS = b.comment("Number of daily backups of the city file to keep").defineInRange("dailyBackups", 3, 0, 20);
        b.pop();
        SPEC = b.build();
    }

    private FrostboundConfig() {
    }
}
