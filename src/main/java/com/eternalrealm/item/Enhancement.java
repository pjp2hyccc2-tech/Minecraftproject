package com.eternalrealm.item;

import com.eternalrealm.core.ModInfo;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * 強化: raises a piece's enhancement level by one, which adds {@value EquipmentStats#ENHANCEMENT_BONUS_PER_LEVEL} of its
 * base attack / defense / max health per level ({@link EquipmentStats}).
 *
 * <pre>
 * success chance (target level): +1..+3 100 %, +4 90 %, +5 80 %, +6 70 %, +7 60 %, +8 50 %, +9 40 %, +10 30 %
 * cost per attempt (target level L): L enhancement stones + L × gold per level of the tier (10, 25, 50, 90, 140, 200, 280)
 * </pre>
 *
 * <p>A failure consumes the cost and changes nothing else: the piece is never destroyed and never loses levels.
 * Equipment is never lost in this game (docs/GAME_DESIGN.md §11) and a 14-day season leaves no time to rebuild lost
 * levels; the falling chance alone makes +10 a long-term goal (on average 16.3 attempts and 109 stones from +0).</p>
 *
 * <p>Order of use on the server: {@link #check} → remove {@link #cost} → {@link #attempt} → write the returned piece
 * back to the stack, all in one tick.</p>
 */
public final class Enhancement {
    /** Index = target level. */
    private static final double[] SUCCESS_CHANCE = {0.0, 1.0, 1.0, 1.0, 0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3};
    /** Index = tier number − 1. */
    private static final long[] GOLD_PER_LEVEL = {10, 25, 50, 90, 140, 200, 280};

    /** Whether an attempt may start. */
    public enum Check {
        OK,
        MAX_LEVEL,
        MISSING_STONES,
        NOT_ENOUGH_GOLD;

        public String translationKey() {
            return "message." + ModInfo.MOD_ID + ".enhancement." + name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Outcome {
        SUCCESS,
        /** The cost is gone, the piece is unchanged. */
        FAILURE,
        /** Nothing happened (no cost should have been taken: {@link #check} reports {@link Check#MAX_LEVEL}). */
        MAX_LEVEL;

        public String translationKey() {
            return "message." + ModInfo.MOD_ID + ".enhancement." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** @param item the piece after the attempt (the input itself unless it succeeded) */
    public record Result(Outcome outcome, EquipmentData item) {
        public Result {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(item, "item");
        }
    }

    private Enhancement() {
    }

    /** Chance that an attempt to reach {@code targetLevel} (1..10) succeeds. */
    public static double successChance(int targetLevel) {
        return SUCCESS_CHANCE[Checks.range("targetLevel", targetLevel, 1, EquipmentData.MAX_ENHANCEMENT)];
    }

    public static boolean isMaxed(EquipmentData data) {
        return data.enhancement() >= EquipmentData.MAX_ENHANCEMENT;
    }

    /** Gold per target level of {@code tier}. */
    public static long goldPerLevel(ItemTier tier) {
        return GOLD_PER_LEVEL[Objects.requireNonNull(tier, "tier").number() - 1];
    }

    /**
     * Cost of the next attempt.
     *
     * @throws IllegalArgumentException if the piece is already at {@link EquipmentData#MAX_ENHANCEMENT}
     */
    public static Cost cost(EquipmentData data) {
        Objects.requireNonNull(data, "data");
        if (isMaxed(data)) {
            throw new IllegalArgumentException("Already at +" + EquipmentData.MAX_ENHANCEMENT);
        }
        int target = data.enhancement() + 1;
        return Cost.of(goldPerLevel(data.tier()) * target, Materials.ENHANCEMENT_STONE, target);
    }

    /**
     * Whether the next attempt may start with the given inventory (item id → count) and gold. Checked in the order
     * max level, stones, gold; the first failure is reported.
     */
    public static Check check(EquipmentData data, Map<String, Integer> inventory, long gold) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(inventory, "inventory");
        Cost.checkGold(gold);
        if (isMaxed(data)) {
            return Check.MAX_LEVEL;
        }
        Cost cost = cost(data);
        if (!cost.missing(inventory).isEmpty()) {
            return Check.MISSING_STONES;
        }
        return gold < cost.gold() ? Check.NOT_ENOUGH_GOLD : Check.OK;
    }

    /**
     * One attempt (the caller has already removed the cost). Draws exactly one {@code nextDouble} unless the piece is
     * maxed, so a seeded generator replays the same outcomes.
     */
    public static Result attempt(EquipmentData data, RandomGenerator random) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(random, "random");
        if (isMaxed(data)) {
            return new Result(Outcome.MAX_LEVEL, data);
        }
        int target = data.enhancement() + 1;
        if (random.nextDouble() < successChance(target)) {
            return new Result(Outcome.SUCCESS, data.withEnhancement(target));
        }
        return new Result(Outcome.FAILURE, data);
    }

    /** Expected number of attempts from {@code from} to {@code to} (for UIs and balance checks). */
    public static double expectedAttempts(int from, int to) {
        Checks.range("from", from, 0, EquipmentData.MAX_ENHANCEMENT);
        Checks.range("to", to, from, EquipmentData.MAX_ENHANCEMENT);
        double attempts = 0.0;
        for (int target = from + 1; target <= to; target++) {
            attempts += 1.0 / successChance(target);
        }
        return attempts;
    }
}
