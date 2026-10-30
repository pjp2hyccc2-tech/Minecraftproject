package com.nihonisshu.tabi.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nihonisshu.tabi.data.TabiData;
import com.nihonisshu.tabi.entity.TabiNpc;
import com.nihonisshu.tabi.game.Areas;
import com.nihonisshu.tabi.game.PlayerData;
import com.nihonisshu.tabi.game.ShopManager;
import com.nihonisshu.tabi.network.TabiNet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data driven conversations (data/tabi/dialogue/&lt;profile&gt;.json).
 * Each entry: id, priority, once, when{...}, lines[], actions[], choices[{text, actions[], reply[]}].
 */
public final class DialogueManager {
    private DialogueManager() {}

    private static final Map<UUID, String> LAST = new HashMap<>();

    public static void talk(ServerPlayer p, TabiNpc npc) {
        String profile = npc.getProfile();
        if (profile.isEmpty()) return;
        talkProfile(p, profile, npc.getDisplay(), npc.getId());
    }

    public static void talkVirtual(ServerPlayer p, String profile) {
        talkProfile(p, profile, null, -1);
    }

    private static void talkProfile(ServerPlayer p, String profile, String display, int entityId) {
        JsonObject d = TabiData.dialogue(profile);
        String name = display != null && !display.isEmpty() ? display : TabiData.str(d, "name", "");
        String shop = TabiData.str(d, "shop", "");
        JsonArray entries = d.getAsJsonArray("entries");
        if ((entries == null || entries.isEmpty()) && !shop.isEmpty()) {
            ShopManager.open(p, shop);
            return;
        }
        if (entries == null || entries.isEmpty()) return;
        JsonObject entry = pick(p, profile, entries);
        if (entry == null) {
            if (!shop.isEmpty()) ShopManager.open(p, shop);
            return;
        }
        String id = TabiData.str(entry, "id", "");
        LAST.put(p.getUUID(), profile + "/" + id);
        if (entry.has("once") && entry.get("once").getAsBoolean()) {
            PlayerData.setFlag(p, "dlg:" + profile + ":" + id, true);
        }
        runActions(p, entry.getAsJsonArray("actions"));
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putString("profile", profile);
        tag.putString("entry", id);
        tag.putInt("entity", entityId);
        tag.put("lines", lines(p, entry.getAsJsonArray("lines")));
        ListTag choices = new ListTag();
        JsonArray ch = entry.getAsJsonArray("choices");
        if (ch != null) for (JsonElement c : ch) choices.add(StringTag.valueOf(TabiData.str(c.getAsJsonObject(), "text", "…")));
        if (choices.isEmpty() && !shop.isEmpty()) {
            choices.add(StringTag.valueOf("買い物をする"));
            choices.add(StringTag.valueOf("またね"));
            tag.putBoolean("shopChoice", true);
        }
        tag.put("choices", choices);
        tag.putString("voice", TabiData.str(d, "voice", "mid"));
        TabiNet.send(p, "dialogue", tag);
    }

    private static ListTag lines(ServerPlayer p, JsonArray arr) {
        ListTag list = new ListTag();
        if (arr == null) return list;
        String pname = p.getGameProfile().getName();
        for (JsonElement e : arr) list.add(StringTag.valueOf(e.getAsString().replace("〇〇", pname).replace("{name}", pname)));
        return list;
    }

    public static void choose(ServerPlayer p, CompoundTag tag) {
        String profile = tag.getString("profile");
        String entryId = tag.getString("entry");
        int choice = tag.getInt("choice");
        int entityId = tag.getInt("entity");
        if (entityId >= 0) {
            Entity e = p.level().getEntity(entityId);
            if (e == null || e.distanceToSqr(p) > 64) return;
        }
        JsonObject d = TabiData.dialogue(profile);
        if (tag.getBoolean("shopChoice")) {
            if (choice == 0) ShopManager.open(p, TabiData.str(d, "shop", ""));
            return;
        }
        String last = LAST.get(p.getUUID());
        if (last == null || !last.equals(profile + "/" + entryId)) return;
        LAST.remove(p.getUUID());
        JsonArray entries = d.getAsJsonArray("entries");
        if (entries == null) return;
        for (JsonElement e : entries) {
            JsonObject entry = e.getAsJsonObject();
            if (!TabiData.str(entry, "id", "").equals(entryId)) continue;
            JsonArray ch = entry.getAsJsonArray("choices");
            if (ch == null || choice < 0 || choice >= ch.size()) return;
            JsonObject c = ch.get(choice).getAsJsonObject();
            runActions(p, c.getAsJsonArray("actions"));
            JsonArray reply = c.getAsJsonArray("reply");
            if (reply != null && !reply.isEmpty()) {
                CompoundTag out = new CompoundTag();
                out.putString("name", tag.getString("name"));
                out.putString("profile", profile);
                out.putString("entry", "");
                out.putInt("entity", entityId);
                out.put("lines", lines(p, reply));
                out.put("choices", new ListTag());
                out.putString("voice", TabiData.str(d, "voice", "mid"));
                TabiNet.send(p, "dialogue", out);
            }
            String openShop = TabiData.str(c, "shop", "");
            if (!openShop.isEmpty()) ShopManager.open(p, openShop);
            return;
        }
    }

    private static JsonObject pick(ServerPlayer p, String profile, JsonArray entries) {
        List<JsonObject> best = new ArrayList<>();
        int bestPri = Integer.MIN_VALUE;
        for (JsonElement e : entries) {
            JsonObject entry = e.getAsJsonObject();
            String id = TabiData.str(entry, "id", "");
            if (entry.has("once") && entry.get("once").getAsBoolean() && PlayerData.flag(p, "dlg:" + profile + ":" + id)) continue;
            if (!Conditions.test(p, entry.getAsJsonObject("when"))) continue;
            int pri = TabiData.integer(entry, "priority", 0);
            if (pri > bestPri) {
                bestPri = pri;
                best.clear();
            }
            if (pri == bestPri) best.add(entry);
        }
        if (best.isEmpty()) return null;
        String last = LAST.get(p.getUUID());
        if (best.size() > 1 && last != null) {
            best.removeIf(o -> last.equals(profile + "/" + TabiData.str(o, "id", "")));
        }
        return best.get(p.getRandom().nextInt(best.size()));
    }

    public static void runActions(ServerPlayer p, JsonArray actions) {
        if (actions == null) return;
        for (JsonElement e : actions) {
            JsonObject a = e.getAsJsonObject();
            if (a.has("give")) {
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(a.get("give").getAsString()));
                if (item != null) {
                    ItemStack st = new ItemStack(item, TabiData.integer(a, "count", 1));
                    if (!p.getInventory().add(st)) p.drop(st, false);
                }
            }
            if (a.has("take")) {
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(a.get("take").getAsString()));
                int need = TabiData.integer(a, "count", 1);
                for (int i = 0; i < p.getInventory().getContainerSize() && need > 0 && item != null; i++) {
                    ItemStack st = p.getInventory().getItem(i);
                    if (st.is(item)) {
                        int n = Math.min(need, st.getCount());
                        st.shrink(n);
                        need -= n;
                    }
                }
            }
            if (a.has("yen")) {
                int y = a.get("yen").getAsInt();
                if (y >= 0) PlayerData.earn(p, y, TabiData.str(a, "reason", "お小遣い"));
                else PlayerData.setYen(p, PlayerData.yen(p) + y);
            }
            if (a.has("flag")) PlayerData.setFlag(p, a.get("flag").getAsString(), true);
            if (a.has("unflag")) PlayerData.setFlag(p, a.get("unflag").getAsString(), false);
            if (a.has("journal")) PlayerData.unlock(p, a.get("journal").getAsString());
            if (a.has("shop")) ShopManager.open(p, a.get("shop").getAsString());
            if (a.has("time") && p.getServer() != null) {
                p.serverLevel().setDayTime(a.get("time").getAsLong());
            }
        }
    }

    /** condition evaluation for dialogue entries */
    public static final class Conditions {
        private Conditions() {}

        public static boolean test(ServerPlayer p, JsonObject w) {
            if (w == null) return true;
            long t = p.level().getDayTime() % 24000L;
            int hour = (int) ((t / 1000 + 6) % 24);
            if (w.has("time")) {
                String want = w.get("time").getAsString();
                String now = hour >= 5 && hour < 10 ? "morning" : hour < 16 && hour >= 10 ? "day" : hour >= 16 && hour < 19 ? "evening" : "night";
                if (!want.equals(now)) return false;
            }
            if (w.has("weather")) {
                boolean rain = p.level().isRaining();
                if (w.get("weather").getAsString().equals("rain") != rain) return false;
            }
            if (w.has("flag") && !all(p, w.get("flag"), true)) return false;
            if (w.has("not_flag") && !all(p, w.get("not_flag"), false)) return false;
            if (w.has("journal") && !PlayerData.unlocked(p, w.get("journal").getAsString())) return false;
            if (w.has("not_journal") && PlayerData.unlocked(p, w.get("not_journal").getAsString())) return false;
            if (w.has("has_item")) {
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(w.get("has_item").getAsString()));
                if (item == null) return false;
                int need = w.has("has_count") ? w.get("has_count").getAsInt() : 1;
                int have = 0;
                for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                    ItemStack st = p.getInventory().getItem(i);
                    if (st.is(item)) have += st.getCount();
                }
                if (have < need) return false;
            }
            if (w.has("yen_gte") && PlayerData.yen(p) < w.get("yen_gte").getAsInt()) return false;
            if (w.has("yen_lt") && PlayerData.yen(p) >= w.get("yen_lt").getAsInt()) return false;
            if (w.has("area") && !w.get("area").getAsString().equals(Areas.current(p))) return false;
            if (w.has("chance") && p.getRandom().nextDouble() > w.get("chance").getAsDouble()) return false;
            return true;
        }

        private static boolean all(ServerPlayer p, JsonElement e, boolean want) {
            if (e.isJsonArray()) {
                for (JsonElement x : e.getAsJsonArray()) if (PlayerData.flag(p, x.getAsString()) != want) return false;
                return true;
            }
            return PlayerData.flag(p, e.getAsString()) == want;
        }
    }
}
