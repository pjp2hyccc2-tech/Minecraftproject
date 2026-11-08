package com.eternalrealm.sound.forge;

import com.eternalrealm.dungeon.forge.DungeonLevels;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiData;
import com.eternalrealm.world.AreaDefinition;
import com.eternalrealm.world.forge.Realm;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tells every client which music fits where its player is (the client's music director plays and crossfades it):
 * the capital, the area, a dungeon, a boss or the final boss. Also sends the jingles of game events.
 */
public final class MusicContexts {
    public static final String MUSIC = "music";
    public static final String JINGLE = "jingle";
    private static final Map<UUID, String> SENT = new HashMap<>();

    private MusicContexts() {
    }

    /** Once per second. */
    public static void tickSecond(MinecraftServer server, ServerContext context) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String music = contextOf(player, context);
            if (!music.equals(SENT.get(player.getUUID()))) {
                SENT.put(player.getUUID(), music);
                JsonObject data = new JsonObject();
                data.addProperty("context", music);
                UiData.send(player, MUSIC, data);
            }
        }
    }

    static String contextOf(ServerPlayer player, ServerContext context) {
        if (DungeonLevels.isDungeonLevel(player.level())) {
            return context.game().dungeons().fightFor(player)
                    .map(fight -> "final".equals(fight.bossId()) ? "final_boss" : "boss").orElse("dungeon");
        }
        if (!Realm.isRealm(player.level())) {
            return "none";
        }
        if (Realm.inCapital(player.serverLevel(), player.getX(), player.getZ())) {
            return "hub";
        }
        return context.game().world().areaOf(player).map(AreaDefinition::id).map(id -> "area_" + id).orElse("none");
    }

    /** A jingle for one player ("victory", "quest_complete", "discovery", "ending"). */
    public static void jingle(ServerPlayer player, String id) {
        JsonObject data = new JsonObject();
        data.addProperty("id", id);
        data.addProperty("nonce", System.nanoTime());
        UiData.send(player, JINGLE, data);
    }

    public static void forget(UUID playerId) {
        SENT.remove(playerId);
    }
}
