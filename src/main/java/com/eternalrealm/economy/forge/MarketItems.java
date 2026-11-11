package com.eternalrealm.economy.forge;

import com.eternalrealm.economy.ItemInfo;
import com.eternalrealm.item.Design;
import com.eternalrealm.item.EquipmentData;
import com.eternalrealm.item.bag.BagCategory;
import com.eternalrealm.item.forge.Bags;
import com.eternalrealm.item.forge.EquipmentStacks;
import com.eternalrealm.item.forge.Inventories;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * What the board and the auction hall know about an item without reading it again: its kind, required level, gold
 * frame, price group ("the same item" for 相場) and search words in Japanese and English. The names come from the
 * mod's own language files inside the jar, so a dedicated server can search them too.
 */
public final class MarketItems {
    private static final Logger LOG = LoggerFactory.getLogger(MarketItems.class);
    private static Map<String, String> japanese;
    private static Map<String, String> english;

    private MarketItems() {
    }

    /** The market description of {@code stack} ({@code count} pieces of it). */
    public static ItemInfo describe(ItemStack stack, int count) {
        String itemId = Inventories.idOf(stack);
        Optional<EquipmentData> equipment = EquipmentStacks.read(stack);
        String rarity = equipment.map(data -> data.rarity().id()).orElse("common");
        String category = category(stack, equipment);
        int level = Math.min(ItemInfo.MAX_LEVEL, equipment.map(EquipmentData::requiredLevel).orElse(0));
        boolean gold = isGoldFrame(stack);
        Optional<Design> design = EquipmentStacks.designOf(stack);
        String group = group(itemId, equipment, design, stack);
        StringBuilder words = new StringBuilder();
        String key = design.map(Design::translationKey).orElse(stack.getItem().getDescriptionId());
        words.append(name(key, true)).append(' ').append(name(key, false)).append(' ');
        if (stack.hasCustomHoverName()) {
            words.append(stack.getHoverName().getString()).append(' ');
        }
        words.append(itemId.substring(itemId.indexOf(':') + 1).replace('_', ' '));
        design.ifPresent(d -> words.append(' ').append(d.id().replace('_', ' ')));
        return new ItemInfo(itemId, Math.max(1, Math.min(ItemInfo.MAX_COUNT, count)), rarity, category, level, gold,
                group, words.toString().toLowerCase(Locale.ROOT).trim());
    }

    /**
     * The board's kind of an item: equipment by its slot (weapon, armor; charms and accessories when the item system
     * has them), consumables, materials, mount deeds, everything else.
     */
    static String category(ItemStack stack, Optional<EquipmentData> equipment) {
        if (MountDeedsBridge.isDeed(stack)) {
            return "mount";
        }
        if (equipment.isPresent()) {
            String slot = equipment.get().slot().name().toLowerCase(Locale.ROOT);
            if (equipment.get().isWeapon()) {
                return "weapon";
            }
            return slot.contains("accessory") || slot.contains("charm") || slot.contains("ring")
                    || slot.contains("amulet") ? "accessory" : "armor";
        }
        BagCategory bag = Bags.category(stack);
        return switch (bag) {
            case CONSUMABLE -> "consumable";
            case MATERIAL -> "material";
            case EQUIPMENT -> "armor";
            default -> "other";
        };
    }

    /** "The same item" for prices: a design and its rarity, a tier piece and its rarity, a mount, a plain item. */
    static String group(String itemId, Optional<EquipmentData> equipment, Optional<Design> design, ItemStack stack) {
        Optional<String> mount = MountDeedsBridge.group(stack);
        if (mount.isPresent()) {
            return mount.get();
        }
        String path = itemId.replace(':', '.');
        if (design.isPresent() && equipment.isPresent()) {
            return "design:" + design.get().id() + ":" + equipment.get().rarity().id();
        }
        if (equipment.isPresent()) {
            return "gear:" + path + ":" + equipment.get().rarity().id();
        }
        return "item:" + itemId;
    }

    /** Whether the stack is a gold-frame piece (金枠). */
    public static boolean isGoldFrame(ItemStack stack) {
        CompoundTag tag = stack.isEmpty() ? null : stack.getTag();
        return tag != null && (tag.getBoolean("er_gold_frame") || tag.getBoolean("GoldFrame"));
    }

    /** The name of a translation key in Japanese or English (the key itself when unknown). */
    static String name(String key, boolean inJapanese) {
        Map<String, String> table = inJapanese ? japanese() : english();
        return table.getOrDefault(key, "");
    }

    private static synchronized Map<String, String> japanese() {
        if (japanese == null) {
            japanese = load("ja_jp");
        }
        return japanese;
    }

    private static synchronized Map<String, String> english() {
        if (english == null) {
            english = load("en_us");
        }
        return english;
    }

    private static Map<String, String> load(String language) {
        Map<String, String> table = new HashMap<>();
        String path = "/assets/eternalrealm/lang/" + language + ".json";
        try (InputStream in = MarketItems.class.getResourceAsStream(path)) {
            if (in == null) {
                return table;
            }
            JsonElement root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            if (root.isJsonObject()) {
                JsonObject object = root.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    if ((entry.getKey().startsWith("item.") || entry.getKey().startsWith("design.")
                            || entry.getKey().startsWith("block.")) && entry.getValue().isJsonPrimitive()) {
                        table.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Could not read {} for market search", path, e);
        }
        return table;
    }
}
