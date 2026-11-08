package com.eternalrealm.player;

/**
 * Lifecycle of an online player's session.
 *
 * <pre>
 *   join ──► LOADING ──(data loaded)──► ACTIVE ──(leave)──► (removed, data saved)
 *               │
 *               └──(load failed)──► FAILED ──(player is disconnected)──► (removed, nothing saved)
 * </pre>
 *
 * Gameplay requests are only accepted while {@link #ACTIVE}.
 */
public enum SessionState {
    LOADING,
    ACTIVE,
    FAILED
}
