package com.eternalrealm.combat;

import java.util.Objects;

/**
 * Something a {@link Combatant} did, reported by {@link Combatant#tick(long)} in the order it happened.
 * The server turns {@link Hit}s into damage and the starts into sounds/particles for nearby players; the client uses
 * the starts for its predicted swing animation and ignores hits.
 */
public sealed interface CombatEvent permits CombatEvent.ActionStarted, CombatEvent.ChargeStarted, CombatEvent.Hit {
    long tick();

    /**
     * @param comboStep index in the weapon's combo for light attacks, otherwise -1
     */
    record ActionStarted(WeaponProfile weapon, ActionDefinition action, int comboStep, long tick) implements CombatEvent {
        public ActionStarted {
            Objects.requireNonNull(weapon, "weapon");
            Objects.requireNonNull(action, "action");
        }
    }

    record ChargeStarted(WeaponProfile weapon, boolean derived, long tick) implements CombatEvent {
        public ChargeStarted {
            Objects.requireNonNull(weapon, "weapon");
        }
    }

    /**
     * A hit frame is due now.
     *
     * @param damageScale charge / derived / riposte scale on top of the frame multiplier
     */
    record Hit(WeaponProfile weapon, ActionDefinition action, int frameIndex, HitFrame frame, double damageScale,
               long tick) implements CombatEvent {
        public Hit {
            Objects.requireNonNull(weapon, "weapon");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(frame, "frame");
        }
    }
}
