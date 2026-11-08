package com.eternalrealm.quest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Converts the server clock into the day index of the daily quests. A quest day starts at {@code resetTime} in the
 * server's time zone (default 05:00, so late-night players do not lose their dailies at midnight); the index is the
 * epoch day of the date on which the current quest day started.
 */
public final class DailyReset {
    public static final LocalTime DEFAULT_RESET_TIME = LocalTime.of(5, 0);

    private DailyReset() {
    }

    public static long dayIndex(Instant now, ZoneId zone, LocalTime resetTime) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(resetTime, "resetTime");
        ZonedDateTime local = now.atZone(zone);
        LocalDate date = local.toLocalTime().isBefore(resetTime) ? local.toLocalDate().minusDays(1) : local.toLocalDate();
        return date.toEpochDay();
    }
}
