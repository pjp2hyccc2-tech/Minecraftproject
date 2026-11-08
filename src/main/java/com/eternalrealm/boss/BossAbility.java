package com.eternalrealm.boss;

import com.eternalrealm.core.ModInfo;

import java.util.Objects;

/**
 * One boss attack or action. Timeline of a use that starts at tick {@code t}:
 * <pre>
 *   t                      telegraph shapes are placed (players see them and can move out)
 *   t + telegraphTicks     execution (damage, summon, teleport, ...) with exactly the shapes shown
 *   ...                    patterns of several hits ({@link AbilityKind#extraTicks()}) place and hit their later
 *                          waves, each with its own warning
 *   t + telegraphTicks + extraTicks + castTicks   the boss is free again; the phase's global cooldown starts
 * </pre>
 * While an ability runs the boss should stand still (the platform pauses its vanilla movement and melee).
 *
 * @param id               globally unique, by convention prefixed with the boss id (e.g. {@code b1_cleave})
 * @param telegraphTicks   warning before the execution; at least {@link AbilityKind#minTelegraphTicks()}
 * @param castTicks        recovery after the execution during which the boss does nothing else
 * @param cooldownTicks    earliest re-use, counted from the start of the use
 * @param damageMultiplier multiplier on the boss's base damage; 0 for non-damaging kinds
 * @param weight           relative chance to be picked among the ready abilities
 * @param targetRule       who the ability aims at (ignored by kinds that do not aim)
 */
public record BossAbility(String id, AbilityKind kind, int telegraphTicks, int castTicks, int cooldownTicks,
                          double damageMultiplier, int weight, TargetRule targetRule) {
    public static final int MAX_TICKS = 400;

    public BossAbility {
        Check.id("id", id);
        Objects.requireNonNull(kind, "kind");
        Check.range(id + ": telegraphTicks", telegraphTicks, kind.minTelegraphTicks(), MAX_TICKS);
        Check.range(id + ": castTicks", castTicks, 0, MAX_TICKS);
        Check.range(id + ": cooldownTicks", cooldownTicks, 0, 20 * 600);
        if (kind.damaging()) {
            Check.range(id + ": damageMultiplier", damageMultiplier, 0.05, 50.0);
        } else if (damageMultiplier != 0.0) {
            throw new IllegalArgumentException(id + ": non-damaging abilities must have damageMultiplier 0");
        }
        Check.range(id + ": weight", weight, 1, 1000);
        Objects.requireNonNull(targetRule, "targetRule");
    }

    public String translationKey() {
        return "boss_ability." + ModInfo.MOD_ID + "." + id;
    }

    /** Ticks from the start of a use until the boss is free again. */
    public int busyTicks() {
        return lastHitTicks() + castTicks;
    }

    /** Ticks from the start of a use to its last hit (the first hit for single-hit kinds). */
    public int lastHitTicks() {
        return telegraphTicks + kind.extraTicks();
    }
}
