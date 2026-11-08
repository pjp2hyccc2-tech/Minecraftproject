package com.eternalrealm.combat;

import java.util.List;

/**
 * Fixed combat rules shared by every weapon. They are code constants (not config) because the client predicts them.
 */
public final class CombatRules {
    /** Dodge step: 12 ticks, cancelable after 10, invulnerable for the first 8, costs 25 stamina. */
    public static final ActionDefinition DODGE =
            new ActionDefinition("dodge", ActionKind.DODGE, 12, 10, List.of(), 25.0, 0, 8, false);

    /** After a parry, the next action started within this window deals {@link #RIPOSTE_MULTIPLIER} damage. */
    public static final int RIPOSTE_WINDOW_TICKS = 30;
    public static final double RIPOSTE_MULTIPLIER = 1.5;
    public static final double PARRY_STAMINA_REFUND = 15.0;
    /** Running out of stamina while guarding staggers for this long. */
    public static final int GUARD_BREAK_STAGGER_TICKS = 20;
    /** Stamina starts regenerating this many ticks after it was last spent. */
    public static final int STAMINA_REGEN_DELAY_TICKS = 15;
    /** Regeneration is slower while guarding. */
    public static final double GUARD_REGEN_FACTOR = 0.5;
    /** Hard cap of the critical chance including weapon bonuses. */
    public static final double MAX_CRIT_CHANCE = 0.95;
    /** Hard cap of one hit, so no stat or config combination can overflow vanilla's float health. */
    public static final double MAX_DAMAGE = 1_000_000.0;

    private CombatRules() {
    }
}
