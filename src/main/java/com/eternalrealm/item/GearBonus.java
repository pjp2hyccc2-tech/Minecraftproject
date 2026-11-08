package com.eternalrealm.item;

import com.eternalrealm.character.StatBlock;
import com.eternalrealm.combat.CombatModifiers;

import java.util.Objects;

/**
 * What equipment adds to a character, in the shape the job module composes
 * ({@code ComposedStats.compose(own, level, job, bonus.stats(), bonus.modifiers())}).
 *
 * @param stats     primary stats from affixes
 * @param modifiers armor defense and max health plus secondary affixes
 */
public record GearBonus(StatBlock stats, CombatModifiers modifiers) {
    public static final GearBonus NONE = new GearBonus(StatBlock.ZERO, CombatModifiers.NONE);

    public GearBonus {
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(modifiers, "modifiers");
    }

    /** Both bonuses together (stats saturate, modifiers are clamped by {@link CombatModifiers#plus}). */
    public GearBonus plus(GearBonus other) {
        return new GearBonus(stats.plus(other.stats), modifiers.plus(other.modifiers));
    }
}
