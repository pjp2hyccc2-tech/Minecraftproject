package com.nihonisshu.tabi.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nihonisshu.tabi.data.TabiData;
import com.nihonisshu.tabi.entity.TabiNpc;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client side ambient speech bubbles ("tsubuyaki"). When you walk within 8 blocks of an NPC that has a bubble pool,
 * a short line (max 15 chars per line) appears above its head for a few seconds. At most 3 at a time.
 * Pool entries: "text" | {"t": "text", "time": "morning|day|evening|night", "rain": true} | {"a": "...", "b": "..."} (two NPCs talking).
 */
public final class Bubbles {
    private Bubbles() {}

    private static final class State {
        List<String> lines;
        int shownAt, until, nextAt;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static final RandomSource RAND = RandomSource.create();
    private static int clock;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        clock++;
        if (mc.player == null || mc.level == null) {
            STATES.clear();
            return;
        }
        if (clock % 10 != 0) return;
        int active = 0;
        for (State s : STATES.values()) if (s.until > clock) active++;
        List<TabiNpc> near = mc.level.getEntitiesOfClass(TabiNpc.class, new AABB(mc.player.blockPosition()).inflate(9, 4, 9),
                n -> !n.getBubblePool().isEmpty());
        near.sort((a, b) -> Double.compare(a.distanceToSqr(mc.player), b.distanceToSqr(mc.player)));
        JsonObject pools = TabiData.bubbles().getAsJsonObject("pools");
        if (pools == null) return;
        for (TabiNpc npc : near) {
            if (active >= 3) break;
            State st = STATES.computeIfAbsent(npc.getId(), k -> {
                State s = new State();
                s.nextAt = clock + 10 + RAND.nextInt(60);
                return s;
            });
            if (st.until > clock || st.nextAt > clock) continue;
            JsonArray pool = pools.getAsJsonArray(npc.getBubblePool());
            if (pool == null || pool.isEmpty()) continue;
            JsonElement pick = choose(pool, mc);
            if (pick == null) continue;
            if (pick.isJsonObject() && pick.getAsJsonObject().has("a")) {
                JsonObject o = pick.getAsJsonObject();
                TabiNpc partner = null;
                for (TabiNpc other : near) {
                    if (other != npc && other.getBubblePool().equals(npc.getBubblePool()) && other.distanceTo(npc) < 5) partner = other;
                }
                if (partner == null) continue;
                show(st, o.get("a").getAsString(), 0, 70);
                State ps = STATES.computeIfAbsent(partner.getId(), k -> new State());
                show(ps, o.get("b").getAsString(), 45, 70);
                active += 2;
            } else {
                String text = pick.isJsonObject() ? pick.getAsJsonObject().get("t").getAsString() : pick.getAsString();
                show(st, text, 0, 60 + text.length() * 2);
                active++;
            }
        }
        STATES.entrySet().removeIf(e -> mc.level.getEntity(e.getKey()) == null);
    }

    private static JsonElement choose(JsonArray pool, Minecraft mc) {
        long t = mc.level.getDayTime() % 24000L;
        int hour = (int) ((t / 1000 + 6) % 24);
        String now = hour >= 5 && hour < 10 ? "morning" : hour >= 10 && hour < 16 ? "day" : hour >= 16 && hour < 19 ? "evening" : "night";
        boolean rain = mc.level.isRaining();
        List<JsonElement> ok = new ArrayList<>();
        for (JsonElement e : pool) {
            if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                if (o.has("time") && !o.get("time").getAsString().equals(now)) continue;
                if (o.has("rain") && o.get("rain").getAsBoolean() != rain) continue;
            }
            ok.add(e);
        }
        return ok.isEmpty() ? null : ok.get(RAND.nextInt(ok.size()));
    }

    private static void show(State st, String text, int delay, int duration) {
        st.lines = wrap(text);
        st.shownAt = clock + delay;
        st.until = clock + delay + duration;
        st.nextAt = st.until + 400 + RAND.nextInt(400);
    }

    private static List<String> wrap(String text) {
        List<String> out = new ArrayList<>();
        int max = 15;
        String rest = text;
        while (rest.length() > max && out.size() < 1) {
            out.add(rest.substring(0, max));
            rest = rest.substring(max);
        }
        out.add(rest.length() > max ? rest.substring(0, max - 1) + "…" : rest);
        return out;
    }

    public static List<String> textFor(TabiNpc npc) {
        State st = STATES.get(npc.getId());
        if (st == null || st.lines == null || clock < st.shownAt || clock >= st.until) return null;
        return st.lines;
    }

    public static float alphaFor(TabiNpc npc) {
        State st = STATES.get(npc.getId());
        if (st == null) return 0f;
        float in = Math.min(1f, (clock - st.shownAt) / 8f);
        float out = Math.min(1f, (st.until - clock) / 10f);
        return Math.max(0.05f, Math.min(in, out));
    }
}
