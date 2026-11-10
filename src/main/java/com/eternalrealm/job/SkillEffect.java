package com.eternalrealm.job;

import java.util.Objects;

/**
 * Non-damage effects of a job skill, executed by the platform layer. {@link #frame()} says when: {@code -1} when the
 * action starts, otherwise at that hit frame of the skill's action (afflictions go to whatever that frame hits).
 *
 * <p>Strengths grow with the skill level: healing, shields, fractional buffs, damage-over-time and the debuff
 * strengths are multiplied by {@link SkillDefinition#effectScale(int)}, affliction durations too (see each record).</p>
 */
public sealed interface SkillEffect permits SkillEffect.Heal, SkillEffect.Buff, SkillEffect.Shield, SkillEffect.Dash,
        SkillEffect.Leap, SkillEffect.Taunt, SkillEffect.DropAggro, SkillEffect.Afflict, SkillEffect.Visual,
        SkillEffect.Cleanse, SkillEffect.Siphon, SkillEffect.Zone, SkillEffect.RestoreStamina,
        SkillEffect.CooldownCut, SkillEffect.Execute, SkillEffect.Mark, SkillEffect.Revive {
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
        ATTACK_UP(true),
        /** Damage taken × (1 − magnitude). */
        DEFENSE_UP(true),
        /** Movement speed (amplifier = magnitude). */
        SPEED(false),
        /** Vanilla Regeneration (amplifier = magnitude). */
        REGENERATION(false),
        INVISIBILITY(false),
        /** Melee hits set targets on fire. */
        FLAME_WEAPON(false),
        /** Critical chance + magnitude. */
        CRIT_UP(true),
        /** Heals magnitude of the damage the fighter's hits deal. */
        LIFESTEAL(true),
        /** Melee hits chill (slow) their targets. */
        FROST_WEAPON(false),
        /** Melee hits poison their targets. */
        VENOM_WEAPON(false),
        /** Melee hits arc to a second enemy nearby. */
        THUNDER_WEAPON(false),
        /** Chance (magnitude) to evade attacks completely. */
        EVASION(true),
        /** Reflects magnitude of the damage taken from attackers. */
        THORNS(true);

        private final boolean fractional;

        BuffKind(boolean fractional) {
            this.fractional = fractional;
        }

        /** The magnitude is a fraction (5 % steps, grows with the skill level); otherwise an amplifier or unused. */
        public boolean fractional() {
            return fractional;
        }

        public String translationKey() {
            return "skill_buff.eternalrealm." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    enum AfflictionKind {
        /** Slowness (amplifier = power). */
        SLOW,
        /** Strong slow plus the powder snow frost overlay. */
        FREEZE,
        /** Sets the target on fire. */
        BURN,
        /** Damage over time: {@code power} × the hit's damage per second. */
        BLEED,
        BLIND,
        /** Cannot move or attack (bosses only stagger for a moment). */
        STUN,
        /** Damage over time like bleeding ({@code power} × the hit's damage per second), stacks with it. */
        POISON,
        /** Damage the target deals × (1 − power). */
        WEAKEN,
        /** Damage the target takes × (1 + power). */
        VULNERABLE,
        /** Drags the target towards the caster ({@code power} = pull speed); instant. */
        PULL,
        /** Throws the target up ({@code power} = upward speed); instant. */
        LAUNCH;

        /** Instant displacements ignore the duration and do not grow with the skill level. */
        public boolean instant() {
            return this == PULL || this == LAUNCH;
        }

        public String translationKey() {
            return "skill_affliction.eternalrealm." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Where a ground area is placed when it is created. */
    enum Placement {
        /** Around the caster's feet. */
        CASTER,
        /** {@code distance} blocks ahead along the horizontal facing. */
        AHEAD,
        /** Where the caster looks, at most {@code distance} blocks away. */
        LOOK
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
     * @param magnitude fractional kinds: fraction; SPEED/REGENERATION: amplifier; otherwise ignored
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

    /**
     * Absorption equal to {@code fraction} of the max health for {@code durationTicks}: the caster's own, or (radius
     * above 0) also every party member within {@code radius}.
     */
    record Shield(int frame, double fraction, int durationTicks, double radius) implements SkillEffect {
        public Shield {
            Checks.frame(frame);
            Checks.range("fraction", fraction, 0.0, 2.0);
            Checks.range("durationTicks", durationTicks, 1, 20 * 600);
            Checks.range("radius", radius, 0.0, 32.0);
        }

        /** A shield for the caster only. */
        public Shield(int frame, double fraction, int durationTicks) {
            this(frame, fraction, durationTicks, 0.0);
        }
    }

    /** Moves the caster {@code distance} blocks along the horizontal facing (backwards if requested). */
    record Dash(int frame, double distance, boolean backwards) implements SkillEffect {
        public Dash {
            Checks.frame(frame);
            Checks.range("distance", distance, 0.5, 16.0);
        }
    }

    /** Jumps {@code distance} blocks forward and about {@code height} blocks up (movement is the client's). */
    record Leap(int frame, double distance, double height) implements SkillEffect {
        public Leap {
            Checks.frame(frame);
            Checks.range("distance", distance, 0.0, 16.0);
            Checks.range("height", height, 0.5, 8.0);
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
            if ((kind == AfflictionKind.WEAKEN || kind == AfflictionKind.VULNERABLE) && power > 0.6) {
                throw new IllegalArgumentException(kind + " power above 60%: " + power);
            }
        }
    }

    /**
     * Particles/sound for the platform layer, coloured by the skill's {@link SkillTheme}: generic kinds
     * ({@code cast}, {@code nova}, {@code pillar}, {@code aura}, {@code shield}, {@code smite}, {@code quake},
     * {@code storm}, {@code vortex}, {@code mist}, {@code blink}, {@code burst}, {@code ray}, {@code rain},
     * {@code sigil}, {@code impact}, {@code roar}) and the named effects of the first skills ({@code lightning},
     * {@code heal}, {@code judgment}...).
     */
    record Visual(int frame, String kind) implements SkillEffect {
        public Visual {
            Checks.frame(frame);
            Checks.id("kind", kind);
        }
    }

    /** Removes harmful effects (and fire) from the caster and party members within {@code radius}. */
    record Cleanse(int frame, double radius) implements SkillEffect {
        public Cleanse {
            Checks.frame(frame);
            Checks.range("radius", radius, 0.0, 32.0);
        }
    }

    /**
     * Heals the caster and party members within {@code radius} by {@code fraction} of the damage the frame (or its
     * projectiles) dealt.
     */
    record Siphon(int frame, double fraction, double radius) implements SkillEffect {
        public Siphon {
            if (frame < 0) {
                throw new IllegalArgumentException("siphons need a hit frame");
            }
            Checks.range("fraction", fraction, 0.01, 2.0);
            Checks.range("radius", radius, 0.0, 32.0);
        }
    }

    /**
     * A ground area that pulses {@code pulses} times every {@code intervalTicks}, the first pulse after
     * {@code delayTicks}: each pulse damages enemies inside ({@code damageMultiplier} × the skill's damage, like a hit
     * frame), heals allies inside ({@code healFraction} of their max health, grows with the level) and may afflict.
     *
     * @param affliction applied to enemies a pulse damages, or {@code null}
     * @param look       the area's own effect at every pulse (a named visual such as a falling meteor), or {@code null}
     *                   for the theme's generic pulse
     */
    record Zone(int frame, Placement placement, double distance, double radius, int delayTicks, int pulses,
                int intervalTicks, double damageMultiplier, double healFraction, AfflictionKind affliction,
                int afflictionTicks, double afflictionPower, String look) implements SkillEffect {
        /** A zone with the theme's generic pulse. */
        public Zone(int frame, Placement placement, double distance, double radius, int delayTicks, int pulses,
                    int intervalTicks, double damageMultiplier, double healFraction, AfflictionKind affliction,
                    int afflictionTicks, double afflictionPower) {
            this(frame, placement, distance, radius, delayTicks, pulses, intervalTicks, damageMultiplier, healFraction,
                    affliction, afflictionTicks, afflictionPower, null);
        }

        public Zone {
            if (look != null && !look.matches("[a-z0-9_]+")) {
                throw new IllegalArgumentException("look must be [a-z0-9_]+: " + look);
            }
            Checks.frame(frame);
            Objects.requireNonNull(placement, "placement");
            Checks.range("distance", distance, 0.0, 24.0);
            Checks.range("radius", radius, 1.0, 12.0);
            Checks.range("delayTicks", delayTicks, 0, 200);
            Checks.range("pulses", pulses, 1, 40);
            Checks.range("intervalTicks", intervalTicks, 1, 100);
            Checks.range("damageMultiplier", damageMultiplier, 0.0, 10.0);
            Checks.range("healFraction", healFraction, 0.0, 0.5);
            if (damageMultiplier == 0.0 && healFraction == 0.0) {
                throw new IllegalArgumentException("a zone must damage or heal");
            }
            if (affliction != null) {
                Checks.range("afflictionTicks", afflictionTicks, 1, 20 * 60);
                Checks.range("afflictionPower", afflictionPower, 0.0, 10.0);
                if (damageMultiplier == 0.0) {
                    throw new IllegalArgumentException("only damaging zones afflict");
                }
            }
            if ((long) delayTicks + (long) (pulses - 1) * intervalTicks > 20 * 30) {
                throw new IllegalArgumentException("a zone lasts at most 30 seconds");
            }
        }

        /** Ticks from creation to the last pulse. */
        public int lifetimeTicks() {
            return delayTicks + (pulses - 1) * intervalTicks;
        }

        /** The same area with its own pulse effect. */
        public Zone withLook(String newLook) {
            return new Zone(frame, placement, distance, radius, delayTicks, pulses, intervalTicks, damageMultiplier,
                    healFraction, affliction, afflictionTicks, afflictionPower, newLook);
        }
    }

    /**
     * A friendly ground marker (telegraph) showing where something will land, for {@code ticks}: placed like a
     * {@link Zone}. Purely visual; allies standing in it are not warned of danger.
     */
    record Mark(int frame, Placement placement, double distance, double radius, int ticks) implements SkillEffect {
        public Mark {
            Checks.frame(frame);
            Objects.requireNonNull(placement, "placement");
            Checks.range("distance", distance, 0.0, 24.0);
            Checks.range("radius", radius, 0.5, 12.0);
            Checks.range("ticks", ticks, 1, 20 * 10);
        }
    }

    /**
     * Raises fallen (戦闘不能) party members within {@code radius} of the caster where they lie, with {@code fraction} of
     * their max health (grows with the skill level, at most full health).
     */
    record Revive(int frame, double radius, double fraction) implements SkillEffect {
        public Revive {
            Checks.frame(frame);
            Checks.range("radius", radius, 1.0, 24.0);
            Checks.range("fraction", fraction, 0.05, 1.0);
        }
    }

    /** Gives the caster {@code amount} stamina back. */
    record RestoreStamina(int frame, double amount) implements SkillEffect {
        public RestoreStamina {
            Checks.frame(frame);
            Checks.range("amount", amount, 1.0, 200.0);
        }
    }

    /** Shortens the running cooldowns of the caster's other job skills by {@code ticks}. */
    record CooldownCut(int frame, int ticks) implements SkillEffect {
        public CooldownCut {
            Checks.frame(frame);
            Checks.range("ticks", ticks, 1, 20 * 60);
        }
    }

    /** The frame's hits deal {@code bonus} more damage to targets below {@code threshold} of their health. */
    record Execute(int frame, double threshold, double bonus) implements SkillEffect {
        public Execute {
            if (frame < 0) {
                throw new IllegalArgumentException("executes need a hit frame");
            }
            Checks.range("threshold", threshold, 0.05, 0.9);
            Checks.range("bonus", bonus, 0.05, 5.0);
        }
    }
}
