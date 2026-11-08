package com.eternalrealm.season;

import com.eternalrealm.core.ModInfo;

import java.util.Objects;
import java.util.Optional;

/**
 * Something that happened to the season, reported by {@link SeasonManager} in lifecycle order:
 * {@code STARTED, DAY_CHANGED, FINAL_DAY, ENDED, ARCHIVE_REQUIRED}.
 *
 * @param day the season day the event refers to (1-based): 1 for STARTED, the new day for DAY_CHANGED, the last day
 *            for FINAL_DAY and a scheduled end, the day of an operator's early end
 */
public record SeasonEvent(Type type, SeasonId seasonId, int day) {
    public enum Type {
        /** The season began (DAY 1). Emitted exactly once. */
        STARTED("started"),
        /** A new season day began (DAY 2 or later). Emitted once per announced day; skipped days are not repeated. */
        DAY_CHANGED("day_changed"),
        /** The last day began. Emitted once, together with its DAY_CHANGED (or STARTED for a one-day season). */
        FINAL_DAY("final_day"),
        /** The season is over: freeze the rankings. Emitted exactly once. */
        ENDED("ended"),
        /**
         * Work request: write the season's archive to the hall of fame, then call
         * {@link SeasonManager#markArchived}. Emitted once per manager instance until acknowledged, i.e. again after a
         * restart if the archive was not confirmed (appending to the hall of fame is idempotent).
         */
        ARCHIVE_REQUIRED(null);

        private final String announcementId;

        Type(String announcementId) {
            this.announcementId = announcementId;
        }

        /**
         * Translation key of the player announcement ({@code message.eternalrealm.season.<event>}; args: season id,
         * day, season length), or empty for internal events.
         */
        public Optional<String> announcementKey() {
            return announcementId == null ? Optional.empty()
                    : Optional.of("message." + ModInfo.MOD_ID + ".season." + announcementId);
        }
    }

    public SeasonEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(seasonId, "seasonId");
        if (day < 1) {
            throw new IllegalArgumentException("day must be >= 1: " + day);
        }
    }
}
