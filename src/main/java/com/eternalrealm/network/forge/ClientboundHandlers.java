package com.eternalrealm.network.forge;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.combat.CombatStats;

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
    private static volatile Consumer<CombatStats> combatStatsHandler = stats -> {
    };
    private static volatile Consumer<CombatStatusPacket> combatStatusHandler = status -> {
    };
    private static volatile Consumer<DamageNumberPacket> damageNumberHandler = number -> {
    };

    private ClientboundHandlers() {
    }

    public static void setCharacterSnapshotHandler(Consumer<CharacterSnapshot> handler) {
        characterSnapshotHandler = Objects.requireNonNull(handler, "handler");
    }

    public static void setCombatStatsHandler(Consumer<CombatStats> handler) {
        combatStatsHandler = Objects.requireNonNull(handler, "handler");
    }

    public static void setCombatStatusHandler(Consumer<CombatStatusPacket> handler) {
        combatStatusHandler = Objects.requireNonNull(handler, "handler");
    }

    public static void setDamageNumberHandler(Consumer<DamageNumberPacket> handler) {
        damageNumberHandler = Objects.requireNonNull(handler, "handler");
    }

    static void onCharacterSnapshot(CharacterSnapshot snapshot) {
        characterSnapshotHandler.accept(snapshot);
    }

    static void onCombatStats(CombatStats stats) {
        combatStatsHandler.accept(stats);
    }

    static void onCombatStatus(CombatStatusPacket status) {
        combatStatusHandler.accept(status);
    }

    static void onDamageNumber(DamageNumberPacket number) {
        damageNumberHandler.accept(number);
    }
}
