package com.eternalrealm.client;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatPercents;
import com.eternalrealm.network.forge.CharacterSnapshotPacket;

import java.util.Optional;

/**
 * Last character snapshot received from the server. The client never computes or modifies these values; it only
 * displays them. Accessed on the client thread only (render thread and packet handlers queued onto it).
 */
public final class ClientCharacterState {
    private static CharacterSnapshot snapshot;
    private static StatBlock bonusStats = StatBlock.ZERO;
    private static StatPercents statPercents = StatPercents.ZERO;

    private ClientCharacterState() {
    }

    public static Optional<CharacterSnapshot> snapshot() {
        return Optional.ofNullable(snapshot);
    }

    /** Stats the job and the equipment add on top of the snapshot's own totals (flat and percentages together). */
    public static StatBlock bonusStats() {
        return bonusStats;
    }

    /** The equipment's stat percentages (STR +250 %: what multiplies the flat stats). */
    public static StatPercents statPercents() {
        return statPercents;
    }

    public static void apply(CharacterSnapshotPacket packet) {
        CharacterSnapshot previous = snapshot;
        if (previous != null && packet.snapshot().level() > previous.level()) {
            com.eternalrealm.client.sound.MusicDirector.playJingle("level_up");
        }
        snapshot = packet.snapshot();
        bonusStats = packet.bonusStats();
        statPercents = packet.statPercents();
        ClientCombatState.onCharacter(snapshot);
    }

    public static void clear() {
        snapshot = null;
        bonusStats = StatBlock.ZERO;
        statPercents = StatPercents.ZERO;
    }
}
