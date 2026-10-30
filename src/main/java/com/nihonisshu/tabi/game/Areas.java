package com.nihonisshu.tabi.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nihonisshu.tabi.data.TabiData;
import com.nihonisshu.tabi.registry.TabiWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Named places (exported by the builder): show a title on entry, unlock the journal, mark buildable zones. */
public final class Areas {
    private Areas() {}

    public record Area(String id, String name, String sub, int x1, int y1, int z1, int x2, int y2, int z2, String journal, boolean build,
                       String nightJournal) {
        boolean contains(double x, double y, double z) {
            return x >= x1 && x <= x2 + 1 && y >= y1 && y <= y2 + 1 && z >= z1 && z <= z2 + 1;
        }

        long volume() {
            return (long) (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
        }
    }

    private static List<Area> AREAS;
    private static final Map<UUID, String> CURRENT = new HashMap<>();

    public static synchronized List<Area> all() {
        if (AREAS == null) {
            AREAS = new ArrayList<>();
            JsonArray arr = TabiData.meta().getAsJsonArray("areas");
            if (arr != null) {
                for (JsonElement e : arr) {
                    JsonObject o = e.getAsJsonObject();
                    JsonArray b = o.getAsJsonArray("box");
                    AREAS.add(new Area(TabiData.str(o, "id", "?"), TabiData.str(o, "name", ""), TabiData.str(o, "sub", ""),
                            b.get(0).getAsInt(), b.get(1).getAsInt(), b.get(2).getAsInt(), b.get(3).getAsInt(), b.get(4).getAsInt(), b.get(5).getAsInt(),
                            TabiData.str(o, "journal", ""), o.has("build") && o.get("build").getAsBoolean(),
                            TabiData.str(o, "night_journal", "")));
                }
            }
        }
        return AREAS;
    }

    public static Area at(double x, double y, double z) {
        Area best = null;
        for (Area a : all()) {
            if (a.build) continue;
            if (a.contains(x, y, z) && (best == null || a.volume() < best.volume())) best = a;
        }
        return best;
    }

    public static String current(Player p) {
        return CURRENT.getOrDefault(p.getUUID(), "");
    }

    public static boolean buildable(Level level, BlockPos pos) {
        if (level.dimension() != TabiWorldgen.JAPAN) return true;
        for (Area a : all()) {
            if (a.build && a.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) return true;
        }
        return false;
    }

    public static void tick(ServerPlayer p) {
        if (p.level().dimension() != TabiWorldgen.JAPAN) return;
        Area a = at(p.getX(), p.getY(), p.getZ());
        String id = a == null ? "" : a.id;
        String prev = CURRENT.getOrDefault(p.getUUID(), "");
        if (a != null && !a.nightJournal.isEmpty()) {
            long t = p.level().getDayTime() % 24000L;
            if (t > 13000 && t < 23000) PlayerData.unlock(p, a.nightJournal);
        }
        if (id.equals(prev)) return;
        CURRENT.put(p.getUUID(), id);
        if (a == null) return;
        if (!a.name.isEmpty()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(a.name)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(a.sub)));
        }
        if (!a.journal.isEmpty()) PlayerData.unlock(p, a.journal);
    }
}
