package com.eternalrealm.dungeon;

/**
 * Lifecycle of one instance:
 * {@code CREATED -> BUILDING -> READY -> IN_PROGRESS -> BOSS -> CLEARED -> CLOSED}; any active state can end in
 * {@code FAILED -> CLOSED}.
 */
public enum RunState {
    /** Slot allocated, members fixed, nothing built yet. */
    CREATED,
    /** The platform is placing the blocks of the layout. */
    BUILDING,
    /** Built; members can enter the entrance hall and wait for the start. */
    READY,
    /** Fighting through the rooms (see {@link DungeonRun#currentRoom()} and {@link DungeonRun#currentWave()}). */
    IN_PROGRESS,
    /** All rooms cleared; the boss fight is on. */
    BOSS,
    /** The boss was defeated: rewards and records. */
    CLEARED,
    /** Time limit, everyone gone, too many deaths, or closed early. */
    FAILED,
    /** Torn down: the slot can be reused. */
    CLOSED;

    /** Whether the run is over (cleared, failed or closed). */
    public boolean isFinished() {
        return this == CLEARED || this == FAILED || this == CLOSED;
    }

    /** Whether the clock is running (the time limit applies). */
    public boolean isRunning() {
        return this == IN_PROGRESS || this == BOSS;
    }
}
