package com.eternalrealm.boss;

/**
 * What an ability does, with its parameters. Distances are in blocks, durations in ticks.
 *
 * <p>The defense flags tell the platform how to apply the damage of a damaging kind:
 * {@link #dodgeable()} - the dodge's invulnerability frames avoid it; {@link #guardable()} - guard and parry work
 * against it; {@link #splitsDamage()} - the damage is divided among all players inside the shape.</p>
 */
public sealed interface AbilityKind permits AbilityKind.CircleAroundBoss, AbilityKind.CircleAtTargets,
        AbilityKind.Cone, AbilityKind.Line, AbilityKind.Ring, AbilityKind.ArenaWide, AbilityKind.Soak,
        AbilityKind.ProjectileVolley, AbilityKind.Summon, AbilityKind.TeleportToTarget, AbilityKind.Shield,
        AbilityKind.Heal, AbilityKind.Buff {

    /** Whether executing it hurts players; damaging abilities must be telegraphed (see {@link #minTelegraphTicks()}). */
    boolean damaging();

    default int minTelegraphTicks() {
        return damaging() ? BossRules.MIN_TELEGRAPH_TICKS : 0;
    }

    default boolean dodgeable() {
        return damaging();
    }

    default boolean guardable() {
        return damaging();
    }

    default boolean splitsDamage() {
        return false;
    }

    /** Disc around the boss (slam, nova): get away from the boss. */
    record CircleAroundBoss(double radius) implements AbilityKind {
        public CircleAroundBoss {
            Check.range("radius", radius, 1.0, 32.0);
        }

        @Override
        public boolean damaging() {
            return true;
        }
    }

    /** One disc under each of up to {@code maxTargets} players: spread out (overlapping discs each hit). */
    record CircleAtTargets(double radius, int maxTargets) implements AbilityKind {
        public CircleAtTargets {
            Check.range("radius", radius, 1.0, 16.0);
            Check.range("maxTargets", maxTargets, 1, BossRules.MAX_PARTY_SIZE);
        }

        @Override
        public boolean damaging() {
            return true;
        }
    }

    /** Sector in front of the boss, aimed at the target: step behind or beside the boss. */
    record Cone(double range, double halfAngleDegrees) implements AbilityKind {
        public Cone {
            Check.range("range", range, 1.0, 48.0);
            Check.range("halfAngleDegrees", halfAngleDegrees, 1.0, 180.0);
        }

        @Override
        public boolean damaging() {
            return true;
        }
    }

    /** Strip from the boss towards the target (charge, beam): step sideways. {@code width} is the full width. */
    record Line(double length, double width) implements AbilityKind {
        public Line {
            Check.range("length", length, 2.0, 64.0);
            Check.range("width", width, 1.0, 16.0);
        }

        @Override
        public boolean damaging() {
            return true;
        }
    }

    /** Annulus around the boss: the safe spots are close to the boss (inside {@code inner}) or beyond {@code outer}. */
    record Ring(double inner, double outer) implements AbilityKind {
        public Ring {
            Check.range("inner", inner, 1.0, 46.0);
            Check.range("outer", outer, 3.0, 48.0);
            if (outer - inner < 2.0) {
                throw new IllegalArgumentException("ring must be at least 2 blocks wide: " + inner + ".." + outer);
            }
        }

        @Override
        public boolean damaging() {
            return true;
        }
    }

    /** Hits everyone in the fight; only a dodge timed to the hit avoids it (no guard, no hiding). */
    record ArenaWide() implements AbilityKind {
        @Override
        public boolean damaging() {
            return true;
        }

        @Override
        public int minTelegraphTicks() {
            return BossRules.ARENA_WIDE_MIN_TELEGRAPH_TICKS;
        }

        @Override
        public boolean guardable() {
            return false;
        }
    }

    /**
     * Disc on one player whose damage is split among all players inside: the party has to stack. Only chosen while at
     * least {@code minPlayers} targets are in the fight, so it never appears in fights that cannot handle it. Cannot
     * be dodged or guarded (otherwise one player could take it alone without consequences).
     */
    record Soak(double radius, int minPlayers) implements AbilityKind {
        public Soak {
            Check.range("radius", radius, 2.0, 16.0);
            Check.range("minPlayers", minPlayers, 2, BossRules.MAX_PARTY_SIZE);
        }

        @Override
        public boolean damaging() {
            return true;
        }

        @Override
        public int minTelegraphTicks() {
            return BossRules.SOAK_MIN_TELEGRAPH_TICKS;
        }

        @Override
        public boolean dodgeable() {
            return false;
        }

        @Override
        public boolean guardable() {
            return false;
        }

        @Override
        public boolean splitsDamage() {
            return true;
        }
    }

    /**
     * {@code count} projectiles fanned out over {@code spreadDegrees} towards the target. The projectiles are real
     * entities spawned by the platform; they deal damage on impact.
     *
     * @param speed blocks per tick
     */
    record ProjectileVolley(int count, double speed, double spreadDegrees) implements AbilityKind {
        public ProjectileVolley {
            Check.range("count", count, 1, 32);
            Check.range("speed", speed, 0.2, 4.0);
            Check.range("spreadDegrees", spreadDegrees, 0.0, 180.0);
        }

        @Override
        public boolean damaging() {
            return true;
        }
    }

    /** Spawns {@code count} monsters of a template owned by the monster module (bosses use "&lt;bossId&gt;_minion"). */
    record Summon(String templateId, int count) implements AbilityKind {
        public Summon {
            Check.id("templateId", templateId);
            Check.range("count", count, 1, 16);
        }

        @Override
        public boolean damaging() {
            return false;
        }
    }

    /** The boss appears where the target stood when the telegraph started. */
    record TeleportToTarget() implements AbilityKind {
        @Override
        public boolean damaging() {
            return false;
        }
    }

    /**
     * The boss becomes immune until all {@code addCount} summoned adds are dead or {@code timeoutTicks} passed.
     * {@code addCount} 0 is a plain timed shield.
     */
    record Shield(String addTemplateId, int addCount, int timeoutTicks) implements AbilityKind {
        public Shield {
            Check.id("addTemplateId", addTemplateId);
            Check.range("addCount", addCount, 0, 16);
            Check.range("timeoutTicks", timeoutTicks, 20, 2400);
        }

        @Override
        public boolean damaging() {
            return false;
        }
    }

    /** Restores {@code fraction} of the maximum health. Only chosen when the boss has lost at least that much. */
    record Heal(double fraction) implements AbilityKind {
        public Heal {
            Check.range("fraction", fraction, 0.001, 0.5);
        }

        @Override
        public boolean damaging() {
            return false;
        }
    }

    /** Multiplies all damage of the boss for {@code durationTicks}. Not chosen while a buff is running. */
    record Buff(double damageMultiplier, int durationTicks) implements AbilityKind {
        public Buff {
            Check.range("damageMultiplier", damageMultiplier, 1.01, 3.0);
            Check.range("durationTicks", durationTicks, 20, 2400);
        }

        @Override
        public boolean damaging() {
            return false;
        }
    }
}
