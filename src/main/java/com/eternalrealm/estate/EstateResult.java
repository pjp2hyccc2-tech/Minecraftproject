package com.eternalrealm.estate;

import com.eternalrealm.core.ModInfo;

/**
 * What an estate request came to. {@link #messageKey()} is the line the player is told (argument 1, where used: the
 * build progress in percent).
 */
public enum EstateResult {
    OK("ok"),
    /** The kind and owner already have an estate (claim). */
    ALREADY_CLAIMED("already_claimed"),
    /** There is no estate of that kind and owner. */
    NOT_CLAIMED("not_claimed"),
    /** No blueprint {@code estates/<kind>/<design>} ships with the mod. */
    UNKNOWN_DESIGN("unknown_design"),
    /** Every plot of the kind's region is taken. */
    NO_PLOT("no_plot"),
    /** The estate is still being built (argument: percent). */
    BUILDING("building"),
    /** The player may not enter or use this estate. */
    NOT_ALLOWED("not_allowed"),
    /** The estates dimension or the server's data is not available (yet). */
    UNAVAILABLE("unavailable"),
    /** A redesign to the design the estate already has. */
    SAME_DESIGN("same_design"),
    /** Things are still stored in the estate. */
    NOT_EMPTY("not_empty"),
    /** A redesign is already under way. */
    BUSY("busy");

    private final String id;

    EstateResult(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public boolean ok() {
        return this == OK;
    }

    public String messageKey() {
        return "message." + ModInfo.MOD_ID + ".estate." + id;
    }
}
