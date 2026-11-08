package com.eternalrealm.combat.forge;

import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.server.ServerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Decides whether a guard (right click with a weapon) may start. Common code: on the server it asks the authoritative
 * combat state; on the client it asks the client's predicted state through a gate installed by the client module
 * (this class must not reference client classes).
 */
public final class GuardGate {
    private static volatile BiPredicate<Player, WeaponProfile> clientGate = (player, weapon) -> false;

    private GuardGate() {
    }

    /** Client setup only. */
    public static void setClientGate(BiPredicate<Player, WeaponProfile> gate) {
        clientGate = Objects.requireNonNull(gate, "gate");
    }

    static boolean tryStartGuard(Player player, WeaponProfile weapon) {
        if (player.level().isClientSide()) {
            return clientGate.test(player, weapon);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        return ServerContext.current()
                .map(context -> context.combat().startGuard(serverPlayer, weapon))
                .orElse(false);
    }
}
