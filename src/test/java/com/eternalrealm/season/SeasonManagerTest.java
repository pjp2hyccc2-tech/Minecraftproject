package com.eternalrealm.season;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SeasonManagerTest {
    private static final SeasonId S1 = SeasonId.of("s1");
    private static final SeasonId S2 = SeasonId.of("s2");
    private static final Instant START = Instant.parse("2026-11-14T01:00:00Z");
    private static final Duration DAY = Duration.ofDays(1);
    private static final Duration HOUR = Duration.ofHours(1);
    private static final SeasonSchedule SCHEDULE = SeasonSchedule.of(S1, START);
    private static final Instant END = SCHEDULE.endsAt();

    private static SeasonEvent event(SeasonEvent.Type type, int day) {
        return new SeasonEvent(type, S1, day);
    }

    private static Instant day(int day) {
        return START.plus(DAY.multipliedBy(day - 1L)).plus(HOUR);
    }

    @Test
    public void aPreparingSeasonWaitsForItsStart() {
        SeasonManager manager = SeasonManager.prepare(SCHEDULE);
        assertEquals(SeasonStatus.PREPARING, manager.status());
        assertEquals(List.of(), manager.tick(START.minusMillis(1)));
        assertEquals(SeasonStatus.PREPARING, manager.status());
        assertFalse(manager.isRankingOpen());
        assertEquals(0, manager.displayDay(START.minusMillis(1)));
    }

    @Test
    public void aFullSeasonEmitsEveryEventExactlyOnceInOrder() {
        SeasonManager manager = SeasonManager.prepare(SCHEDULE);
        List<SeasonEvent> events = new ArrayList<>();
        for (Instant now = START.minus(HOUR.multipliedBy(2)); now.isBefore(END.plus(DAY)); now = now.plus(HOUR)) {
            events.addAll(manager.tick(now));
        }
        List<SeasonEvent> expected = new ArrayList<>();
        expected.add(event(SeasonEvent.Type.STARTED, 1));
        for (int day = 2; day <= 14; day++) {
            expected.add(event(SeasonEvent.Type.DAY_CHANGED, day));
        }
        expected.add(event(SeasonEvent.Type.FINAL_DAY, 14));
        expected.add(event(SeasonEvent.Type.ENDED, 14));
        expected.add(event(SeasonEvent.Type.ARCHIVE_REQUIRED, 14));
        assertEquals(expected, events);
        assertEquals(SeasonStatus.ENDED, manager.status());
        assertEquals("the scheduled end, not the tick that noticed it", END, manager.state().endedAt());

        assertTrue(manager.markArchived(END.plus(DAY)));
        assertEquals(SeasonStatus.ARCHIVED, manager.status());
        assertEquals(END.plus(DAY), manager.state().archivedAt());
        assertEquals(List.of(), manager.tick(END.plus(DAY.multipliedBy(30))));
        assertEquals(14, manager.displayDay(END.plus(DAY.multipliedBy(30))));
    }

    @Test
    public void aRestartNeverRepeatsAnAnnouncement() {
        SeasonManager manager = SeasonManager.prepare(SCHEDULE);
        assertEquals(List.of(event(SeasonEvent.Type.STARTED, 1)), manager.tick(START));
        assertEquals(List.of(event(SeasonEvent.Type.DAY_CHANGED, 3)), manager.tick(day(3)));

        SeasonManager restarted = new SeasonManager(manager.state());
        assertFalse(restarted.isDirty());
        assertEquals(List.of(), restarted.tick(day(3).plus(HOUR)));
        assertEquals(List.of(event(SeasonEvent.Type.DAY_CHANGED, 4)), restarted.tick(day(4)));

        restarted.tick(day(14));
        SeasonManager onFinalDay = new SeasonManager(restarted.state());
        assertEquals("FINAL_DAY is not repeated", List.of(), onFinalDay.tick(day(14).plus(HOUR)));
        assertEquals(List.of(event(SeasonEvent.Type.ENDED, 14), event(SeasonEvent.Type.ARCHIVE_REQUIRED, 14)),
                onFinalDay.tick(END));
        SeasonManager afterEnd = new SeasonManager(onFinalDay.state());
        assertEquals("only the unconfirmed archive request comes again",
                List.of(event(SeasonEvent.Type.ARCHIVE_REQUIRED, 14)), afterEnd.tick(END.plus(HOUR)));
    }

    @Test
    public void aTimeJumpAnnouncesOnlyTheLatestDay() {
        SeasonManager manager = SeasonManager.prepare(SCHEDULE);
        manager.tick(START);
        assertEquals(List.of(event(SeasonEvent.Type.DAY_CHANGED, 2)), manager.tick(day(2)));
        assertEquals(List.of(event(SeasonEvent.Type.DAY_CHANGED, 6)), manager.tick(day(6).plus(HOUR.multipliedBy(3))));
        assertEquals(6, manager.state().lastAnnouncedDay());
        assertEquals(List.of(event(SeasonEvent.Type.DAY_CHANGED, 14), event(SeasonEvent.Type.FINAL_DAY, 14)),
                manager.tick(day(14)));
    }

    @Test
    public void aJumpPastTheEndEndsWithoutDayAnnouncements() {
        SeasonManager manager = SeasonManager.prepare(SCHEDULE);
        manager.tick(START);
        manager.tick(day(3));
        assertEquals(List.of(event(SeasonEvent.Type.ENDED, 14), event(SeasonEvent.Type.ARCHIVE_REQUIRED, 14)),
                manager.tick(END.plus(DAY.multipliedBy(3))));
        assertEquals(3, manager.state().lastAnnouncedDay());
        assertEquals(END, manager.state().endedAt());
    }

    @Test
    public void aLateFirstTickAnnouncesTheStartAndTheCurrentDay() {
        SeasonManager manager = SeasonManager.prepare(SCHEDULE);
        assertEquals(List.of(event(SeasonEvent.Type.STARTED, 1), event(SeasonEvent.Type.DAY_CHANGED, 3)),
                manager.tick(day(3).plus(HOUR.multipliedBy(4))));
    }

    @Test
    public void aSeasonThatPassedWhileOfflineStillStartsBeforeItEnds() {
        SeasonManager manager = SeasonManager.prepare(SCHEDULE);
        assertEquals(List.of(event(SeasonEvent.Type.STARTED, 1), event(SeasonEvent.Type.ENDED, 14),
                event(SeasonEvent.Type.ARCHIVE_REQUIRED, 14)), manager.tick(END.plus(HOUR)));
    }

    @Test
    public void aOneDaySeasonStartsOnItsFinalDay() {
        SeasonManager manager = SeasonManager.prepare(new SeasonSchedule(S1, START, 1));
        assertEquals(List.of(event(SeasonEvent.Type.STARTED, 1), event(SeasonEvent.Type.FINAL_DAY, 1)), manager.tick(START));
        assertEquals(List.of(), manager.tick(START.plus(HOUR)));
        assertEquals(List.of(event(SeasonEvent.Type.ENDED, 1), event(SeasonEvent.Type.ARCHIVE_REQUIRED, 1)),
                manager.tick(START.plus(DAY)));
    }

    @Test
    public void aClockGoingBackwardsChangesNothing() {
        SeasonManager manager = SeasonManager.prepare(SCHEDULE);
        manager.tick(START);
        manager.tick(day(5));
        SeasonState state = manager.state();
        assertEquals(List.of(), manager.tick(day(2)));
        assertEquals(List.of(), manager.tick(START.minus(DAY.multipliedBy(10))));
        assertEquals(List.of(), manager.tick(day(5).plus(HOUR)));
        assertEquals(state, manager.state());
        assertEquals(SeasonStatus.ACTIVE, manager.status());
    }

    @Test
    public void theArchiveRequestIsRepeatedPerInstanceUntilConfirmed() {
        SeasonManager manager = SeasonManager.prepare(SCHEDULE);
        manager.tick(START);
        manager.tick(END);
        assertEquals(List.of(), manager.tick(END.plus(HOUR)));

        SeasonManager restarted = new SeasonManager(manager.state());
        assertEquals(List.of(event(SeasonEvent.Type.ARCHIVE_REQUIRED, 14)), restarted.tick(END.plus(HOUR)));
        assertFalse("a request does not change the persisted state", restarted.isDirty());
        assertEquals(List.of(), restarted.tick(END.plus(HOUR)));
        assertTrue(restarted.requestArchiveAgain());
        assertEquals(List.of(event(SeasonEvent.Type.ARCHIVE_REQUIRED, 14)), restarted.tick(END.plus(HOUR)));

        assertTrue(restarted.markArchived(END.plus(DAY)));
        assertFalse(restarted.markArchived(END.plus(DAY)));
        assertFalse(restarted.requestArchiveAgain());
        assertEquals(List.of(), new SeasonManager(restarted.state()).tick(END.plus(DAY)));
    }

    @Test
    public void anOperatorStartBeforeTheScheduleStartsTheSeasonNow() {
        SeasonManager manager = SeasonManager.prepare(SCHEDULE);
        Instant now = START.minus(DAY.multipliedBy(3));
        assertEquals(List.of(event(SeasonEvent.Type.STARTED, 1)), manager.start(now));
        assertEquals(now, manager.schedule().start());
        assertEquals(now.plus(DAY.multipliedBy(14)), manager.schedule().endsAt());
        assertEquals(List.of(), manager.start(now.plus(HOUR)));
        assertEquals("days count from the actual start", List.of(event(SeasonEvent.Type.DAY_CHANGED, 2)),
                manager.tick(now.plus(DAY)));
    }

    @Test
    public void anOperatorStartAfterTheScheduledTimeKeepsTheSchedule() {
        SeasonManager manager = SeasonManager.prepare(SCHEDULE);
        assertEquals(List.of(event(SeasonEvent.Type.STARTED, 1), event(SeasonEvent.Type.DAY_CHANGED, 2)),
                manager.start(day(2)));
        assertEquals(SCHEDULE, manager.schedule());
    }

    @Test
    public void anOperatorEndEndsNowAndRequestsTheArchive() {
        SeasonManager manager = SeasonManager.prepare(SCHEDULE);
        manager.tick(START);
        Instant at = day(5).plus(HOUR);
        assertEquals(List.of(event(SeasonEvent.Type.DAY_CHANGED, 5)), manager.tick(at));
        assertEquals(List.of(event(SeasonEvent.Type.ENDED, 5), event(SeasonEvent.Type.ARCHIVE_REQUIRED, 5)),
                manager.forceEnd(at));
        assertEquals(at, manager.state().endedAt());
        assertEquals(SeasonStatus.ENDED, manager.status());
        assertFalse(manager.isRankingOpen());
        assertEquals(5, manager.displayDay(END.plus(DAY)));
        assertEquals(List.of(), manager.forceEnd(at.plus(HOUR)));
        assertEquals(List.of(), manager.tick(END.plus(DAY)));
    }

    @Test
    public void anOperatorEndAppliesDueTransitionsFirst() {
        SeasonManager preparing = SeasonManager.prepare(SCHEDULE);
        assertEquals("nothing to end before the start", List.of(), preparing.forceEnd(START.minus(DAY)));
        assertEquals(SeasonStatus.PREPARING, preparing.status());

        SeasonManager late = SeasonManager.prepare(SCHEDULE);
        late.tick(START);
        assertEquals(List.of(event(SeasonEvent.Type.ENDED, 14), event(SeasonEvent.Type.ARCHIVE_REQUIRED, 14)),
                late.forceEnd(END.plus(HOUR)));
        assertEquals("the scheduled end had already passed", END, late.state().endedAt());
    }

    @Test
    public void theArchiveCanOnlyBeConfirmedForAnEndedSeason() {
        SeasonManager manager = SeasonManager.prepare(SCHEDULE);
        assertFalse(manager.markArchived(START));
        manager.tick(START);
        assertFalse(manager.markArchived(START));
        assertFalse(manager.requestArchiveAgain());
        assertEquals(SeasonStatus.ACTIVE, manager.status());
    }

    @Test
    public void theFinalBossFirstClearIsRecordedOnceWhileActive() {
        SeasonManager manager = SeasonManager.prepare(SCHEDULE);
        assertFalse(manager.recordFinalBossClear(START.minus(HOUR)));
        manager.tick(START);
        assertTrue(manager.isRankingOpen());
        Instant firstClear = day(12);
        assertTrue(manager.recordFinalBossClear(firstClear));
        assertFalse(manager.recordFinalBossClear(day(13)));
        manager.tick(day(14));
        manager.tick(END);
        assertEquals(firstClear, manager.state().finalBossFirstClearAt());
        assertFalse(manager.recordFinalBossClear(END.plus(HOUR)));
        SeasonManager unclaimed = SeasonManager.prepare(SCHEDULE);
        unclaimed.tick(END);
        assertNull(unclaimed.state().finalBossFirstClearAt());
    }

    @Test
    public void changesAreTrackedForSaving() {
        SeasonManager manager = SeasonManager.prepare(SCHEDULE);
        assertTrue("a new season must be stored", manager.isDirty());
        manager.markSaved(manager.revision());
        assertFalse(manager.isDirty());
        manager.tick(START.minus(HOUR));
        assertFalse(manager.isDirty());
        manager.tick(START);
        assertTrue(manager.isDirty());
        long saved = manager.revision();
        manager.markSaved(saved);
        manager.tick(START.plus(HOUR));
        assertFalse(manager.isDirty());
        manager.markSaved(saved - 1);
        assertFalse(manager.isDirty());
        assertTrue(manager.recordFinalBossClear(START.plus(HOUR)));
        assertTrue(manager.isDirty());
    }

    @Test
    public void startupChoosesTheConfiguredSeasonOnlyWhenThatIsSafe() {
        SeasonManager fresh = SeasonManager.atStartup(Optional.empty(), SCHEDULE);
        assertEquals(SeasonState.preparing(SCHEDULE), fresh.state());
        assertTrue(fresh.isDirty());

        SeasonManager resumed = SeasonManager.atStartup(Optional.of(SeasonState.preparing(SCHEDULE)), SCHEDULE);
        assertFalse(resumed.isDirty());

        SeasonSchedule movedStart = SCHEDULE.withStart(START.plus(DAY));
        assertEquals("a season that has not started follows the configuration", movedStart,
                SeasonManager.atStartup(Optional.of(SeasonState.preparing(SCHEDULE)), movedStart).schedule());
        SeasonSchedule other = SeasonSchedule.of(S2, START.plus(DAY.multipliedBy(30)));
        assertEquals(S2, SeasonManager.atStartup(Optional.of(SeasonState.preparing(SCHEDULE)), other).seasonId());

        SeasonManager running = SeasonManager.prepare(SCHEDULE);
        running.tick(day(3));
        SeasonManager kept = SeasonManager.atStartup(Optional.of(running.state()), movedStart);
        assertEquals("a running season keeps its days", SCHEDULE, kept.schedule());
        assertEquals(SeasonStatus.ACTIVE, kept.status());
        assertThrows(IllegalStateException.class, () -> SeasonManager.atStartup(Optional.of(running.state()), other));

        running.tick(END);
        assertThrows(IllegalStateException.class, () -> SeasonManager.atStartup(Optional.of(running.state()), other));
        running.markArchived(END.plus(HOUR));
        SeasonManager next = SeasonManager.atStartup(Optional.of(running.state()), other);
        assertEquals(SeasonState.preparing(other), next.state());
        assertTrue(next.isDirty());
        assertEquals(SeasonStatus.ARCHIVED, SeasonManager.atStartup(Optional.of(running.state()), SCHEDULE).status());
    }
}
