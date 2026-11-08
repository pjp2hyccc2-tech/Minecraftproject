package com.eternalrealm.config;

import com.eternalrealm.character.CharacterRules;
import com.eternalrealm.character.PowerExperienceCurve;
import com.eternalrealm.season.SeasonId;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Per-world server configuration ({@code <world>/serverconfig/eternalrealm-server.toml}).
 * Values documented as "restart required" are read once when the server starts; the others are re-applied when the
 * file is reloaded (see ServerBootstrap#onConfigReloading).
 */
public final class ERServerConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue LEVEL_CAP;
    public static final ForgeConfigSpec.IntValue STAT_POINTS_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue BASE_STAT_VALUE;
    public static final ForgeConfigSpec.IntValue MAX_POINTS_PER_REQUEST;
    public static final ForgeConfigSpec.LongValue EXP_CURVE_BASE;
    public static final ForgeConfigSpec.DoubleValue EXP_CURVE_EXPONENT;

    public static final ForgeConfigSpec.BooleanValue MOB_KILL_EXP_ENABLED;
    public static final ForgeConfigSpec.DoubleValue MOB_KILL_EXP_PER_MAX_HEALTH;

    public static final ForgeConfigSpec.ConfigValue<String> SEASON_ID;

    public static final ForgeConfigSpec.IntValue AUTOSAVE_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.IntValue IO_THREADS;
    public static final ForgeConfigSpec.IntValue SHUTDOWN_FLUSH_TIMEOUT_SECONDS;

    public static final ForgeConfigSpec.IntValue BACKUP_INTERVAL_MINUTES;
    public static final ForgeConfigSpec.IntValue BACKUP_KEEP;
    public static final ForgeConfigSpec.BooleanValue BACKUP_ON_SHUTDOWN;

    public static final ForgeConfigSpec.IntValue ALLOCATE_BURST;
    public static final ForgeConfigSpec.DoubleValue ALLOCATE_PER_SECOND;
    public static final ForgeConfigSpec.IntValue VIOLATION_KICK_THRESHOLD;
    public static final ForgeConfigSpec.IntValue VIOLATION_WINDOW_SECONDS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Character progression. Reloadable: online characters are re-validated against new values.")
                .push("character");
        LEVEL_CAP = builder.comment("Maximum level of the current season. Initial season: 50. Hard limit: 100.")
                .defineInRange("levelCap", 50, 1, CharacterRules.MAX_LEVEL);
        STAT_POINTS_PER_LEVEL = builder.comment("Stat points granted per level gained.")
                .defineInRange("statPointsPerLevel", 3, 0, CharacterRules.MAX_STAT_POINTS_PER_LEVEL);
        BASE_STAT_VALUE = builder.comment("Starting value of every stat (STR/VIT/AGI/INT/DEX/LUK).")
                .defineInRange("baseStatValue", 5, 0, CharacterRules.MAX_BASE_STAT);
        MAX_POINTS_PER_REQUEST = builder.comment("Maximum stat points a single allocation request may spend.")
                .defineInRange("maxPointsPerRequest", 10, 1, CharacterRules.MAX_POINTS_PER_REQUEST);
        builder.comment("EXP to next level = round(curveBase * level ^ curveExponent)").push("experience");
        EXP_CURVE_BASE = builder.defineInRange("curveBase", 100L, PowerExperienceCurve.MIN_BASE, PowerExperienceCurve.MAX_BASE);
        EXP_CURVE_EXPONENT = builder.defineInRange("curveExponent", 1.5, PowerExperienceCurve.MIN_EXPONENT, PowerExperienceCurve.MAX_EXPONENT);
        builder.pop();
        builder.comment("INTERIM experience source until the monster system exists:",
                        "killing a hostile vanilla mob grants round(maxHealth * expPerMaxHealth) EXP.")
                .push("interimMobKillExp");
        MOB_KILL_EXP_ENABLED = builder.define("enabled", true);
        MOB_KILL_EXP_PER_MAX_HEALTH = builder.defineInRange("expPerMaxHealth", 1.0, 0.0, 100.0);
        builder.pop();
        builder.pop();

        builder.comment("Season. Restart required.").push("season");
        SEASON_ID = builder.comment("Identifier of the current season: lower-case letters, digits, '_' or '-', max 32 characters.")
                .define("currentSeasonId", "s1", value -> value instanceof String text && SeasonId.isValid(text));
        builder.pop();

        builder.comment("Persistence").push("persistence");
        AUTOSAVE_INTERVAL_SECONDS = builder.comment("Interval between autosaves of changed characters (reloadable).")
                .defineInRange("autosaveIntervalSeconds", 60, 10, 3600);
        IO_THREADS = builder.comment("Threads for blocking storage I/O. Restart required.")
                .defineInRange("ioThreads", 2, 1, 8);
        SHUTDOWN_FLUSH_TIMEOUT_SECONDS = builder.comment("How long shutdown waits for pending saves.")
                .defineInRange("shutdownFlushTimeoutSeconds", 30, 5, 600);
        builder.pop();

        builder.comment("Zip snapshots of the data directory, stored in <world>/eternalrealm_backups").push("backup");
        BACKUP_INTERVAL_MINUTES = builder.comment("Minutes between automatic snapshots; 0 disables them (reloadable).")
                .defineInRange("intervalMinutes", 30, 0, 1440);
        BACKUP_KEEP = builder.comment("Number of snapshots to keep. Restart required.")
                .defineInRange("keepSnapshots", 48, 1, 1000);
        BACKUP_ON_SHUTDOWN = builder.comment("Create a snapshot after the final flush on shutdown.")
                .define("snapshotOnShutdown", true);
        builder.pop();

        builder.comment("Client request limits. Restart required.").push("network");
        ALLOCATE_BURST = builder.defineInRange("allocateStatBurst", 10, 1, 100);
        ALLOCATE_PER_SECOND = builder.defineInRange("allocateStatPerSecond", 5.0, 0.1, 100.0);
        VIOLATION_KICK_THRESHOLD = builder.comment("Protocol violations within the window that disconnect a player; 0 = never.")
                .defineInRange("violationKickThreshold", 20, 0, 10_000);
        VIOLATION_WINDOW_SECONDS = builder.defineInRange("violationWindowSeconds", 60, 1, 3600);
        builder.pop();

        SPEC = builder.build();
    }

    private ERServerConfig() {
    }

    public static CharacterRules characterRules() {
        return new CharacterRules(LEVEL_CAP.get(), STAT_POINTS_PER_LEVEL.get(), BASE_STAT_VALUE.get(),
                MAX_POINTS_PER_REQUEST.get(), new PowerExperienceCurve(EXP_CURVE_BASE.get(), EXP_CURVE_EXPONENT.get()));
    }

    public static SeasonId seasonId() {
        return SeasonId.of(SEASON_ID.get());
    }
}
