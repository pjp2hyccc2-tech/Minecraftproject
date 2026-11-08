package com.eternalrealm.config;

import com.eternalrealm.character.CharacterRules;
import com.eternalrealm.character.PowerExperienceCurve;
import com.eternalrealm.combat.CombatTuning;
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
    public static final ForgeConfigSpec.IntValue FLOOD_KICK_THRESHOLD;
    public static final ForgeConfigSpec.IntValue COMBAT_ACTION_BURST;
    public static final ForgeConfigSpec.DoubleValue COMBAT_ACTION_PER_SECOND;

    public static final ForgeConfigSpec.DoubleValue ATTACK_PER_STR;
    public static final ForgeConfigSpec.DoubleValue HEALTH_PER_VIT;
    public static final ForgeConfigSpec.DoubleValue HEALTH_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue DEFENSE_PER_VIT;
    public static final ForgeConfigSpec.DoubleValue DEFENSE_CONSTANT;
    public static final ForgeConfigSpec.DoubleValue MAX_DAMAGE_REDUCTION;
    public static final ForgeConfigSpec.DoubleValue SPEED_PER_AGI;
    public static final ForgeConfigSpec.DoubleValue MIN_ACTION_TIME_FACTOR;
    public static final ForgeConfigSpec.DoubleValue BASE_CRIT_CHANCE;
    public static final ForgeConfigSpec.DoubleValue CRIT_PER_LUK;
    public static final ForgeConfigSpec.DoubleValue MAX_CRIT_CHANCE;
    public static final ForgeConfigSpec.DoubleValue CRIT_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue BASE_STAMINA;
    public static final ForgeConfigSpec.DoubleValue STAMINA_PER_AGI;
    public static final ForgeConfigSpec.DoubleValue BASE_STAMINA_REGEN;
    public static final ForgeConfigSpec.DoubleValue STAMINA_REGEN_PER_AGI;

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
        FLOOD_KICK_THRESHOLD = builder.comment("Requests dropped by the rate limits within the window that disconnect a",
                        "player; 0 = never. Rate-limited requests are NOT protocol violations (fast clicking is legitimate).")
                .defineInRange("floodKickThreshold", 300, 0, 100_000);
        COMBAT_ACTION_BURST = builder.comment("Combat actions (attack, dodge, skill...) a client may send in a burst.")
                .defineInRange("combatActionBurst", 20, 1, 200);
        COMBAT_ACTION_PER_SECOND = builder.comment("Sustained combat actions per second.")
                .defineInRange("combatActionPerSecond", 20.0, 1.0, 200.0);
        builder.pop();

        builder.comment("How character stats turn into combat values (docs/COMBAT_SYSTEM.md). Reloadable.",
                        "Weapon timings and damage multipliers are fixed in code so clients can predict them.")
                .push("combat");
        CombatTuning d = CombatTuning.defaults();
        ATTACK_PER_STR = builder.comment("Damage multiplier = 1 + STR * attackPerStr")
                .defineInRange("attackPerStr", d.attackPerStr(), 0.0, 1.0);
        HEALTH_PER_VIT = builder.comment("Max health bonus = VIT * healthPerVit + (level - 1) * healthPerLevel")
                .defineInRange("healthPerVit", d.healthPerVit(), 0.0, 10.0);
        HEALTH_PER_LEVEL = builder.defineInRange("healthPerLevel", d.healthPerLevel(), 0.0, 20.0);
        DEFENSE_PER_VIT = builder.comment("Damage reduction vs attackers = def / (def + defenseConstant), def = VIT * defensePerVit")
                .defineInRange("defensePerVit", d.defensePerVit(), 0.0, 10.0);
        DEFENSE_CONSTANT = builder.defineInRange("defenseConstant", d.defenseConstant(), 1.0, 10_000.0);
        MAX_DAMAGE_REDUCTION = builder.defineInRange("maxDamageReduction", d.maxDamageReduction(), 0.0, 0.95);
        SPEED_PER_AGI = builder.comment("Light/heavy attack time factor = max(minActionTimeFactor, 1 / (1 + AGI * speedPerAgi))")
                .defineInRange("speedPerAgi", d.speedPerAgi(), 0.0, 0.1);
        MIN_ACTION_TIME_FACTOR = builder.defineInRange("minActionTimeFactor", d.minActionTimeFactor(), 0.2, 1.0);
        BASE_CRIT_CHANCE = builder.comment("Critical chance = min(maxCritChance, baseCritChance + LUK * critPerLuk) + weapon bonus")
                .defineInRange("baseCritChance", d.baseCritChance(), 0.0, 1.0);
        CRIT_PER_LUK = builder.defineInRange("critPerLuk", d.critPerLuk(), 0.0, 0.1);
        MAX_CRIT_CHANCE = builder.defineInRange("maxCritChance", d.maxCritChance(), 0.0, 1.0);
        CRIT_MULTIPLIER = builder.defineInRange("critMultiplier", d.critMultiplier(), 1.0, 10.0);
        BASE_STAMINA = builder.comment("Stamina = baseStamina + AGI * staminaPerAgi; regeneration per tick = baseStaminaRegen + AGI * staminaRegenPerAgi")
                .defineInRange("baseStamina", d.baseStamina(), 10.0, 10_000.0);
        STAMINA_PER_AGI = builder.defineInRange("staminaPerAgi", d.staminaPerAgi(), 0.0, 100.0);
        BASE_STAMINA_REGEN = builder.defineInRange("baseStaminaRegen", d.baseStaminaRegen(), 0.0, 100.0);
        STAMINA_REGEN_PER_AGI = builder.defineInRange("staminaRegenPerAgi", d.staminaRegenPerAgi(), 0.0, 10.0);
        builder.pop();

        SPEC = builder.build();
    }

    private ERServerConfig() {
    }

    public static CharacterRules characterRules() {
        return new CharacterRules(LEVEL_CAP.get(), STAT_POINTS_PER_LEVEL.get(), BASE_STAT_VALUE.get(),
                MAX_POINTS_PER_REQUEST.get(), new PowerExperienceCurve(EXP_CURVE_BASE.get(), EXP_CURVE_EXPONENT.get()));
    }

    public static CombatTuning combatTuning() {
        return new CombatTuning(ATTACK_PER_STR.get(), HEALTH_PER_VIT.get(), HEALTH_PER_LEVEL.get(), DEFENSE_PER_VIT.get(),
                DEFENSE_CONSTANT.get(), MAX_DAMAGE_REDUCTION.get(), SPEED_PER_AGI.get(), MIN_ACTION_TIME_FACTOR.get(),
                BASE_CRIT_CHANCE.get(), CRIT_PER_LUK.get(), MAX_CRIT_CHANCE.get(), CRIT_MULTIPLIER.get(),
                BASE_STAMINA.get(), STAMINA_PER_AGI.get(), BASE_STAMINA_REGEN.get(), STAMINA_REGEN_PER_AGI.get());
    }

    public static SeasonId seasonId() {
        return SeasonId.of(SEASON_ID.get());
    }
}
