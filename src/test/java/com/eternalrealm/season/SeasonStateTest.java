package com.eternalrealm.season;

import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SeasonStateTest {
    private static final SeasonSchedule SCHEDULE = SeasonSchedule.of(SeasonId.of("s1"), Instant.parse("2026-11-14T01:00:00Z"));
    private static final Instant AT = Instant.parse("2026-11-20T00:00:00Z");

    private static SeasonState state(SeasonStatus status, int day, Instant clear, Instant ended, Instant archived) {
        return new SeasonState(SCHEDULE, status, day, clear, ended, archived);
    }

    @Test
    public void validStatesOfEveryStatus() {
        assertEquals(SeasonStatus.PREPARING, SeasonState.preparing(SCHEDULE).status());
        assertEquals(SCHEDULE.id(), SeasonState.preparing(SCHEDULE).seasonId());
        state(SeasonStatus.ACTIVE, 1, null, null, null);
        state(SeasonStatus.ACTIVE, 14, AT, null, null);
        state(SeasonStatus.ENDED, 3, null, AT, null);
        state(SeasonStatus.ARCHIVED, 14, AT, AT, AT);
    }

    @Test
    public void eachStatusHasItsInvariants() {
        assertThrows(IllegalArgumentException.class, () -> state(SeasonStatus.PREPARING, 1, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> state(SeasonStatus.PREPARING, 0, AT, null, null));
        assertThrows(IllegalArgumentException.class, () -> state(SeasonStatus.PREPARING, 0, null, AT, null));
        assertThrows(IllegalArgumentException.class, () -> state(SeasonStatus.ACTIVE, 0, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> state(SeasonStatus.ACTIVE, 15, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> state(SeasonStatus.ACTIVE, 2, null, AT, null));
        assertThrows(IllegalArgumentException.class, () -> state(SeasonStatus.ACTIVE, 2, null, null, AT));
        assertThrows(IllegalArgumentException.class, () -> state(SeasonStatus.ENDED, 2, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> state(SeasonStatus.ENDED, 2, null, AT, AT));
        assertThrows(IllegalArgumentException.class, () -> state(SeasonStatus.ARCHIVED, 2, null, AT, null));
        assertThrows(IllegalArgumentException.class, () -> state(SeasonStatus.ARCHIVED, 2, null, null, AT));
        assertThrows(NullPointerException.class, () -> new SeasonState(null, SeasonStatus.ACTIVE, 1, null, null, null));
        assertThrows(NullPointerException.class, () -> new SeasonState(SCHEDULE, null, 1, null, null, null));
    }

    @Test
    public void statusesHaveStableIdsAndTranslationKeys() {
        assertEquals(List.of("preparing", "active", "ended", "archived"),
                SeasonStatus.all().stream().map(SeasonStatus::id).toList());
        for (SeasonStatus status : SeasonStatus.all()) {
            assertEquals(Optional.of(status), SeasonStatus.byId(status.id()));
            assertEquals("season_status.eternalrealm." + status.id(), status.translationKey());
        }
        assertTrue(SeasonStatus.byId("ACTIVE").isEmpty());
    }

    @Test
    public void eventsCarryAValidDayAndAnnouncementKeys() {
        assertThrows(IllegalArgumentException.class, () -> new SeasonEvent(SeasonEvent.Type.STARTED, SCHEDULE.id(), 0));
        assertThrows(NullPointerException.class, () -> new SeasonEvent(null, SCHEDULE.id(), 1));
        assertEquals(Optional.of("message.eternalrealm.season.day_changed"), SeasonEvent.Type.DAY_CHANGED.announcementKey());
        assertTrue("internal work request, not shown to players", SeasonEvent.Type.ARCHIVE_REQUIRED.announcementKey().isEmpty());
    }
}
