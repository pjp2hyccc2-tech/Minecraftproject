package com.eternalrealm.network.forge;

import com.eternalrealm.character.CharacterSnapshot;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Indirection between common packet classes and client-only code.
 *
 * <p>Packet classes are loaded on both sides, so they must not reference client classes. Instead the client module
 * installs its handlers here during client setup. On a dedicated server nothing is installed and client-bound
 * packets are never received there anyway.</p>
 */
public final class ClientboundHandlers {
    private static volatile Consumer<CharacterSnapshot> characterSnapshotHandler = snapshot -> {
    };

    private ClientboundHandlers() {
    }

    public static void setCharacterSnapshotHandler(Consumer<CharacterSnapshot> handler) {
        characterSnapshotHandler = Objects.requireNonNull(handler, "handler");
    }

    static void onCharacterSnapshot(CharacterSnapshot snapshot) {
        characterSnapshotHandler.accept(snapshot);
    }
}
