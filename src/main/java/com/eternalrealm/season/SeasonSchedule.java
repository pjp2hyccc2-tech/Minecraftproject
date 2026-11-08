package com.eternalrealm.season;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * When a season runs: {@code lengthDays} periods of 24 hours from {@code start}. DAY {@code n} begins at
 * {@code start + (n - 1) × 24 h}, independent of time zones and daylight saving, so every server computes the same
 * day; operators pick the start instant (e.g. 10:00 JST) to place the day boundaries.
 *
 * <p>Days are a pacing guide and an announcement schedule, not a gate: progress is unlocked by conditions, never by
 * waiting (docs/DECISIONS.md D-003).</p>
 *
 * @param start      first instant of DAY 1 (server wall clock)
 * @param lengthDays 1 to {@link #MAX_LENGTH_DAYS}; {@link #DEFAULT_LENGTH_DAYS} for the standard season
 */
public record SeasonSchedule(SeasonId id, Instant start, int lengthDays) {
    public static final int DEFAULT_LENGTH_DAYS = 14;
    public static final int MAX_LENGTH_DAYS = 365;
    private static final Duration DAY = Duration.ofDays(1);

    public SeasonSchedule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(start, "start");
        if (lengthDays < 1 || lengthDays > MAX_LENGTH_DAYS) {
            throw new IllegalArgumentException("lengthDays out of range [1, " + MAX_LENGTH_DAYS + "]: " + lengthDays);
        }
        try {
            start.plus(DAY.multipliedBy(lengthDays));
        } catch (DateTimeException | ArithmeticException e) {
            throw new IllegalArgumentException("Season end is not representable for start " + start, e);
        }
    }

    /** A standard {@value #DEFAULT_LENGTH_DAYS}-day season. */
    public static SeasonSchedule of(SeasonId id, Instant start) {
        return new SeasonSchedule(id, start, DEFAULT_LENGTH_DAYS);
    }

    /** First instant after the season (exclusive end). */
    public Instant endsAt() {
        return start.plus(DAY.multipliedBy(lengthDays));
    }

    /** First instant of {@code day} (1-based). */
    public Instant dayStartsAt(int day) {
        if (day < 1 || day > lengthDays) {
            throw new IllegalArgumentException("day out of range [1, " + lengthDays + "]: " + day);
        }
        return start.plus(DAY.multipliedBy(day - 1L));
    }

    /** The 1-based season day at {@code now}, clamped to {@code [1, lengthDays]} before the start and after the end. */
    public int dayOf(Instant now) {
        Objects.requireNonNull(now, "now");
        if (!now.isAfter(start)) {
            return 1;
        }
        long fullDays = Duration.between(start, now).toDays();
        return (int) Math.min(lengthDays, fullDays + 1);
    }

    public SeasonPhase phaseAt(Instant now) {
        Objects.requireNonNull(now, "now");
        if (now.isBefore(start)) {
            return SeasonPhase.NOT_STARTED;
        }
        return now.isBefore(endsAt()) ? SeasonPhase.ACTIVE : SeasonPhase.ENDED;
    }

    /** The same season and length, starting at {@code newStart} (an operator starting the season early). */
    public SeasonSchedule withStart(Instant newStart) {
        return new SeasonSchedule(id, newStart, lengthDays);
    }
}
