package com.eternalrealm.client;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.character.StatBlock;
import com.eternalrealm.network.forge.CharacterSnapshotPacket;

import java.util.Optional;

/**
 * Last character snapshot received from the server. The client never computes or modifies these values; it only
 * displays them. Accessed on the client thread only (render thread and packet handlers queued onto it).
 */
public final class ClientCharacterState {
    private static CharacterSnapshot snapshot;
    private static StatBlock bonusStats = StatBlock.ZERO;

    private ClientCharacterState() {
    }

    public static Optional<CharacterSnapshot> snapshot() {
        return Optional.ofNullable(snapshot);
    }

    /** Stats the job and the equipment add on top of the snapshot's own totals. */
    public static StatBlock bonusStats() {
        return bonusStats;
    }

    public static void apply(CharacterSnapshotPacket packet) {
        CharacterSnapshot previous = snapshot;
        if (previous != null && packet.snapshot().level() > previous.level()) {
            com.eternalrealm.client.sound.MusicDirector.playJingle("level_up");
        }
        snapshot = packet.snapshot();
        bonusStats = packet.bonusStats();
        ClientCombatState.onCharacter(snapshot);
    }

    public static void clear() {
        snapshot = null;
        bonusStats = StatBlock.ZERO;
    }
}
