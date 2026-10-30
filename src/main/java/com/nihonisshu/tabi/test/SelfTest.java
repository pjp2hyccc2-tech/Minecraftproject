package com.nihonisshu.tabi.test;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nihonisshu.tabi.TabiMod;
import com.nihonisshu.tabi.data.CarDesign;
import com.nihonisshu.tabi.data.Route;
import com.nihonisshu.tabi.data.TabiData;
import com.nihonisshu.tabi.entity.TabiNpc;
import com.nihonisshu.tabi.entity.VehicleEntity;
import com.nihonisshu.tabi.registry.TabiWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CI only (-Dtabi.selftest=true on the dedicated server): validates the data files, generates the hand-built areas,
 * checks blocks / NPCs, runs a train between two stations, writes tabi-selftest.json and stops the server.
 */
public final class SelfTest {
    private SelfTest() {}

    private static final boolean ENABLED = Boolean.getBoolean("tabi.selftest");
    private static final List<String> errors = new ArrayList<>();
    private static final List<String> info = new ArrayList<>();
    private static int tick = -1;
    private static VehicleEntity train;
    private static double trainStartS;
    private static String trainRoute = "";
    private static final Set<ChunkPos> forced = new HashSet<>();

    private static void err(String s) {
        errors.add(s);
        TabiMod.LOGGER.error("[tabi-selftest] FAIL {}", s);
    }

    private static void ok(String s) {
        info.add(s);
        TabiMod.LOGGER.info("[tabi-selftest] {}", s);
    }

    public static void start(MinecraftServer server) {
        if (!ENABLED) return;
        ok("starting");
        try {
            validateData();
        } catch (Throwable t) {
            err("data validation crashed: " + t);
        }
        ServerLevel level = server.getLevel(TabiWorldgen.JAPAN);
        if (level == null) {
            err("dimension tabi:japan missing");
            finish(server);
            return;
        }
        try {
            checkBlocks(level);
        } catch (Throwable t) {
            err("block checks crashed: " + t);
        }
        tick = 0;
    }

    private static void validateData() {
        JsonObject meta = TabiData.meta();
        if (meta.size() == 0) err("meta.json empty");
        // shops reference real items
        JsonObject shops = TabiData.shops().getAsJsonObject("shops");
        int shopItems = 0;
        if (shops != null) {
            for (Map.Entry<String, JsonElement> s : shops.entrySet()) {
                for (JsonElement it : s.getValue().getAsJsonObject().getAsJsonArray("items")) {
                    String id = TabiData.str(it.getAsJsonObject(), "item", "");
                    if (!ForgeRegistries.ITEMS.containsKey(new ResourceLocation(id))) err("shop " + s.getKey() + " unknown item " + id);
                    shopItems++;
                }
                String j = TabiData.str(s.getValue().getAsJsonObject(), "journal", "");
                if (!j.isEmpty() && !journalHas(j)) err("shop " + s.getKey() + " journal key missing " + j);
            }
        }
        ok("shops: " + (shops == null ? 0 : shops.size()) + " shops, " + shopItems + " items");
        // NPC profiles have dialogue or a shop
        Set<String> profiles = new HashSet<>();
        JsonArray npcs = meta.getAsJsonArray("npcs");
        if (npcs != null) for (JsonElement e : npcs) profiles.add(TabiData.str(e.getAsJsonObject(), "profile", ""));
        int lines = 0;
        for (String p : profiles) {
            if (p.isEmpty()) continue;
            JsonObject d = TabiData.dialogue(p);
            if (d.size() == 0) {
                err("no dialogue file for profile " + p);
                continue;
            }
            JsonArray entries = d.getAsJsonArray("entries");
            if (entries != null) {
                for (JsonElement e : entries) {
                    JsonObject en = e.getAsJsonObject();
                    JsonArray l = en.getAsJsonArray("lines");
                    if (l != null) lines += l.size();
                    checkActions(p, en.getAsJsonArray("actions"));
                    JsonArray ch = en.getAsJsonArray("choices");
                    if (ch != null) for (JsonElement c : ch) checkActions(p, c.getAsJsonObject().getAsJsonArray("actions"));
                }
            }
            String shop = TabiData.str(d, "shop", "");
            if (!shop.isEmpty() && (shops == null || !shops.has(shop))) err("profile " + p + " shop missing " + shop);
        }
        ok("npcs: " + (npcs == null ? 0 : npcs.size()) + " placed, " + profiles.size() + " profiles, " + lines + " dialogue lines");
        // bubble pools exist
        JsonObject pools = TabiData.bubbles().getAsJsonObject("pools");
        if (npcs != null) for (JsonElement e : npcs) {
            String b = TabiData.str(e.getAsJsonObject(), "bubbles", "");
            if (!b.isEmpty() && (pools == null || !pools.has(b))) err("bubble pool missing " + b);
        }
        // areas' journal keys
        JsonArray areas = meta.getAsJsonArray("areas");
        if (areas != null) for (JsonElement e : areas) {
            String j = TabiData.str(e.getAsJsonObject(), "journal", "");
            if (!j.isEmpty() && !journalHas(j)) err("area journal key missing " + j);
        }
        // routes + car designs
        for (Route r : Route.all()) {
            if (CarDesign.get(r.design) == null) err("route " + r.id + " car design missing " + r.design);
            for (Route.Station st : r.stations) {
                if (!journalHas("station." + st.id)) err("journal key missing station." + st.id);
            }
            if (!journalHas("ride." + r.id)) err("journal key missing ride." + r.id);
        }
        ok("routes: " + Route.all().size());
        // food items with journal keys
        for (var item : ForgeRegistries.ITEMS.getValues()) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id == null || !id.getNamespace().equals(TabiMod.MODID)) continue;
            if (item instanceof com.nihonisshu.tabi.item.TabiFoodItem f && !f.journalKey().isEmpty() && !journalHas(f.journalKey())) {
                err("food " + id + " journal key missing " + f.journalKey());
            }
        }
    }

    private static void checkActions(String profile, JsonArray actions) {
        if (actions == null) return;
        for (JsonElement a : actions) {
            JsonObject o = a.getAsJsonObject();
            if (o.has("give") || o.has("take")) {
                String id = o.has("give") ? o.get("give").getAsString() : o.get("take").getAsString();
                if (!ForgeRegistries.ITEMS.containsKey(new ResourceLocation(id))) err("dialogue " + profile + " unknown item " + id);
            }
            if (o.has("journal") && !journalHas(o.get("journal").getAsString())) err("dialogue " + profile + " journal key missing " + o.get("journal").getAsString());
            if (o.has("shop")) {
                JsonObject shops = TabiData.shops().getAsJsonObject("shops");
                if (shops == null || !shops.has(o.get("shop").getAsString())) err("dialogue " + profile + " shop missing " + o.get("shop").getAsString());
            }
        }
    }

    private static boolean journalHas(String key) {
        JsonObject e = TabiData.journal().getAsJsonObject("entries");
        return e != null && e.has(key);
    }

    private static void checkBlocks(ServerLevel level) {
        JsonArray checks = TabiData.meta().getAsJsonArray("checks");
        int passed = 0, n = 0;
        if (checks != null) {
            for (JsonElement e : checks) {
                JsonObject c = e.getAsJsonObject();
                JsonArray p = c.getAsJsonArray("pos");
                BlockPos pos = new BlockPos(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt());
                String want = TabiData.str(c, "block", "");
                BlockState st = level.getBlockState(pos);
                String got = BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
                n++;
                if (got.equals(want)) passed++;
                else err("block at " + pos.toShortString() + " (" + TabiData.str(c, "what", "") + ") expected " + want + " got " + got);
            }
        }
        ok("block checks " + passed + "/" + n);
        // keep the spawn area loaded so NPC entities appear
        JsonObject spawn = TabiData.meta().getAsJsonObject("spawn");
        if (spawn != null) {
            int sx = spawn.get("x").getAsInt() >> 4, sz = spawn.get("z").getAsInt() >> 4;
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) force(level, new ChunkPos(sx + dx, sz + dz));
        }
    }

    private static void force(ServerLevel level, ChunkPos cp) {
        if (forced.add(cp)) level.setChunkForced(cp.x, cp.z, true);
    }

    public static void tick(MinecraftServer server) {
        if (!ENABLED || tick < 0) return;
        tick++;
        ServerLevel level = server.getLevel(TabiWorldgen.JAPAN);
        if (level == null) return;
        try {
            if (tick == 100) {
                JsonObject spawn = TabiData.meta().getAsJsonObject("spawn");
                if (spawn != null) {
                    Vec3 c = new Vec3(spawn.get("x").getAsDouble(), spawn.get("y").getAsDouble(), spawn.get("z").getAsDouble());
                    int npcs = level.getEntitiesOfClass(TabiNpc.class, new AABB(c, c).inflate(48, 32, 48)).size();
                    if (npcs < 2) err("expected family NPCs near spawn, found " + npcs);
                    else ok("NPCs near spawn: " + npcs);
                }
                // set up a train at the second station of the first train route
                Route r = Route.all().stream().filter(x -> "train".equals(x.type) && x.stations.size() >= 3).findFirst().orElse(null);
                if (r == null) {
                    err("no train route with 3+ stations");
                } else {
                    Route.Station st = r.stations.get(1);
                    for (double s = Math.max(0, st.s - 120); s <= Math.min(r.length, st.s + 900); s += 12) {
                        Vec3 p = r.pos(s);
                        ChunkPos cp = new ChunkPos(BlockPos.containing(p));
                        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) force(level, new ChunkPos(cp.x + dx, cp.z + dz));
                    }
                    level.getChunk(BlockPos.containing(r.pos(st.s)));
                    train = VehicleEntity.spawnAt(level, r, st);
                    if (train != null) train.keepAlive = true;
                    trainStartS = st.s;
                    trainRoute = r.id;
                    ok("spawned train " + r.id + " at " + st.name + " s=" + Math.round(st.s));
                }
            }
            if (tick == 1300) {
                if (train == null || train.isRemoved()) {
                    err("train disappeared");
                } else {
                    double moved = Math.abs(train.getS() - trainStartS);
                    Route r = Route.get(trainRoute);
                    Vec3 expect = r.pos(train.getS());
                    double off = train.position().distanceTo(expect);
                    if (moved < 50) err("train moved only " + Math.round(moved) + " blocks in 1200 ticks");
                    else ok("train moved " + Math.round(moved) + " blocks, state " + train.getState() + ", station " + train.getStationIndex());
                    if (off > 1.0) err("train position off route by " + off);
                }
                finish(server);
            }
        } catch (Throwable t) {
            err("tick crashed: " + t);
            finish(server);
        }
    }

    private static void finish(MinecraftServer server) {
        tick = -1;
        JsonObject out = new JsonObject();
        out.addProperty("result", errors.isEmpty() ? "PASS" : "FAIL");
        JsonArray e = new JsonArray();
        errors.forEach(e::add);
        JsonArray i = new JsonArray();
        info.forEach(i::add);
        out.add("errors", e);
        out.add("info", i);
        try {
            Files.writeString(Path.of("tabi-selftest.json"), new GsonBuilder().setPrettyPrinting().create().toJson(out), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            TabiMod.LOGGER.error("[tabi-selftest] could not write result", ex);
        }
        TabiMod.LOGGER.info("[tabi-selftest] RESULT {} ({} errors)", errors.isEmpty() ? "PASS" : "FAIL", errors.size());
        server.halt(false);
    }
}
