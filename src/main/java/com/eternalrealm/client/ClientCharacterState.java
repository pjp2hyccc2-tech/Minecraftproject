package com.eternalrealm.client;

import com.eternalrealm.character.CharacterSnapshot;

import java.util.Optional;

/**
 * Last character snapshot received from the server. The client never computes or modifies these values; it only
 * displays them. Accessed on the client thread only (render thread and packet handlers queued onto it).
 */
public final class ClientCharacterState {
    private static CharacterSnapshot snapshot;

    private ClientCharacterState() {
    }

    public static Optional<CharacterSnapshot> snapshot() {
        return Optional.ofNullable(snapshot);
    }

    public static void apply(CharacterSnapshot newSnapshot) {
        snapshot = newSnapshot;
    }

    public static void clear() {
        snapshot = null;
    }
}
