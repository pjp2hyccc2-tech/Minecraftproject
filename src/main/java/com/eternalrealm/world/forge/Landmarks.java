package com.eternalrealm.world.forge;

import com.eternalrealm.world.gen.LandmarkSite;
import com.eternalrealm.world.gen.LandmarkSites;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.function.Function;

/**
 * Positions at the dungeon entrances of アストリア ("d1".."d8"). All positions are feet positions (stand in the block,
 * floor below) and are valid before the entrance is built: the floor height is derived from the noise terrain exactly
 * as {@link LandmarkBuilder} uses it. The one-argument methods use the running server.
 *
 * <ul>
 *     <li>{@link #entrance}: in front of the gate's portal, where the dungeon is entered</li>
 *     <li>{@link #npc}: the marked spot for the dungeon NPC beside the gate</li>
 *     <li>{@link #waypoint}: centre of the waypoint pad (teleport destination)</li>
 * </ul>
 */
public final class Landmarks {
    private Landmarks() {
    }

    public static List<String> dungeonIds() {
        return LandmarkSites.all().stream().map(LandmarkSite::dungeonId).toList();
    }

    public static BlockPos entrance(String dungeonId) {
        return entrance(astoria(), dungeonId);
    }

    public static BlockPos entrance(ServerLevel astoria, String dungeonId) {
        return at(astoria, dungeonId, LandmarkSite::entrance);
    }

    public static BlockPos waypoint(String dungeonId) {
        return waypoint(astoria(), dungeonId);
    }

    public static BlockPos waypoint(ServerLevel astoria, String dungeonId) {
        return at(astoria, dungeonId, LandmarkSite::waypoint);
    }

    public static BlockPos npc(String dungeonId) {
        return npc(astoria(), dungeonId);
    }

    public static BlockPos npc(ServerLevel astoria, String dungeonId) {
        return at(astoria, dungeonId, LandmarkSite::npc);
    }

    /** Minecraft yaw facing the gate (for players arriving at the entrance or the waypoint). */
    public static float gateFacingYaw(String dungeonId) {
        LandmarkSite site = site(dungeonId);
        return site.worldYaw(site.waypoint().yaw());
    }

    /** Minecraft yaw for the dungeon NPC (facing arriving players). */
    public static float npcYaw(String dungeonId) {
        LandmarkSite site = site(dungeonId);
        return site.worldYaw(site.npc().yaw());
    }

    /** Area (1..7) of a dungeon entrance. */
    public static int area(String dungeonId) {
        return site(dungeonId).area();
    }

    static LandmarkSite site(String dungeonId) {
        return LandmarkSites.byId(dungeonId).orElseThrow(() -> new IllegalArgumentException("unknown dungeon " + dungeonId));
    }

    private static BlockPos at(ServerLevel astoria, String dungeonId, Function<LandmarkSite, LandmarkSite.Local> spot) {
        LandmarkSite site = site(dungeonId);
        LandmarkSite.Local local = spot.apply(site);
        int y = LandmarkBuilder.groundY(astoria, site) + local.dy();
        return new BlockPos(site.worldX(local.dx(), local.dz()), y, site.worldZ(local.dx(), local.dz()));
    }

    static ServerLevel astoria() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException("no server is running");
        }
        ServerLevel astoria = server.getLevel(AstoriaDimension.LEVEL);
        if (astoria == null) {
            throw new IllegalStateException("the dimension " + AstoriaDimension.LEVEL.location() + " is not loaded");
        }
        return astoria;
    }
}
