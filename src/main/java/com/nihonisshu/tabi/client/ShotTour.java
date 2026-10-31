package com.nihonisshu.tabi.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nihonisshu.tabi.TabiMod;
import com.nihonisshu.tabi.client.screen.DialogueScreen;
import com.nihonisshu.tabi.client.screen.JournalScreen;
import com.nihonisshu.tabi.client.screen.ShopScreen;
import com.nihonisshu.tabi.client.screen.TicketScreen;
import com.nihonisshu.tabi.data.Route;
import com.nihonisshu.tabi.data.TabiData;
import com.nihonisshu.tabi.registry.TabiWorldgen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CI only (-Dtabi.shots=true): creates a world, flies to every viewpoint listed in meta.json, takes screenshots,
 * rides the train for a few window shots, photographs the GUI screens, then quits.
 */
public final class ShotTour {
    private ShotTour() {}

    private static final boolean ENABLED = Boolean.getBoolean("tabi.shots");
    private static boolean worldRequested;
    private static int stage = 0;
    private static int wait = 0;
    private static int index = 0;
    private static int sub = 0;
    private static int total = 0;
    private static final List<JsonObject> VIEWS = new ArrayList<>();

    public static void onScreen(Screen s) {
        if (!ENABLED || worldRequested || !(s instanceof TitleScreen)) return;
        worldRequested = true;
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> {
            try {
                LevelSettings settings = new LevelSettings("tabi_shots", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("tabi_shots_" + System.currentTimeMillis(), settings,
                        new WorldOptions(12345L, false, false), WorldPresets::createNormalWorldDimensions);
            } catch (Throwable t) {
                TabiMod.LOGGER.error("[tabi-shots] could not create world", t);
                mc.stop();
            }
        });
    }

    private static void cmd(Minecraft mc, String c) {
        if (mc.player != null) mc.player.connection.sendCommand(c);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, String.format("%02d_%s.png", total++, name), mc.getMainRenderTarget(),
                c -> TabiMod.LOGGER.info("[tabi-shots] {}", c.getString()));
    }

    public static void tick(Minecraft mc) {
        if (!ENABLED) return;
        total = Math.max(total, 0);
        if (mc.player == null || mc.level == null) return;
        if (++sub > 40000) {
            TabiMod.LOGGER.error("[tabi-shots] timeout");
            mc.stop();
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        switch (stage) {
            case 0 -> { // arrived in the world: wait until we are in the japan dimension
                if (mc.player.level().dimension() != TabiWorldgen.JAPAN) {
                    wait = 20;
                    return;
                }
                JsonArray arr = TabiData.meta().getAsJsonArray("viewpoints");
                String only = System.getProperty("tabi.shots.only", "").trim();
                java.util.Set<String> keep = new java.util.HashSet<>(java.util.Arrays.asList(only.split(",")));
                if (arr != null) for (JsonElement e : arr) {
                    JsonObject o = e.getAsJsonObject();
                    if (only.isEmpty() || keep.contains(TabiData.str(o, "name", ""))) VIEWS.add(o);
                }
                TabiMod.LOGGER.info("[tabi-shots] in japan, {} viewpoints", VIEWS.size());
                cmd(mc, "gamerule doDaylightCycle false");
                cmd(mc, "gamerule doWeatherCycle false");
                cmd(mc, "weather clear");
                wait = 400;
                stage = 100;
            }
            case 100 -> { // the bedroom where the game starts, as the player sees it
                mc.options.hideGui = false;
                shot(mc, "spawn_view");
                stage = 1;
                wait = 5;
            }
            case 1 -> {
                if (index >= VIEWS.size()) {
                    stage = System.getProperty("tabi.shots.only", "").isBlank() ? 3 : 8;
                    return;
                }
                JsonObject v = VIEWS.get(index);
                JsonArray p = v.getAsJsonArray("pos");
                cmd(mc, "gamemode " + TabiData.str(v, "mode", "spectator"));
                cmd(mc, "time set " + TabiData.integer(v, "time", 6000));
                cmd(mc, String.format("execute in tabi:japan run tp @s %.1f %.1f %.1f %.1f %.1f", p.get(0).getAsDouble(), p.get(1).getAsDouble(),
                        p.get(2).getAsDouble(), TabiData.dbl(v, "yaw", 0), TabiData.dbl(v, "pitch", 0)));
                mc.options.hideGui = !v.has("gui");
                wait = TabiData.integer(v, "wait", 160);
                stage = 2;
            }
            case 2 -> {
                JsonObject v = VIEWS.get(index);
                if (v.has("ride") && sub >= 0) {
                    // board the train standing at this terminal and look out of the window during the journey
                    cmd(mc, "ride @s mount @e[type=tabi:vehicle,sort=nearest,limit=1]");
                    stage = 10;
                    wait = 40;
                    return;
                }
                shot(mc, TabiData.str(v, "name", "view" + index));
                index++;
                stage = 1;
                wait = 5;
            }
            case 10, 11, 12, 13, 14, 15 -> { // riding
                JsonObject v = VIEWS.get(index);
                mc.options.hideGui = false;
                if (mc.player.getVehicle() instanceof com.nihonisshu.tabi.entity.VehicleEntity ve) {
                    double carYaw = ve.carPose(ve.getS(), 0).yaw;           // atan2(fx, fz) of the travel direction
                    float travel = (float) -Math.toDegrees(carYaw) + (ve.getDir() < 0 ? 180f : 0f);
                    mc.player.setYRot(travel + (float) TabiData.dbl(v, "look", 90));
                    mc.player.setXRot(8f);
                }
                shot(mc, TabiData.str(v, "name", "ride") + "_" + (stage - 10));
                stage++;
                wait = TabiData.integer(v, "interval", 500);
                if (stage > 15) {
                    cmd(mc, "gamemode spectator");
                    index++;
                    stage = 1;
                    wait = 20;
                }
            }
            case 3 -> { // GUI screens
                mc.options.hideGui = false;
                cmd(mc, "time set 6000");
                mc.setScreen(new DialogueScreen(sampleDialogue()));
                wait = 120;
                stage = 4;
            }
            case 4 -> {
                shot(mc, "ui_dialogue");
                CompoundTag shop = sampleShop();
                if (shop != null) mc.setScreen(new ShopScreen(shop));
                wait = 30;
                stage = 5;
            }
            case 5 -> {
                shot(mc, "ui_shop");
                CompoundTag t = sampleTickets();
                if (t != null) mc.setScreen(new TicketScreen(t));
                wait = 30;
                stage = 6;
            }
            case 6 -> {
                shot(mc, "ui_tickets");
                CompoundTag all = new CompoundTag();
                all.putInt("yen", 4321);
                CompoundTag j = new CompoundTag();
                JsonObject entries = TabiData.journal().getAsJsonObject("entries");
                if (entries != null) for (Map.Entry<String, JsonElement> e : entries.entrySet()) j.putBoolean(e.getKey(), true);
                all.put("journal", j);
                ClientState.data = all;
                mc.setScreen(new JournalScreen());
                wait = 30;
                stage = 7;
            }
            case 7 -> {
                shot(mc, "ui_journal");
                mc.setScreen(null);
                wait = 40;
                stage = 8;
            }
            case 8 -> {
                TabiMod.LOGGER.info("[tabi-shots] done, {} screenshots", total);
                mc.stop();
                stage = 9;
            }
            default -> {}
        }
    }

    private static CompoundTag sampleDialogue() {
        CompoundTag t = new CompoundTag();
        JsonObject d = TabiData.dialogue("grandma");
        t.putString("name", TabiData.str(d, "name", "おばあちゃん"));
        t.putString("profile", "grandma");
        t.putString("entry", "");
        t.putInt("entity", -1);
        ListTag lines = new ListTag();
        JsonArray entries = d.getAsJsonArray("entries");
        if (entries != null && !entries.isEmpty()) {
            for (JsonElement l : entries.get(0).getAsJsonObject().getAsJsonArray("lines")) lines.add(StringTag.valueOf(l.getAsString().replace("〇〇", "Dev")));
        } else {
            lines.add(StringTag.valueOf("おはよう。よう眠れたかい？"));
        }
        t.put("lines", lines);
        ListTag ch = new ListTag();
        ch.add(StringTag.valueOf("うん、ぐっすり"));
        ch.add(StringTag.valueOf("まだねむい……"));
        t.put("choices", ch);
        t.putString("voice", "old");
        return t;
    }

    private static CompoundTag sampleShop() {
        JsonObject shops = TabiData.shops().getAsJsonObject("shops");
        if (shops == null || shops.size() == 0) return null;
        String id = shops.has("harbor_cafe") ? "harbor_cafe" : shops.keySet().iterator().next();
        JsonObject s = shops.getAsJsonObject(id);
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("name", TabiData.str(s, "name", id));
        tag.putString("greet", TabiData.str(s, "greet", ""));
        tag.putString("mode", TabiData.str(s, "mode", "buy"));
        ListTag items = new ListTag();
        for (JsonElement e : s.getAsJsonArray("items")) {
            JsonObject it = e.getAsJsonObject();
            CompoundTag c = new CompoundTag();
            c.putString("item", TabiData.str(it, "item", "minecraft:air"));
            c.putInt("price", TabiData.integer(it, "price", 100));
            c.putString("note", TabiData.str(it, "note", ""));
            items.add(c);
        }
        tag.put("items", items);
        tag.putInt("yen", 4321);
        return tag;
    }

    private static CompoundTag sampleTickets() {
        Route r = Route.all().stream().filter(x -> "train".equals(x.type)).findFirst().orElse(null);
        if (r == null) return null;
        CompoundTag tag = new CompoundTag();
        tag.putString("route", r.id);
        tag.putString("routeName", r.name);
        tag.putInt("from", 0);
        tag.putString("fromName", r.stations.get(0).name);
        ListTag dests = new ListTag();
        for (int i = 1; i < r.stations.size(); i++) {
            Route.Station st = r.stations.get(i);
            CompoundTag d = new CompoundTag();
            d.putInt("idx", st.index);
            d.putString("name", st.name);
            d.putString("kana", st.kana);
            d.putInt("fare", r.fare(r.stations.get(0), st));
            dests.add(d);
        }
        tag.put("dests", dests);
        tag.putInt("yen", 4321);
        return tag;
    }
}
