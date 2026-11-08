package com.eternalrealm.combat;

import java.util.List;
import java.util.Objects;

/**
 * Everything the combat system needs to know about a weapon. Profiles are immutable singletons
 * ({@link WeaponProfiles}); identity comparison is used to detect weapon switches.
 *
 * @param baseAttack      weapon attack power before stat scaling
 * @param critBonus       added to the wielder's critical chance
 * @param combo           light attack steps in order
 * @param comboResetTicks ticks after a step ends during which the next light attack continues the combo
 */
public record WeaponProfile(String id, WeaponType type, double baseAttack, double critBonus,
                            List<ActionDefinition> combo, int comboResetTicks, HeavyAttack heavy,
                            ActionDefinition skill, GuardProfile guard) {
    public WeaponProfile {
        Check.id("id", id);
        Objects.requireNonNull(type, "type");
        Check.range("baseAttack", baseAttack, 0.1, 10_000.0);
        Check.range("critBonus", critBonus, 0.0, 1.0);
        combo = List.copyOf(combo);
        Check.range("combo size", combo.size(), 1, 8);
        for (ActionDefinition step : combo) {
            if (step.kind() != ActionKind.LIGHT) {
                throw new IllegalArgumentException(id + ": combo steps must be LIGHT actions");
            }
        }
        Check.range("comboResetTicks", comboResetTicks, 0, 200);
        Objects.requireNonNull(heavy, "heavy");
        Objects.requireNonNull(skill, "skill");
        if (skill.kind() != ActionKind.SKILL || skill.cooldownTicks() <= 0) {
            throw new IllegalArgumentException(id + ": the weapon skill must be a SKILL with a cooldown");
        }
        Objects.requireNonNull(guard, "guard");
    }
}
