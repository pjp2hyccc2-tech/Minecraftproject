package com.eternalrealm.combat;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * A job skill placed in one of a fighter's job skill slots, with the values that depend on the learned skill level.
 * Built by the job module from the same data on the server and the owning client, so both predict the same timings.
 *
 * @param action        timing and hit frames ({@link ActionKind#SKILL})
 * @param weapons       weapon types the skill can be used with; empty = any weapon
 * @param damageScale   multiplier of every hit of the skill (skill level)
 * @param cooldownTicks cooldown started when the skill is used (skill level)
 */
public record SkillBinding(ActionDefinition action, Set<WeaponType> weapons, double damageScale, int cooldownTicks) {
    public SkillBinding {
        Objects.requireNonNull(action, "action");
        if (action.kind() != ActionKind.SKILL) {
            throw new IllegalArgumentException(action.id() + ": job skills must be SKILL actions");
        }
        weapons = weapons.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(weapons));
        Check.range("damageScale", damageScale, 0.0, 100.0);
        Check.range("cooldownTicks", cooldownTicks, 1, 20 * 600);
    }

    public boolean allows(WeaponProfile weapon) {
        return weapon != null && (weapons.isEmpty() || weapons.contains(weapon.type()));
    }
}
