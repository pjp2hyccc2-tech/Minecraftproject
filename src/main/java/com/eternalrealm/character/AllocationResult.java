package com.eternalrealm.character;

import com.eternalrealm.core.ModInfo;

import java.util.Locale;

/**
 * Outcome of a stat point allocation request.
 */
public enum AllocationResult {
    SUCCESS,
    /** Player data is not loaded (still loading, or load failed). */
    NOT_READY,
    /** Amount outside {@code [1, maxPointsPerRequest]}. A legitimate client never sends this. */
    INVALID_AMOUNT,
    /** Not enough unspent points (can legitimately happen when a client double-clicks). */
    NOT_ENOUGH_POINTS,
    /** The stat would exceed {@link StatBlock#MAX_VALUE}. */
    STAT_LIMIT_REACHED;

    public boolean isSuccess() {
        return this == SUCCESS;
    }

    public String messageKey() {
        return "message." + ModInfo.MOD_ID + ".allocate." + name().toLowerCase(Locale.ROOT);
    }
}
