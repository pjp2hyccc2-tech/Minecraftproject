package com.eternalrealm.season;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class SeasonScheduleTest {
    private static final SeasonId S1 = SeasonId.of("s1");
    /** 10:00 JST. */
    private static final Instant START = Instant.parse("2026-11-14T01:00:00Z");
    private static final Duration DAY = Duration.ofDays(1);
    private static final Duration MS = Duration.ofMillis(1);

    @Test
    public void theDefaultSeasonLastsFourteenDays() {
        SeasonSchedule schedule = SeasonSchedule.of(S1, START);
        assertEquals(SeasonSchedule.DEFAULT_LENGTH_DAYS, schedule.lengthDays());
        assertEquals(14, schedule.lengthDays());
        assertEquals(Instant.parse("2026-11-28T01:00:00Z"), schedule.endsAt());
    }

    @Test
    public void daysAreTwentyFourHourPeriodsFromTheStart() {
        SeasonSchedule schedule = SeasonSchedule.of(S1, START);
        assertEquals(1, schedule.dayOf(START));
        assertEquals(1, schedule.dayOf(START.plus(DAY).minus(MS)));
        assertEquals(2, schedule.dayOf(START.plus(DAY)));
        assertEquals(14, schedule.dayOf(START.plus(DAY.multipliedBy(13))));
        assertEquals(14, schedule.dayOf(schedule.endsAt().minus(MS)));
        assertEquals(START, schedule.dayStartsAt(1));
        assertEquals(START.plus(DAY.multipliedBy(13)), schedule.dayStartsAt(14));
        assertThrows(IllegalArgumentException.class, () -> schedule.dayStartsAt(0));
        assertThrows(IllegalArgumentException.class, () -> schedule.dayStartsAt(15));
    }

    @Test
    public void dayIsClampedOutsideTheSeason() {
        SeasonSchedule schedule = SeasonSchedule.of(S1, START);
        assertEquals(1, schedule.dayOf(START.minus(MS)));
        assertEquals(1, schedule.dayOf(START.minus(DAY.multipliedBy(30))));
        assertEquals(1, schedule.dayOf(Instant.MIN));
        assertEquals(14, schedule.dayOf(schedule.endsAt()));
        assertEquals(14, schedule.dayOf(schedule.endsAt().plus(DAY.multipliedBy(100))));
        assertEquals(14, schedule.dayOf(Instant.MAX));
    }

    @Test
    public void phaseChangesAtTheStartAndAtTheEnd() {
        SeasonSchedule schedule = SeasonSchedule.of(S1, START);
        assertEquals(SeasonPhase.NOT_STARTED, schedule.phaseAt(START.minus(MS)));
        assertEquals(SeasonPhase.ACTIVE, schedule.phaseAt(START));
        assertEquals(SeasonPhase.ACTIVE, schedule.phaseAt(schedule.endsAt().minus(MS)));
        assertEquals(SeasonPhase.ENDED, schedule.phaseAt(schedule.endsAt()));
    }

    @Test
    public void lengthAndValuesAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> new SeasonSchedule(S1, START, 0));
        assertThrows(IllegalArgumentException.class, () -> new SeasonSchedule(S1, START, SeasonSchedule.MAX_LENGTH_DAYS + 1));
        assertEquals(1, new SeasonSchedule(S1, START, 1).lengthDays());
        assertEquals(365, new SeasonSchedule(S1, START, 365).lengthDays());
        assertThrows(NullPointerException.class, () -> new SeasonSchedule(null, START, 14));
        assertThrows(NullPointerException.class, () -> new SeasonSchedule(S1, null, 14));
        assertThrows(IllegalArgumentException.class, () -> new SeasonSchedule(S1, Instant.MAX, 14));
    }

    @Test
    public void movingTheStartKeepsTheSeasonAndItsLength() {
        SeasonSchedule moved = new SeasonSchedule(S1, START, 7).withStart(START.minus(DAY));
        assertEquals(S1, moved.id());
        assertEquals(7, moved.lengthDays());
        assertEquals(START.plus(DAY.multipliedBy(6)), moved.endsAt());
    }
}
