package com.eternalrealm.season;

import java.time.Instant;
import java.util.Objects;

/**
 * Persisted state of the current season ({@code season_state.json}), owned by {@link SeasonManager}. It records which
 * lifecycle events have already been emitted, so that each is emitted exactly once even across restarts.
 *
 * <p>The constructor enforces the invariants of each status, so a corrupt file is rejected instead of producing
 * repeated or skipped announcements.</p>
 *
 * @param lastAnnouncedDay      0 while preparing; afterwards the last season day announced (DAY 1 is announced by
 *                              the start itself)
 * @param finalBossFirstClearAt when the final boss was first defeated, or {@code null}
 * @param endedAt               when the season ended (scheduled end, or an operator's early end), or {@code null}
 * @param archivedAt            when the season was archived to the hall of fame, or {@code null}
 */
public record SeasonState(SeasonSchedule schedule, SeasonStatus status, int lastAnnouncedDay,
                          Instant finalBossFirstClearAt, Instant endedAt, Instant archivedAt) {
    public SeasonState {
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(status, "status");
        if (status == SeasonStatus.PREPARING) {
            if (lastAnnouncedDay != 0 || finalBossFirstClearAt != null || endedAt != null || archivedAt != null) {
                throw new IllegalArgumentException("A preparing season has no announced day, clear, end or archive");
            }
        } else {
            if (lastAnnouncedDay < 1 || lastAnnouncedDay > schedule.lengthDays()) {
                throw new IllegalArgumentException("lastAnnouncedDay out of range [1, " + schedule.lengthDays()
                        + "]: " + lastAnnouncedDay);
            }
            boolean ended = status == SeasonStatus.ENDED || status == SeasonStatus.ARCHIVED;
            if (ended != (endedAt != null)) {
                throw new IllegalArgumentException("endedAt must be set exactly for ended and archived seasons");
            }
            if ((status == SeasonStatus.ARCHIVED) != (archivedAt != null)) {
                throw new IllegalArgumentException("archivedAt must be set exactly for archived seasons");
            }
        }
    }

    /** A season waiting for its start. */
    public static SeasonState preparing(SeasonSchedule schedule) {
        return new SeasonState(schedule, SeasonStatus.PREPARING, 0, null, null, null);
    }

    public SeasonId seasonId() {
        return schedule.id();
    }
}
