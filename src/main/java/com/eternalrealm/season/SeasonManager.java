package com.eternalrealm.season;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Drives the lifecycle of the current season from the server wall clock: {@code PREPARING → ACTIVE → ENDED →
 * ARCHIVED} (never backwards), reporting each step as {@link SeasonEvent}s.
 *
 * <p><b>Exactly once:</b> every announcement event (STARTED, DAY_CHANGED, FINAL_DAY, ENDED) is recorded in the
 * {@link SeasonState} the moment it is emitted, so it is never emitted twice, also not after a restart, provided the
 * caller persists {@link #state()} after every change ({@link #isDirty()}, or whenever a method reports a change).
 * {@link SeasonEvent.Type#ARCHIVE_REQUIRED} is a work request instead: it is emitted once per manager instance until
 * {@link #markArchived} confirms the archive, so a crash between the request and the archive cannot lose the season's
 * hall-of-fame entry.</p>
 *
 * <p><b>Time jumps</b> (server offline, clock changes): only the <em>latest</em> day is announced, never every skipped
 * day (announcing "DAY 3", "DAY 4", "DAY 5" at once is noise; day-based rules should ask
 * {@link SeasonSchedule#dayOf(Instant)} instead of counting events). FINAL_DAY is only announced while the season is
 * still running, and a season whose end has passed ends directly, without day announcements; STARTED always comes
 * before ENDED. A clock that goes backwards changes nothing.</p>
 *
 * <p>Not thread-safe: server thread only; persist {@link #state()} (immutable) on the I/O thread.</p>
 */
public final class SeasonManager {
    private SeasonState state;
    private boolean archiveRequested;
    private long revision;
    private long savedRevision;

    /** Resumes a stored state; not dirty. */
    public SeasonManager(SeasonState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    /** A new season waiting for its scheduled start; dirty, so that it gets persisted. */
    public static SeasonManager prepare(SeasonSchedule schedule) {
        SeasonManager manager = new SeasonManager(SeasonState.preparing(schedule));
        manager.revision = 1;
        return manager;
    }

    /**
     * Chooses the season at server start from the stored state and the configured schedule
     * ({@code season.currentSeasonId} and its start).
     * <ul>
     *     <li>nothing stored, or the stored season has not started yet: the configured season, preparing</li>
     *     <li>the configured season has started: the stored state (a changed start or length is ignored once
     *         running, because it would move the days under the players)</li>
     *     <li>another season is configured: allowed only once the stored season is archived (the rollover)</li>
     * </ul>
     *
     * @throws IllegalStateException if another season is configured while the stored one is active or ended but not
     *                               archived; switching then would lose its rankings and hall-of-fame entry
     */
    public static SeasonManager atStartup(Optional<SeasonState> stored, SeasonSchedule configured) {
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(configured, "configured");
        if (stored.isEmpty()) {
            return prepare(configured);
        }
        SeasonState state = stored.get();
        if (state.status() == SeasonStatus.PREPARING) {
            return state.schedule().equals(configured) ? new SeasonManager(state) : prepare(configured);
        }
        if (state.seasonId().equals(configured.id())) {
            return new SeasonManager(state);
        }
        if (state.status() == SeasonStatus.ARCHIVED) {
            return prepare(configured);
        }
        throw new IllegalStateException("Season " + state.seasonId() + " is " + state.status().id()
                + " and not archived yet; archive it before switching to season " + configured.id());
    }

    /**
     * Applies every transition that is due at {@code now}. Call regularly (e.g. once per second) with the server
     * wall clock.
     *
     * @return the new events in lifecycle order (usually none)
     */
    public List<SeasonEvent> tick(Instant now) {
        Objects.requireNonNull(now, "now");
        List<SeasonEvent> events = new ArrayList<>();
        SeasonSchedule schedule = state.schedule();
        if (state.status() == SeasonStatus.PREPARING && schedule.phaseAt(now) != SeasonPhase.NOT_STARTED) {
            begin(events);
        }
        if (state.status() == SeasonStatus.ACTIVE) {
            if (schedule.phaseAt(now) == SeasonPhase.ENDED) {
                end(schedule.endsAt(), events);
            } else {
                announceDay(schedule.dayOf(now), events);
            }
        }
        requestArchiveIfPending(events);
        return List.copyOf(events);
    }

    /**
     * Operator start. Before the scheduled start the season starts now (the schedule moves, keeping its length);
     * otherwise this is the same as {@link #tick}.
     *
     * @return the events (empty if the season is not preparing)
     */
    public List<SeasonEvent> start(Instant now) {
        Objects.requireNonNull(now, "now");
        if (state.status() != SeasonStatus.PREPARING) {
            return List.of();
        }
        if (state.schedule().phaseAt(now) == SeasonPhase.NOT_STARTED) {
            update(SeasonState.preparing(state.schedule().withStart(now)));
        }
        return tick(now);
    }

    /**
     * Operator end before the schedule. First applies whatever is due at {@code now} (as {@link #tick} would), then
     * ends a still active season at {@code now}.
     *
     * @return the events (empty if there was nothing to end)
     */
    public List<SeasonEvent> forceEnd(Instant now) {
        List<SeasonEvent> events = new ArrayList<>(tick(now));
        if (state.status() == SeasonStatus.ACTIVE) {
            end(now, events);
            requestArchiveIfPending(events);
        }
        return List.copyOf(events);
    }

    /**
     * Confirms that the season's archive is stored in the hall of fame.
     *
     * @return {@code false} if the season is not ended (not yet, or already archived)
     */
    public boolean markArchived(Instant now) {
        Objects.requireNonNull(now, "now");
        if (state.status() != SeasonStatus.ENDED) {
            return false;
        }
        update(new SeasonState(state.schedule(), SeasonStatus.ARCHIVED, state.lastAnnouncedDay(),
                state.finalBossFirstClearAt(), state.endedAt(), now));
        return true;
    }

    /**
     * Makes the next {@link #tick} emit ARCHIVE_REQUIRED again (an operator retry after a failed archive).
     *
     * @return {@code false} if the season is not waiting for its archive
     */
    public boolean requestArchiveAgain() {
        if (state.status() != SeasonStatus.ENDED) {
            return false;
        }
        archiveRequested = false;
        return true;
    }

    /**
     * Records the first defeat of the final boss (a world event: triggers the ending and the next-season teaser).
     *
     * @return {@code true} only for the first clear of an active season
     */
    public boolean recordFinalBossClear(Instant now) {
        Objects.requireNonNull(now, "now");
        if (state.status() != SeasonStatus.ACTIVE || state.finalBossFirstClearAt() != null) {
            return false;
        }
        update(new SeasonState(state.schedule(), state.status(), state.lastAnnouncedDay(), now, null, null));
        return true;
    }

    // ------------------------------------------------------------------------------------------------- reads

    public SeasonState state() {
        return state;
    }

    public SeasonSchedule schedule() {
        return state.schedule();
    }

    public SeasonId seasonId() {
        return state.seasonId();
    }

    public SeasonStatus status() {
        return state.status();
    }

    /** Whether play counts for the season's rankings (only while active). */
    public boolean isRankingOpen() {
        return state.status() == SeasonStatus.ACTIVE;
    }

    /** The day to display: 0 while preparing, the current day while active, the day the season ended afterwards. */
    public int displayDay(Instant now) {
        Objects.requireNonNull(now, "now");
        return switch (state.status()) {
            case PREPARING -> 0;
            case ACTIVE -> state.schedule().dayOf(now);
            case ENDED, ARCHIVED -> state.schedule().dayOf(state.endedAt());
        };
    }

    /** Incremented by every change of {@link #state()}. */
    public long revision() {
        return revision;
    }

    public boolean isDirty() {
        return revision > savedRevision;
    }

    /** Records that the state at {@code persistedRevision} has been stored. Never moves backwards. */
    public void markSaved(long persistedRevision) {
        if (persistedRevision > savedRevision) {
            savedRevision = persistedRevision;
        }
    }

    // ------------------------------------------------------------------------------------------------- internals

    private void begin(List<SeasonEvent> events) {
        update(new SeasonState(state.schedule(), SeasonStatus.ACTIVE, 1, null, null, null));
        events.add(event(SeasonEvent.Type.STARTED, 1));
        if (state.schedule().lengthDays() == 1) {
            events.add(event(SeasonEvent.Type.FINAL_DAY, 1));
        }
    }

    private void announceDay(int day, List<SeasonEvent> events) {
        if (day <= state.lastAnnouncedDay()) {
            return;
        }
        update(new SeasonState(state.schedule(), state.status(), day, state.finalBossFirstClearAt(), null, null));
        events.add(event(SeasonEvent.Type.DAY_CHANGED, day));
        if (day == state.schedule().lengthDays()) {
            events.add(event(SeasonEvent.Type.FINAL_DAY, day));
        }
    }

    private void end(Instant endedAt, List<SeasonEvent> events) {
        update(new SeasonState(state.schedule(), SeasonStatus.ENDED, state.lastAnnouncedDay(),
                state.finalBossFirstClearAt(), endedAt, null));
        events.add(event(SeasonEvent.Type.ENDED, state.schedule().dayOf(endedAt)));
    }

    private void requestArchiveIfPending(List<SeasonEvent> events) {
        if (state.status() == SeasonStatus.ENDED && !archiveRequested) {
            archiveRequested = true;
            events.add(event(SeasonEvent.Type.ARCHIVE_REQUIRED, state.schedule().dayOf(state.endedAt())));
        }
    }

    private SeasonEvent event(SeasonEvent.Type type, int day) {
        return new SeasonEvent(type, state.seasonId(), day);
    }

    private void update(SeasonState next) {
        if (!next.equals(state)) {
            state = next;
            revision++;
        }
    }
}
