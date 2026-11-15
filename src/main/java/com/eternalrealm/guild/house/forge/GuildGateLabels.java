package com.eternalrealm.guild.house.forge;

import com.eternalrealm.guild.Guild;
import com.eternalrealm.guild.GuildPerks;
import com.eternalrealm.guild.house.GuildHouseCatalog;
import com.eternalrealm.guild.house.GuildHouseDesign;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiData;
import com.eternalrealm.world.forge.AstoriaTravel;
import com.eternalrealm.world.forge.Capital;
import com.eternalrealm.world.forge.HubLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.UUID;

/**
 * Which gate of the Guild Gate Terrace is the player's (owner, 2026-11-15: 「ギルドハウスの門がどれが自分のギルドの
 * ハウスか分からないからなんかわかるように工夫して、名前つけるとか」). Every player in the capital is told where the
 * twenty gates stand and which of them leads to their guild's house (UI channel {@value #CHANNEL}); the client hangs
 * each gate's name over it and the guild's own name, a beam of light and 「あなたのギルドの門」 over theirs, marks it on
 * the capital's map and can lay the guide's dots to it (docs/modules/guildhouse.md §gate labels).
 *
 * <p>The document is sent when the player comes to the capital and again whenever it changes (the guild bought,
 * switched or sold its house, the player joined or left a guild, the guild's standing rose): looked at every two
 * seconds, sent only when different.</p>
 */
public final class GuildGateLabels {
    public static final String CHANNEL = "guildhouse.gates";
    /** Spot names of the gates on the terrace: {@code guildgate.<design>}. */
    static final String PREFIX = "guildgate.";
    private static final int PERIOD = 40;
    /** What each player in the capital was last sent. */
    private static final Map<UUID, String> SENT = new HashMap<>();

    private GuildGateLabels() {
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % PERIOD != 23) {
            return;
        }
        MinecraftServer server = event.getServer();
        Optional<ServerLevel> astoria = AstoriaTravel.astoria(server);
        if (astoria.isEmpty()) {
            SENT.clear();
            return;
        }
        HubLayout layout = Capital.layout(astoria.get());
        SortedMap<String, BlockPos> gates = layout.namedWithPrefix(PREFIX);
        Set<UUID> here = new HashSet<>();
        for (ServerPlayer player : astoria.get().players()) {
            here.add(player.getUUID());
            JsonObject document = document(player, layout, gates);
            String text = document.toString();
            if (!text.equals(SENT.get(player.getUUID()))) {
                SENT.put(player.getUUID(), text);
                UiData.send(player, CHANNEL, document);
            }
        }
        // whoever left the capital is told again when they come back
        SENT.keySet().retainAll(here);
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        SENT.clear();
    }

    /**
     * The gates (design, name key, feet position at the centre of the portal, the way one faces walking in) and,
     * for a member of a guild with a house, the guild (name, tag, the colour of its standing) and its design.
     */
    static JsonObject document(ServerPlayer player, HubLayout layout, SortedMap<String, BlockPos> gates) {
        JsonObject data = new JsonObject();
        data.addProperty("dim", player.level().dimension().location().toString());
        GuildHouseCatalog catalog = GuildHouses.get().map(h -> h.service().catalog())
                .orElseGet(GuildHouseCatalog::standard);
        JsonArray list = new JsonArray();
        for (Map.Entry<String, BlockPos> entry : gates.entrySet()) {
            String design = entry.getKey().substring(PREFIX.length());
            BlockPos pos = entry.getValue();
            JsonObject gate = new JsonObject();
            gate.addProperty("design", design);
            gate.addProperty("name", catalog.design(design).map(GuildHouseDesign::nameKey).orElse(design));
            gate.addProperty("x", pos.getX());
            gate.addProperty("y", pos.getY());
            gate.addProperty("z", pos.getZ());
            gate.addProperty("yaw", layout.namedYaw(entry.getKey(), 0.0F));
            list.add(gate);
        }
        data.add("gates", list);
        Optional<Guild> guild = GuildHouses.guildOf(player);
        guild.ifPresent(g -> {
            data.addProperty("guild", g.name());
            data.addProperty("tag", g.tag());
            int level = ServerContext.current().map(c -> c.game().guilds().service().level(g)).orElse(1);
            data.addProperty("color", GuildPerks.standing(level).color());
            GuildHouses.get().flatMap(h -> h.service().design(g.id()))
                    .ifPresent(design -> data.addProperty("own", design.id()));
        });
        return data;
    }
}
