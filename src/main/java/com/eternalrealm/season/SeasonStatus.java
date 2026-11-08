package com.eternalrealm.season;

import com.eternalrealm.core.ModInfo;

import java.util.List;
import java.util.Optional;

/**
 * Persisted lifecycle of a season: {@code PREPARING → ACTIVE → ENDED → ARCHIVED}, never backwards
 * ({@link SeasonManager}). {@link #id()} is the stable persistence key.
 */
public enum SeasonStatus {
    /** Waiting for the scheduled start. */
    PREPARING("preparing"),
    /** Running: progress and rankings count. */
    ACTIVE("active"),
    /** Over: rankings are final, the archive has not been written yet. */
    ENDED("ended"),
    /** The season is in the hall of fame; the next season may be prepared. */
    ARCHIVED("archived");

    private static final List<SeasonStatus> ALL = List.of(values());

    private final String id;

    SeasonStatus(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "season_status." + ModInfo.MOD_ID + "." + id;
    }

    public static List<SeasonStatus> all() {
        return ALL;
    }

    public static Optional<SeasonStatus> byId(String id) {
        for (SeasonStatus status : ALL) {
            if (status.id.equals(id)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}
