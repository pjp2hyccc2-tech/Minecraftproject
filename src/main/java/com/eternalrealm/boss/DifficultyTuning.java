package com.eternalrealm.boss;

import java.util.HashSet;
import java.util.List;

/**
 * How one difficulty changes one boss.
 *
 * @param healthMultiplier   applied on top of the party-scaled health
 * @param damageMultiplier   applied to every hit (melee and abilities)
 * @param cooldownMultiplier applied to per-ability and global cooldowns; below 1 means the boss acts more often
 * @param extraAbilityIds    abilities added to the rotation of every phase (EX uses this for its extra mechanic)
 */
public record DifficultyTuning(double healthMultiplier, double damageMultiplier, double cooldownMultiplier,
                               List<String> extraAbilityIds) {
    /** No change: what every boss uses on {@link Difficulty#NORMAL}. */
    public static final DifficultyTuning NORMAL = new DifficultyTuning(1.0, 1.0, 1.0, List.of());

    public DifficultyTuning {
        Check.range("healthMultiplier", healthMultiplier, 0.1, 10.0);
        Check.range("damageMultiplier", damageMultiplier, 0.1, 10.0);
        Check.range("cooldownMultiplier", cooldownMultiplier, 0.25, 4.0);
        extraAbilityIds = List.copyOf(extraAbilityIds);
        for (String id : extraAbilityIds) {
            Check.id("extraAbilityId", id);
        }
        if (new HashSet<>(extraAbilityIds).size() != extraAbilityIds.size()) {
            throw new IllegalArgumentException("duplicate extra ability ids: " + extraAbilityIds);
        }
    }

    /** Standard HARD numbers: +40% health, +30% damage, 10% shorter cooldowns. */
    public static DifficultyTuning hard() {
        return new DifficultyTuning(1.4, 1.3, 0.9, List.of());
    }

    /** Standard EX numbers (double health, +60% damage, 20% shorter cooldowns) plus the given extra abilities. */
    public static DifficultyTuning ex(String... extraAbilityIds) {
        return new DifficultyTuning(2.0, 1.6, 0.8, List.of(extraAbilityIds));
    }
}
