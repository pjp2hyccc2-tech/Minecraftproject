package com.eternalrealm.season;

/**
 * Where an instant lies relative to a {@link SeasonSchedule}: a pure function of the clock. The persisted lifecycle
 * (which also records operator actions such as an early end) is {@link SeasonStatus}.
 */
public enum SeasonPhase {
    /** Before the start. */
    NOT_STARTED,
    /** From the start (inclusive) to the end (exclusive). */
    ACTIVE,
    /** From the end on. */
    ENDED
}
