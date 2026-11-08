package com.eternalrealm.world.forge;

import com.eternalrealm.world.gen.AstoriaAreas;
import com.eternalrealm.world.gen.HubSpot;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Travel into and inside アストリア. Teleports load (and if needed generate) the destination chunk first and build the
 * capital or the dungeon entrance when it does not exist yet. Login/respawn wiring is done by the integrator.
 */
public final class AstoriaTravel {
    private static final Logger LOG = LogUtils.getLogger();

    private AstoriaTravel() {
    }

    /** The アストリア level of a server, if the dimension is loaded. */
    public static Optional<ServerLevel> astoria(MinecraftServer server) {
        return Optional.ofNullable(server.getLevel(AstoriaDimension.LEVEL));
    }

    /**
     * Teleports a player to the spawn point of the capital (building the capital first if needed).
     *
     * @return false if the server has no アストリア dimension
     */
    public static boolean sendToHub(ServerPlayer player) {
        ServerLevel astoria = player.getServer() == null ? null : player.getServer().getLevel(AstoriaDimension.LEVEL);
        if (astoria == null) {
            LOG.warn("Cannot send {} to the capital: dimension {} is not loaded", player.getGameProfile().getName(),
                    AstoriaDimension.LEVEL.location());
            return false;
        }
        HubLayout layout = HubBuilder.ensureBuilt(astoria);
        teleport(player, astoria, layout.spawnPoint(), layout.yaw(HubSpot.SPAWN));
        return true;
    }

    /**
     * Teleports a player to the waypoint of a dungeon entrance (building the entrance first if needed).
     *
     * @return false if the server has no アストリア dimension
     * @throws IllegalArgumentException for an unknown dungeon id
     */
    public static boolean sendToWaypoint(ServerPlayer player, String dungeonId) {
        ServerLevel astoria = player.getServer() == null ? null : player.getServer().getLevel(AstoriaDimension.LEVEL);
        if (astoria == null) {
            return false;
        }
        LandmarkBuilder.ensureBuilt(astoria, dungeonId);
        teleport(player, astoria, Landmarks.waypoint(astoria, dungeonId), Landmarks.gateFacingYaw(dungeonId));
        return true;
    }

    /** Teleports a player to a feet position in アストリア, loading the chunk first. */
    public static void teleport(ServerPlayer player, ServerLevel astoria, BlockPos feet, float yaw) {
        astoria.getChunk(feet.getX() >> 4, feet.getZ() >> 4);
        player.fallDistance = 0.0F;
        player.teleportTo(astoria, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, yaw, 0.0F);
    }

    public static boolean isInAstoria(Entity entity) {
        return entity.level().dimension() == AstoriaDimension.LEVEL;
    }

    /** Area 1..7 at a horizontal position (the exact rings of {@link AstoriaAreas}). */
    public static int areaAt(double x, double z) {
        return AstoriaAreas.areaAt(x, z);
    }
}
