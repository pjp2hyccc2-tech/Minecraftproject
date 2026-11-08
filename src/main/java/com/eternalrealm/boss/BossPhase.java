package com.eternalrealm.boss;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * One phase (form) of a boss fight. Phases only ever advance: healing above a threshold does not bring a phase back.
 *
 * @param startsAtHpFraction      the phase starts when the health fraction drops to this value or below (first phase:
 *                                1.0)
 * @param abilityIds              the rotation of this phase, picked by weight among the abilities that are ready
 * @param globalCooldownTicks     pause after every ability during which the boss only moves and melees (vanilla AI)
 * @param movementSpeedMultiplier applied by the platform to the boss's base movement speed during the phase
 * @param openerIds               abilities used once, in this order, as soon as the phase begins (ignoring their
 *                                cooldowns; skipped if not usable): the phase's signature move, e.g. an arena-wide
 *                                attack or the add shield. Each must also be in {@code abilityIds}
 * @param transitionId            ability the boss uses during the invulnerable transition into this phase (the
 *                                transformation's shockwave), {@code ""} for none; never on the first phase
 * @param enrages                 entering this phase enrages the boss (as the soft enrage does): the desperate last
 *                                form of the stronger bosses
 */
public record BossPhase(double startsAtHpFraction, List<String> abilityIds, int globalCooldownTicks,
                        double movementSpeedMultiplier, List<String> openerIds, String transitionId,
                        boolean enrages) {
    public BossPhase {
        Check.range("startsAtHpFraction", startsAtHpFraction, 0.01, 1.0);
        abilityIds = List.copyOf(abilityIds);
        if (abilityIds.isEmpty()) {
            throw new IllegalArgumentException("a phase needs at least one ability");
        }
        for (String id : abilityIds) {
            Check.id("abilityId", id);
        }
        if (new HashSet<>(abilityIds).size() != abilityIds.size()) {
            throw new IllegalArgumentException("duplicate ability ids in phase: " + abilityIds);
        }
        Check.range("globalCooldownTicks", globalCooldownTicks, 0, 1200);
        Check.range("movementSpeedMultiplier", movementSpeedMultiplier, 0.1, 3.0);
        openerIds = List.copyOf(openerIds);
        if (new HashSet<>(openerIds).size() != openerIds.size()) {
            throw new IllegalArgumentException("duplicate opener ids: " + openerIds);
        }
        for (String opener : openerIds) {
            if (!abilityIds.contains(opener)) {
                throw new IllegalArgumentException("opener " + opener + " is not in the phase's abilities");
            }
        }
        Objects.requireNonNull(transitionId, "transitionId");
        if (!transitionId.isEmpty()) {
            Check.id("transitionId", transitionId);
        }
    }

    /** A phase without transition ability and without enrage. */
    public BossPhase(double startsAtHpFraction, List<String> abilityIds, int globalCooldownTicks,
                     double movementSpeedMultiplier, List<String> openerIds) {
        this(startsAtHpFraction, abilityIds, globalCooldownTicks, movementSpeedMultiplier, openerIds, "", false);
    }

    /** A phase without openers. */
    public BossPhase(double startsAtHpFraction, List<String> abilityIds, int globalCooldownTicks,
                     double movementSpeedMultiplier) {
        this(startsAtHpFraction, abilityIds, globalCooldownTicks, movementSpeedMultiplier, List.of());
    }

    /** Whether the boss uses an ability during the transition into this phase. */
    public boolean hasTransition() {
        return !transitionId.isEmpty();
    }
}
