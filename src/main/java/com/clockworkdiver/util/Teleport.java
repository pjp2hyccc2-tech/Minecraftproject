package com.clockworkdiver.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class Teleport {
    private Teleport() {}

    /** Moves a player to a position in any dimension, cancelling motion and fall damage. */
    public static void to(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        player.stopRiding();
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0f;
        player.teleportTo(level, x, y, z, yaw, pitch);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0f;
    }
}
