package com.eternalrealm.item;

import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatPercents;
import com.eternalrealm.combat.CombatModifiers;

import java.util.Objects;

/**
 * What equipment adds to a character, in the shape the job module composes
 * ({@code ComposedStats.compose(own, level, job, bonus.stats(), bonus.percents(), bonus.modifiers(), …)}).
 *
 * @param stats     primary stats from affixes (flat)
 * @param percents  primary stat percentages (percent affixes and trinkets), applied after every flat source
 * @param modifiers armor defense and max health plus secondary affixes
 */
public record GearBonus(StatBlock stats, StatPercents percents, CombatModifiers modifiers) {
    public static final GearBonus NONE = new GearBonus(StatBlock.ZERO, StatPercents.ZERO, CombatModifiers.NONE);

    public GearBonus {
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(percents, "percents");
        Objects.requireNonNull(modifiers, "modifiers");
    }

    /** A bonus without stat percentages. */
    public GearBonus(StatBlock stats, CombatModifiers modifiers) {
        this(stats, StatPercents.ZERO, modifiers);
    }

    /** Both bonuses together (stats and percentages saturate, modifiers are clamped by {@link CombatModifiers#plus}). */
    public GearBonus plus(GearBonus other) {
        return new GearBonus(stats.plus(other.stats), percents.plus(other.percents), modifiers.plus(other.modifiers));
    }
}
