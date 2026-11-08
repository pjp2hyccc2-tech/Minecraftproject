package com.eternalrealm.dungeon;

import com.eternalrealm.core.ModInfo;

/** Why a run failed. {@link #id()} is the stable key for logs and records. */
public enum FailureReason {
    /** The time limit ran out before the boss died. */
    TIME_LIMIT("time_limit"),
    /** Every member left the instance (logged out, teleported away) for longer than the grace period. */
    EVERYONE_LEFT("everyone_left"),
    /** The party died more often than the configured limit. */
    TOO_MANY_DEATHS("too_many_deaths"),
    /** Closed before it finished (server stop, admin command). */
    ABORTED("aborted");

    private final String id;

    FailureReason(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Message shown to the members. */
    public String messageKey() {
        return "message." + ModInfo.MOD_ID + ".dungeon.failed." + id;
    }
}
