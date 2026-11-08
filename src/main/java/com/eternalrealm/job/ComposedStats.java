package com.eternalrealm.job;

import com.eternalrealm.character.StatBlock;
import com.eternalrealm.combat.CombatModifiers;

import java.util.Objects;

/**
 * The final stats of a character and where they come from.
 *
 * @param total     base + allocated + job + equipment (what combat uses)
 * @param jobBonus  stats from the job's growth
 * @param gearBonus stats from equipment
 * @param modifiers job passive + equipment secondary bonuses
 */
public record ComposedStats(StatBlock total, StatBlock jobBonus, StatBlock gearBonus, CombatModifiers modifiers) {
    public ComposedStats {
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(jobBonus, "jobBonus");
        Objects.requireNonNull(gearBonus, "gearBonus");
        Objects.requireNonNull(modifiers, "modifiers");
    }

    /** Job and equipment stats together (shown as the "+" part on the character screen). */
    public StatBlock bonus() {
        return jobBonus.plus(gearBonus);
    }

    /**
     * Combines a character's own stats (base + allocated) with the job at {@code level} and the equipment.
     */
    public static ComposedStats compose(StatBlock own, int level, JobType job, StatBlock gearStats,
                                        CombatModifiers gearModifiers) {
        Objects.requireNonNull(own, "own");
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(gearStats, "gearStats");
        Objects.requireNonNull(gearModifiers, "gearModifiers");
        JobDefinition definition = Jobs.get(job);
        StatBlock jobBonus = definition.bonusStats(level);
        return new ComposedStats(own.plus(jobBonus).plus(gearStats), jobBonus, gearStats,
                definition.passive().plus(gearModifiers));
    }
}
