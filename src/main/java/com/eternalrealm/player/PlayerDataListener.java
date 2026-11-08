package com.eternalrealm.player;

import com.eternalrealm.character.ExperienceResult;

import java.util.UUID;

/**
 * Callbacks from {@link PlayerDataService} to the platform layer (network sync, chat feedback, kicking).
 * Always invoked on the server thread.
 */
public interface PlayerDataListener {
    /** The player's data finished loading; gameplay requests are accepted from now on. */
    default void onCharacterReady(UUID playerId) {
    }

    /** Character state changed and should be re-synchronised to the owning client. */
    default void onCharacterChanged(UUID playerId) {
    }

    default void onLevelUp(UUID playerId, ExperienceResult result) {
    }

    /**
     * Loading failed. The player must be disconnected: continuing would let them play on data that cannot be saved
     * without overwriting their real progress.
     *
     * @param incidentId short id that is also written to the server log
     */
    default void onLoadFailed(UUID playerId, String incidentId) {
    }
}
