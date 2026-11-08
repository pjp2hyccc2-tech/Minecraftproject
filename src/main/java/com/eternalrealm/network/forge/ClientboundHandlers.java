package com.eternalrealm.network.forge;

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
    private static volatile Consumer<CharacterSnapshotPacket> characterSnapshotHandler = snapshot -> {
    };
    private static volatile java.util.function.BiConsumer<String, String> uiDataHandler = (channel, json) -> {
    };
    private static volatile Consumer<PlayerActionPacket> playerActionHandler = action -> {
    };
    private static volatile Consumer<CombatStats> combatStatsHandler = stats -> {
    };
    private static volatile Consumer<CombatStatusPacket> combatStatusHandler = status -> {
    };
    private static volatile Consumer<DamageNumberPacket> damageNumberHandler = number -> {
    };

    private ClientboundHandlers() {
    }

    public static void setCharacterSnapshotHandler(Consumer<CharacterSnapshotPacket> handler) {
        characterSnapshotHandler = Objects.requireNonNull(handler, "handler");
    }

    public static void setUiDataHandler(java.util.function.BiConsumer<String, String> handler) {
        uiDataHandler = Objects.requireNonNull(handler, "handler");
    }

    public static void setPlayerActionHandler(Consumer<PlayerActionPacket> handler) {
        playerActionHandler = Objects.requireNonNull(handler, "handler");
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

    static void onCharacterSnapshot(CharacterSnapshotPacket snapshot) {
        characterSnapshotHandler.accept(snapshot);
    }

    static void onUiData(String channel, String json) {
        uiDataHandler.accept(channel, json);
    }

    static void onPlayerAction(PlayerActionPacket packet) {
        playerActionHandler.accept(packet);
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
