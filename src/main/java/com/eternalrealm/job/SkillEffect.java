package com.eternalrealm.job;

import java.util.Objects;

/**
 * Non-damage effects of a job skill, executed by the platform layer. {@link #frame()} says when: {@code -1} when the
 * action starts, otherwise at that hit frame of the skill's action (afflictions go to whatever that frame hits).
 */
public sealed interface SkillEffect permits SkillEffect.Heal, SkillEffect.Buff, SkillEffect.Shield, SkillEffect.Dash,
        SkillEffect.Taunt, SkillEffect.DropAggro, SkillEffect.Afflict, SkillEffect.Visual {
    int ON_START = -1;

    int frame();

    /** Who a buff applies to. */
    enum BuffTarget {
        SELF,
        /** The caster and party members within the radius. */
        ALLIES
    }

    enum BuffKind {
        /** Damage dealt × (1 + magnitude). */
        ATTACK_UP,
        /** Damage taken × (1 − magnitude). */
        DEFENSE_UP,
        /** Movement speed (vanilla Speed, amplifier = magnitude). */
        SPEED,
        /** Vanilla Regeneration (amplifier = magnitude). */
        REGENERATION,
        INVISIBILITY,
        /** Melee hits set targets on fire. */
        FLAME_WEAPON
    }

    enum AfflictionKind {
        /** Vanilla Slowness (amplifier = power). */
        SLOW,
        /** Strong slow plus the powder snow frost overlay. */
        FREEZE,
        /** Sets the target on fire. */
        BURN,
        /** Damage over time: {@code power} × the hit's damage per second. */
        BLEED,
        BLIND
    }

    /** Heals the caster and party members within {@code radius}: {@code fraction} of max health + {@code flat}. */
    record Heal(int frame, double radius, double fraction, double flat) implements SkillEffect {
        public Heal {
            Checks.frame(frame);
            Checks.range("radius", radius, 0.0, 32.0);
            Checks.range("fraction", fraction, 0.0, 1.0);
            Checks.range("flat", flat, 0.0, 1000.0);
        }
    }

    /**
     * @param magnitude ATTACK_UP/DEFENSE_UP: fraction; SPEED/REGENERATION: vanilla amplifier; otherwise ignored
     * @param radius    ALLIES only: reach around the caster
     */
    record Buff(int frame, BuffTarget target, BuffKind kind, double magnitude, int durationTicks, double radius)
            implements SkillEffect {
        public Buff {
            Checks.frame(frame);
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(kind, "kind");
            Checks.range("magnitude", magnitude, 0.0, 5.0);
            Checks.range("durationTicks", durationTicks, 1, 20 * 600);
            Checks.range("radius", radius, 0.0, 32.0);
        }
    }

    /** Absorption equal to {@code fraction} of the caster's max health for {@code durationTicks}. */
    record Shield(int frame, double fraction, int durationTicks) implements SkillEffect {
        public Shield {
            Checks.frame(frame);
            Checks.range("fraction", fraction, 0.0, 2.0);
            Checks.range("durationTicks", durationTicks, 1, 20 * 600);
        }
    }

    /** Moves the caster {@code distance} blocks along the horizontal facing (backwards if requested). */
    record Dash(int frame, double distance, boolean backwards) implements SkillEffect {
        public Dash {
            Checks.frame(frame);
            Checks.range("distance", distance, 0.5, 16.0);
        }
    }

    /** Hostile mobs within {@code radius} target the caster. */
    record Taunt(int frame, double radius, int durationTicks) implements SkillEffect {
        public Taunt {
            Checks.frame(frame);
            Checks.range("radius", radius, 1.0, 32.0);
            Checks.range("durationTicks", durationTicks, 1, 20 * 60);
        }
    }

    /** Hostile mobs within {@code radius} that target the caster lose their target. */
    record DropAggro(int frame, double radius) implements SkillEffect {
        public DropAggro {
            Checks.frame(frame);
            Checks.range("radius", radius, 1.0, 32.0);
        }
    }

    /** Applied to every target the frame (or its projectiles) hits. */
    record Afflict(int frame, AfflictionKind kind, int durationTicks, double power) implements SkillEffect {
        public Afflict {
            if (frame < 0) {
                throw new IllegalArgumentException("afflictions need a hit frame");
            }
            Objects.requireNonNull(kind, "kind");
            Checks.range("durationTicks", durationTicks, 1, 20 * 60);
            Checks.range("power", power, 0.0, 10.0);
        }
    }

    /** Particles/sound hint for the platform layer (e.g. {@code lightning}, {@code heal}). */
    record Visual(int frame, String kind) implements SkillEffect {
        public Visual {
            Checks.frame(frame);
            Checks.id("kind", kind);
        }
    }
}
