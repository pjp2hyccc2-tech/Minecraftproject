package com.eternalrealm.combat.forge;

import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.server.ServerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Decides whether a guard (right click with a weapon) may start. Only the server decides (D-035): a client-side guard
 * prediction could disagree with the server at the edge of an action and leave a guard pose that does not protect.
 * The client shows the guard when vanilla syncs the server's "using item" state, a round trip later.
 */
public final class GuardGate {
    private GuardGate() {
    }

    static boolean tryStartGuard(Player player, WeaponProfile weapon) {
        if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        return ServerContext.current()
                .map(context -> context.combat().startGuard(serverPlayer, weapon))
                .orElse(false);
    }
}
