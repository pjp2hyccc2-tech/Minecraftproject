package com.eternalrealm.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The collapse of a defeated boss. When its health reaches 0 the platform freezes it (no AI, no attacks, invulnerable),
 * plays these beats tick by tick from the killing blow, and only after {@link #lengthTicks()} removes the body and runs
 * the defeat flow (rewards, story, music). The King of the End collapses longer, before the ending.
 *
 * @param beats in tick order; the last one is the single {@link Kind#VANISH} at {@code lengthTicks}
 */
public record DefeatSequence(int lengthTicks, List<Beat> beats) {
    /** What happens at a beat. */
    public enum Kind {
        /** The boss staggers and sinks to its knees (death animation, held), roars, the camera shakes. */
        COLLAPSE,
        /** The body flashes white with a burst of its theme particles. */
        FLASH,
        /** Sparks and light burst out of the body. */
        BURST,
        /** A ring of dust and light rushes outward over the floor. */
        SHOCKWAVE,
        /** A pillar of light rises from the body into the sky. */
        PILLAR,
        /** The body starts to dissolve into light (the client fades and shrinks it until the end). */
        DISSOLVE,
        /** A last burst of light; the body is removed and the defeat flow runs. */
        VANISH
    }

    /** One beat; {@code intensity} 0..1 scales particles and camera shake. */
    public record Beat(int tick, Kind kind, double intensity) {
        public Beat {
            Objects.requireNonNull(kind, "kind");
            Check.range("tick", tick, 0, 20 * 60);
            Check.range("intensity", intensity, 0.0, 1.0);
        }
    }

    public DefeatSequence {
        beats = List.copyOf(beats);
        Check.range("lengthTicks", lengthTicks, 1, 20 * 60);
        int previous = 0;
        int vanishes = 0;
        for (Beat beat : beats) {
            if (beat.tick() < previous || beat.tick() > lengthTicks) {
                throw new IllegalArgumentException("beats must be in order within the sequence: " + beat);
            }
            previous = beat.tick();
            if (beat.kind() == Kind.VANISH) {
                vanishes++;
                if (beat.tick() != lengthTicks) {
                    throw new IllegalArgumentException("the body vanishes at the end");
                }
            }
        }
        if (vanishes != 1) {
            throw new IllegalArgumentException("exactly one VANISH");
        }
    }

    /** A dungeon boss: five seconds from the killing blow to the rewards. */
    public static final DefeatSequence REGULAR = build(100, new int[][]{
            {0, Kind.COLLAPSE.ordinal(), 100}, {0, Kind.FLASH.ordinal(), 100}, {10, Kind.BURST.ordinal(), 60},
            {20, Kind.FLASH.ordinal(), 70}, {28, Kind.BURST.ordinal(), 70}, {36, Kind.SHOCKWAVE.ordinal(), 80},
            {44, Kind.FLASH.ordinal(), 80}, {52, Kind.BURST.ordinal(), 80}, {60, Kind.PILLAR.ordinal(), 100},
            {66, Kind.DISSOLVE.ordinal(), 100}, {72, Kind.FLASH.ordinal(), 90}, {80, Kind.BURST.ordinal(), 90},
            {88, Kind.FLASH.ordinal(), 100}, {100, Kind.VANISH.ordinal(), 100}});

    /** The King of the End: nine seconds, two shockwaves, three pillars, before the ending begins. */
    public static final DefeatSequence FINAL = build(180, new int[][]{
            {0, Kind.COLLAPSE.ordinal(), 100}, {0, Kind.FLASH.ordinal(), 100}, {12, Kind.BURST.ordinal(), 70},
            {24, Kind.FLASH.ordinal(), 70}, {32, Kind.SHOCKWAVE.ordinal(), 80}, {40, Kind.BURST.ordinal(), 80},
            {52, Kind.FLASH.ordinal(), 80}, {60, Kind.PILLAR.ordinal(), 80}, {70, Kind.BURST.ordinal(), 90},
            {80, Kind.FLASH.ordinal(), 90}, {90, Kind.SHOCKWAVE.ordinal(), 100}, {100, Kind.PILLAR.ordinal(), 90},
            {110, Kind.FLASH.ordinal(), 90}, {120, Kind.BURST.ordinal(), 100}, {126, Kind.DISSOLVE.ordinal(), 100},
            {132, Kind.FLASH.ordinal(), 100}, {144, Kind.PILLAR.ordinal(), 100}, {150, Kind.BURST.ordinal(), 100},
            {160, Kind.FLASH.ordinal(), 100}, {168, Kind.SHOCKWAVE.ordinal(), 100},
            {180, Kind.VANISH.ordinal(), 100}});

    /** The sequence of a boss: the long one for {@code final}, the regular one otherwise. */
    public static DefeatSequence of(String bossId) {
        return "final".equals(bossId) ? FINAL : REGULAR;
    }

    /** Beats at exactly {@code tick} (ticks since the killing blow). */
    public List<Beat> at(int tick) {
        List<Beat> found = new ArrayList<>(2);
        for (Beat beat : beats) {
            if (beat.tick() == tick) {
                found.add(beat);
            }
        }
        return found;
    }

    /** Tick at which the body starts to dissolve. */
    public int dissolveTick() {
        for (Beat beat : beats) {
            if (beat.kind() == Kind.DISSOLVE) {
                return beat.tick();
            }
        }
        return lengthTicks;
    }

    private static DefeatSequence build(int length, int[][] rows) {
        List<Beat> beats = new ArrayList<>(rows.length);
        for (int[] row : rows) {
            beats.add(new Beat(row[0], Kind.values()[row[1]], row[2] / 100.0));
        }
        return new DefeatSequence(length, beats);
    }
}
