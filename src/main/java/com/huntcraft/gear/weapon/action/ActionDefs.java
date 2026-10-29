package com.huntcraft.gear.weapon.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.huntcraft.HuntCraft;
import com.huntcraft.core.DamageKind;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loads hunter_actions.json (both sides, from the mod jar). */
public final class ActionDefs {
    private static final String PATH = "/assets/" + HuntCraft.MODID + "/hcmodels/hunter_actions.json";
    private static Map<String, ActionDef> actions;
    private static Map<String, WeaponSprite> weapons;

    /** Sprite pixels (32px icon) of the grip and the tip, used for weapon trails and the second hand. */
    public record WeaponSprite(float gripX, float gripY, float tipX, float tipY, float hand2) {
    }

    private ActionDefs() {
    }

    public static synchronized void load() {
        if (actions != null) return;
        Map<String, ActionDef> a = new HashMap<>();
        Map<String, WeaponSprite> w = new HashMap<>();
        try (InputStream in = ActionDefs.class.getResourceAsStream(PATH)) {
            if (in == null) {
                HuntCraft.LOGGER.error("Missing {}", PATH);
            } else {
                try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("actions").entrySet()) {
                        try {
                            a.put(e.getKey(), parse(e.getKey(), e.getValue().getAsJsonObject()));
                        } catch (Exception ex) {
                            HuntCraft.LOGGER.error("Bad action {}", e.getKey(), ex);
                        }
                    }
                    if (root.has("weapons")) {
                        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("weapons").entrySet()) {
                            JsonObject o = e.getValue().getAsJsonObject();
                            JsonArray g = o.getAsJsonArray("grip");
                            JsonArray t = o.getAsJsonArray("tip");
                            w.put(e.getKey(), new WeaponSprite(g.get(0).getAsFloat(), g.get(1).getAsFloat(), t.get(0).getAsFloat(), t.get(1).getAsFloat(),
                                    o.has("hand2") ? o.get("hand2").getAsFloat() : 0));
                        }
                    }
                }
            }
        } catch (Exception e) {
            HuntCraft.LOGGER.error("Failed to load hunter actions", e);
        }
        actions = a;
        weapons = w;
        HuntCraft.LOGGER.info("Loaded {} hunter combat actions", a.size());
    }

    @Nullable
    public static ActionDef get(String name) {
        if (actions == null) load();
        return name == null ? null : actions.get(name);
    }

    public static Map<String, ActionDef> all() {
        if (actions == null) load();
        return Collections.unmodifiableMap(actions);
    }

    @Nullable
    public static WeaponSprite sprite(String weaponClassId) {
        if (weapons == null) load();
        return weapons.get(weaponClassId);
    }

    private static float f(JsonObject o, String k, float def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsFloat() : def;
    }

    private static int i(JsonObject o, String k, int def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : def;
    }

    private static boolean b(JsonObject o, String k, boolean def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsBoolean() : def;
    }

    @Nullable
    private static String s(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    private static ActionDef parse(String name, JsonObject o) {
        int ticks = i(o, "ticks", 20);
        List<ActionDef.Hit> hits = new ArrayList<>();
        if (o.has("hits")) {
            for (JsonElement he : o.getAsJsonArray("hits")) {
                JsonObject h = he.getAsJsonObject();
                float koHead = 0, koBody = 0;
                if (h.has("ko")) {
                    JsonArray ko = h.getAsJsonArray("ko");
                    koHead = ko.get(0).getAsFloat();
                    koBody = ko.size() > 1 ? ko.get(1).getAsFloat() : 0;
                }
                DamageKind kind = null;
                String ks = s(h, "kind");
                if (ks != null) {
                    try {
                        kind = DamageKind.valueOf(ks.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                hits.add(new ActionDef.Hit(i(h, "t", 0), f(h, "mv", 0.2F), f(h, "range", 3.4F), f(h, "cone", 100F), i(h, "sharp", 1), koHead, koBody,
                        f(h, "elem", 1F), f(h, "status", 1F), b(h, "bounce", true), b(h, "use_sharp", true), i(h, "hitstop", 2), f(h, "shake", 0F),
                        s(h, "fx") == null ? "sweep" : s(h, "fx"), f(h, "fwd", 1.4F), f(h, "up", 1.0F), kind, f(h, "area", 0F), s(h, "sound")));
            }
        }
        List<ActionDef.Move> moves = new ArrayList<>();
        if (o.has("move")) {
            for (JsonElement me : o.getAsJsonArray("move")) {
                JsonArray m = me.getAsJsonArray();
                moves.add(new ActionDef.Move(m.get(0).getAsInt(), m.get(1).getAsInt(), m.get(2).getAsFloat(), m.size() > 3 ? m.get(3).getAsFloat() : 0));
            }
        }
        List<ActionDef.Sound> sounds = new ArrayList<>();
        if (o.has("sounds")) {
            for (JsonElement se : o.getAsJsonArray("sounds")) {
                JsonArray sa = se.getAsJsonArray();
                sounds.add(new ActionDef.Sound(sa.get(0).getAsInt(), sa.get(1).getAsString(), sa.size() > 2 ? sa.get(2).getAsFloat() : 1F,
                        sa.size() > 3 ? sa.get(3).getAsFloat() : 1F));
            }
        }
        int ifs = 0, ife = 0, trs = 0, tre = 0;
        if (o.has("iframes")) {
            JsonArray a = o.getAsJsonArray("iframes");
            ifs = a.get(0).getAsInt();
            ife = a.get(1).getAsInt();
        }
        if (o.has("trail")) {
            JsonArray a = o.getAsJsonArray("trail");
            trs = a.get(0).getAsInt();
            tre = a.get(1).getAsInt();
        }
        Map<String, Float> tags = new HashMap<>();
        if (o.has("tags")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("tags").entrySet()) tags.put(e.getKey(), e.getValue().getAsFloat());
        }
        String anim = s(o, "anim");
        return new ActionDef(name, anim == null ? name : anim, ticks, i(o, "cancel", ticks), b(o, "lock", true), ifs, ife, trs, tre,
                f(o, "stamina", 0F), s(o, "next"), hits, moves, sounds, tags);
    }
}
