package com.eternalrealm.combat.forge;

import com.eternalrealm.combat.Telegraph;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.TelegraphPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ground telegraphs for everything that hits an area after a warning: bosses, dungeon traps, big monster moves. Shows
 * the marker to the players around it (or a given audience), then {@link #fire} when the hit happens (the marker
 * flashes and disappears) or {@link #cancel} it. The caller still decides who is hit, usually with
 * {@link Telegraph#contains}. Server thread only.
 */
public final class Telegraphs {
    /** Players this far from a marker's centre (plus its size) see it. */
    public static final double AUDIENCE_RANGE = 64.0;
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    /** Who was shown which marker, so fire/cancel reach exactly them. */
    private static final Map<Integer, List<UUID>> AUDIENCES = new HashMap<>();

    private Telegraphs() {
    }

    /** Shows a marker to everyone near it. @return its id for {@link #fire}/{@link #cancel} */
    public static int show(ServerLevel level, Telegraph telegraph) {
        double range = AUDIENCE_RANGE + telegraph.boundingRadius();
        List<ServerPlayer> near = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - telegraph.x();
            double dz = player.getZ() - telegraph.z();
            if (dx * dx + dz * dz <= range * range && Math.abs(player.getY() - telegraph.y()) <= 48.0) {
                near.add(player);
            }
        }
        return show(near, telegraph);
    }

    /** Shows a marker to {@code players} (e.g. everyone in a boss fight). @return its id */
    public static int show(Collection<ServerPlayer> players, Telegraph telegraph) {
        int id = NEXT_ID.getAndIncrement();
        List<UUID> audience = new ArrayList<>(players.size());
        TelegraphPacket packet = new TelegraphPacket(id, TelegraphPacket.SHOW, telegraph);
        for (ServerPlayer player : players) {
            ERNetwork.sendToPlayer(player, packet);
            audience.add(player.getUUID());
        }
        AUDIENCES.put(id, audience);
        if (AUDIENCES.size() > 4096) {
            AUDIENCES.clear(); // markers expire on the client by themselves; never let this grow without bound
        }
        return id;
    }

    /** The hit happens now: the marker flashes and disappears. */
    public static void fire(ServerLevel level, int id) {
        finish(level, id, TelegraphPacket.FIRE);
    }

    /** The attack was called off (interrupted, the attacker died...). */
    public static void cancel(ServerLevel level, int id) {
        finish(level, id, TelegraphPacket.CANCEL);
    }

    private static void finish(ServerLevel level, int id, int action) {
        List<UUID> audience = AUDIENCES.remove(id);
        if (audience == null) {
            return;
        }
        TelegraphPacket packet = new TelegraphPacket(id, action, null);
        for (UUID playerId : audience) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                ERNetwork.sendToPlayer(player, packet);
            }
        }
    }
}
