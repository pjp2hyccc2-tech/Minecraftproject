package com.eternalrealm.boss;

import java.util.List;

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
        AbilityKind.Heal, AbilityKind.Buff, AbilityKind.Checkerboard, AbilityKind.Crosses, AbilityKind.Trail,
        AbilityKind.Exaflare, AbilityKind.Scatter, AbilityKind.Charge, AbilityKind.Knockback,
        AbilityKind.RotatingBeam, AbilityKind.SafeZones, AbilityKind.Sequence {

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

    /**
     * Ticks from the first hit to the last hit of a pattern of several hits (0 for a single hit). The ability is busy
     * for {@code telegraphTicks + extraTicks() + castTicks}.
     */
    default int extraTicks() {
        return 0;
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

    // ------------------------------------------------------------------ patterns (several hits)

    /**
     * Square floor tiles in a {@code tilesPerSide × tilesPerSide} grid (centred on the arena when the platform told
     * the brain where it is, otherwise on the boss): every wave hits half of the tiles like the dark squares of a
     * checkerboard, the next wave the other half. Each wave appears when the previous one hits and hits
     * {@code intervalTicks} later, so the safe squares are the ones that just exploded.
     */
    record Checkerboard(double tileSize, int tilesPerSide, int waves, int intervalTicks) implements AbilityKind {
        public Checkerboard {
            Check.range("tileSize", tileSize, 2.0, 10.0);
            Check.range("tilesPerSide", tilesPerSide, 2, 12);
            Check.range("waves", waves, 1, 8);
            Check.range("intervalTicks", intervalTicks, BossRules.MIN_TELEGRAPH_TICKS, 200);
        }

        @Override
        public boolean damaging() {
            return true;
        }

        @Override
        public int extraTicks() {
            return (waves - 1) * intervalTicks;
        }
    }

    /**
     * A cross centred on the boss (a plus), then turned by 45 degrees every wave (an X, a plus...): stand between the
     * arms, then move. Each wave appears when the previous one hits.
     */
    record Crosses(double armLength, double armWidth, int waves, int intervalTicks) implements AbilityKind {
        public Crosses {
            Check.range("armLength", armLength, 3.0, 40.0);
            Check.range("armWidth", armWidth, 1.0, 10.0);
            Check.range("waves", waves, 1, 8);
            Check.range("intervalTicks", intervalTicks, BossRules.MIN_TELEGRAPH_TICKS, 200);
        }

        @Override
        public boolean damaging() {
            return true;
        }

        @Override
        public int extraTicks() {
            return (waves - 1) * intervalTicks;
        }
    }

    /**
     * Puddles that chase up to {@code targets} players: every {@code intervalTicks} a circle appears under each
     * chased player and explodes the ability's telegraph time later, so they must keep moving. With
     * {@code lingerTicks} > 0 every explosion leaves a hazard that hurts while standing in it.
     */
    record Trail(double radius, int targets, int drops, int intervalTicks, int lingerTicks) implements AbilityKind {
        public Trail {
            Check.range("radius", radius, 1.0, 8.0);
            Check.range("targets", targets, 1, BossRules.MAX_PARTY_SIZE);
            Check.range("drops", drops, 1, 16);
            Check.range("intervalTicks", intervalTicks, 4, 100);
            Check.range("lingerTicks", lingerTicks, 0, 400);
        }

        @Override
        public boolean damaging() {
            return true;
        }

        @Override
        public int extraTicks() {
            return (drops - 1) * intervalTicks;
        }
    }

    /**
     * Rows that advance outward from the boss in {@code directions} evenly spaced directions (the first towards the
     * target), one row every {@code intervalTicks}: all rows are shown at once and fill up in order, so players step
     * into a row right after it exploded. Rows are {@code spacing} deep and {@code rowLength} wide.
     */
    record Exaflare(int directions, int rows, double spacing, double rowLength, int intervalTicks)
            implements AbilityKind {
        public Exaflare {
            Check.range("directions", directions, 1, 8);
            Check.range("rows", rows, 1, 16);
            Check.range("spacing", spacing, 1.0, 8.0);
            Check.range("rowLength", rowLength, 2.0, 48.0);
            Check.range("intervalTicks", intervalTicks, 2, 60);
        }

        @Override
        public boolean damaging() {
            return true;
        }

        @Override
        public int extraTicks() {
            return (rows - 1) * intervalTicks;
        }
    }

    /**
     * {@code count} circles scattered over the arena (or around the boss when the arena is unknown) within
     * {@code spread}: meteors, orbs, mines. All are shown at once and explode one after another every
     * {@code intervalTicks}; with {@code lingerTicks} > 0 each leaves a hazard.
     */
    record Scatter(int count, double radius, double spread, int intervalTicks, int lingerTicks) implements AbilityKind {
        public Scatter {
            Check.range("count", count, 1, 32);
            Check.range("radius", radius, 1.0, 12.0);
            Check.range("spread", spread, 0.0, 32.0);
            Check.range("intervalTicks", intervalTicks, 0, 60);
            Check.range("lingerTicks", lingerTicks, 0, 400);
        }

        @Override
        public boolean damaging() {
            return true;
        }

        @Override
        public int extraTicks() {
            return (count - 1) * intervalTicks;
        }
    }

    /**
     * The boss charges {@code charges} times along a strip towards its target and ends up at the strip's end (the
     * platform moves it, stopping at walls). Each further charge is aimed {@link BossRules#DASH_TICKS} + 1 ticks
     * after the previous one hit, from where the boss arrived, and hits {@code intervalTicks} after the previous one.
     */
    record Charge(double length, double width, int charges, int intervalTicks) implements AbilityKind {
        public Charge {
            Check.range("length", length, 2.0, 40.0);
            Check.range("width", width, 1.0, 10.0);
            Check.range("charges", charges, 1, 6);
            Check.range("intervalTicks", intervalTicks, BossRules.DASH_TICKS + 1 + BossRules.MIN_TELEGRAPH_TICKS,
                    200);
        }

        @Override
        public boolean damaging() {
            return true;
        }

        @Override
        public int extraTicks() {
            return (charges - 1) * intervalTicks;
        }
    }

    /**
     * A blast around the boss that pushes everyone inside away ({@code strength} &gt; 0) or pulls them in
     * ({@code strength} &lt; 0); {@code |strength|} is the launch speed in blocks per tick.
     */
    record Knockback(double radius, double strength) implements AbilityKind {
        public Knockback {
            Check.range("radius", radius, 1.0, 32.0);
            Check.range("strength", Math.abs(strength), 0.2, 3.0);
        }

        @Override
        public boolean damaging() {
            return true;
        }

        public boolean pulls() {
            return strength < 0.0;
        }
    }

    /**
     * {@code beams} strips from the boss, evenly spaced (the first towards the target), that turn by
     * {@code stepDegrees} every wave in a random direction: keep walking around the boss. Each wave appears when the
     * previous one hits.
     */
    record RotatingBeam(int beams, double length, double width, double stepDegrees, int steps, int intervalTicks)
            implements AbilityKind {
        public RotatingBeam {
            Check.range("beams", beams, 1, 8);
            Check.range("length", length, 2.0, 40.0);
            Check.range("width", width, 1.0, 10.0);
            Check.range("stepDegrees", stepDegrees, 5.0, 90.0);
            Check.range("steps", steps, 1, 24);
            Check.range("intervalTicks", intervalTicks, BossRules.MIN_TELEGRAPH_TICKS, 200);
        }

        @Override
        public boolean damaging() {
            return true;
        }

        @Override
        public int extraTicks() {
            return (steps - 1) * intervalTicks;
        }
    }

    /**
     * Green safe circles spread over the arena; everyone outside all of them is hit. A positioning test: it cannot be
     * dodged or guarded, so its warning is long ({@link BossRules#SAFE_ZONE_MIN_TELEGRAPH_TICKS}).
     */
    record SafeZones(int count, double radius, double spread) implements AbilityKind {
        public SafeZones {
            Check.range("count", count, 1, 8);
            Check.range("radius", radius, 1.5, 10.0);
            Check.range("spread", spread, 0.0, 24.0);
        }

        @Override
        public boolean damaging() {
            return true;
        }

        @Override
        public int minTelegraphTicks() {
            return BossRules.SAFE_ZONE_MIN_TELEGRAPH_TICKS;
        }

        @Override
        public boolean dodgeable() {
            return false;
        }

        @Override
        public boolean guardable() {
            return false;
        }
    }

    /**
     * Single-hit attacks one after another (a combo: pull then blast, out then in...). The first part hits the
     * ability's telegraph time after the start; every further part appears when the previous one hits and hits its
     * own {@code telegraphTicks} later. Each part keeps its own defense flags.
     */
    record Sequence(List<Part> parts) implements AbilityKind {
        public Sequence {
            parts = List.copyOf(parts);
            Check.range("parts", parts.size(), 2, 6);
            for (int i = 0; i < parts.size(); i++) {
                Part part = parts.get(i);
                if (i == 0 && part.telegraphTicks() != 0) {
                    throw new IllegalArgumentException("the first part uses the ability's telegraph (give 0)");
                }
                if (i > 0 && part.telegraphTicks() < part.kind().minTelegraphTicks()) {
                    throw new IllegalArgumentException("part " + i + " needs at least "
                            + part.kind().minTelegraphTicks() + " ticks of warning");
                }
            }
        }

        @Override
        public boolean damaging() {
            return true;
        }

        @Override
        public int minTelegraphTicks() {
            return parts.get(0).kind().minTelegraphTicks();
        }

        @Override
        public boolean dodgeable() {
            return parts.stream().allMatch(part -> part.kind().dodgeable());
        }

        @Override
        public boolean guardable() {
            return parts.stream().allMatch(part -> part.kind().guardable());
        }

        @Override
        public int extraTicks() {
            int ticks = 0;
            for (int i = 1; i < parts.size(); i++) {
                ticks += parts.get(i).telegraphTicks();
            }
            return ticks;
        }
    }

    /**
     * One step of a {@link Sequence}: a single-hit damaging kind, its warning ({@code 0} for the first part) and the
     * share of the ability's damage it deals ({@code 0}: none, e.g. a pull that only moves players).
     */
    record Part(AbilityKind kind, int telegraphTicks, double damageScale) {
        public Part {
            java.util.Objects.requireNonNull(kind, "kind");
            if (!kind.damaging() || kind.extraTicks() != 0 || kind instanceof Sequence
                    || kind instanceof ProjectileVolley) {
                throw new IllegalArgumentException("a sequence part must be a single damaging area: " + kind);
            }
            Check.range("telegraphTicks", telegraphTicks, 0, BossAbility.MAX_TICKS);
            Check.range("damageScale", damageScale, 0.0, 3.0);
        }

        /** A part that deals the ability's full damage. */
        public static Part of(AbilityKind kind, int telegraphTicks) {
            return new Part(kind, telegraphTicks, 1.0);
        }
    }
}
