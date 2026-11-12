package com.eternalrealm.job;

import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatPercents;
import com.eternalrealm.combat.CombatModifiers;

import java.util.Objects;

/**
 * The final stats of a character and where they come from.
 *
 * <pre>
 * flat  = own (base + allocated) + job growth + equipment flat stats
 * total = min(9999, floor(flat × (1 + Σ stat percentages)))          (see {@link StatPercents})
 * </pre>
 *
 * @param total        what combat uses
 * @param jobBonus     stats from the job's growth
 * @param gearBonus    flat stats from equipment
 * @param percents     the stat percentages of the equipment
 * @param percentBonus what the percentages add on top of the flat stats ({@code total − flat})
 * @param modifiers    job passive + equipment + learned passive skills (secondary bonuses)
 */
public record ComposedStats(StatBlock total, StatBlock jobBonus, StatBlock gearBonus, StatPercents percents,
                            StatBlock percentBonus, CombatModifiers modifiers) {
    public ComposedStats {
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(jobBonus, "jobBonus");
        Objects.requireNonNull(gearBonus, "gearBonus");
        Objects.requireNonNull(percents, "percents");
        Objects.requireNonNull(percentBonus, "percentBonus");
        Objects.requireNonNull(modifiers, "modifiers");
    }

    /** Job, equipment and percentage stats together (shown as the "+" part on the character screen). */
    public StatBlock bonus() {
        return jobBonus.plus(gearBonus).plus(percentBonus);
    }

    /**
     * Combines a character's own stats (base + allocated) with the job at {@code level} and the equipment.
     */
    public static ComposedStats compose(StatBlock own, int level, JobType job, StatBlock gearStats,
                                        CombatModifiers gearModifiers) {
        return compose(own, level, job, gearStats, StatPercents.ZERO, gearModifiers, CombatModifiers.NONE);
    }

    /** As above, plus the secondary bonuses of the character's learned passive skills. */
    public static ComposedStats compose(StatBlock own, int level, JobType job, StatBlock gearStats,
                                        CombatModifiers gearModifiers, CombatModifiers skillModifiers) {
        return compose(own, level, job, gearStats, StatPercents.ZERO, gearModifiers, skillModifiers);
    }

    /** As above, with the stat percentages of the equipment applied after every flat source. */
    public static ComposedStats compose(StatBlock own, int level, JobType job, StatBlock gearStats,
                                        StatPercents gearPercents, CombatModifiers gearModifiers,
                                        CombatModifiers skillModifiers) {
        Objects.requireNonNull(skillModifiers, "skillModifiers");
        Objects.requireNonNull(own, "own");
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(gearStats, "gearStats");
        Objects.requireNonNull(gearPercents, "gearPercents");
        Objects.requireNonNull(gearModifiers, "gearModifiers");
        JobDefinition definition = Jobs.get(job);
        StatBlock jobBonus = definition.bonusStats(level);
        StatBlock flat = own.plus(jobBonus).plus(gearStats);
        StatBlock percentBonus = gearPercents.bonusOn(flat);
        return new ComposedStats(flat.plus(percentBonus), jobBonus, gearStats, gearPercents, percentBonus,
                definition.passive().plus(gearModifiers).plus(skillModifiers));
    }
}
