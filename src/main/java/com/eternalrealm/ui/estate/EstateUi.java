package com.eternalrealm.ui.estate;

import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared look and helpers of the inn's screens (the innkeeper, the ledger, the door's menu): the walnut-and-brass
 * palette of the Starlight Manor and small JSON readers. Client thread only.
 */
final class EstateUi {
    static final int SHADOW = 0x90000000;
    static final int PANEL = 0xF2161114;
    static final int PANEL_INNER = 0xFF201820;
    static final int WALNUT = 0xFF4A3020;
    static final int WALNUT_DARK = 0xFF2A1A12;
    static final int BRASS = 0xFFD8A84A;
    static final int BRASS_DARK = 0xFF7A5420;
    static final int ROW = 0xFF261D24;
    static final int ROW_HOVER = 0xFF33282F;
    static final int ROW_SELECTED = 0xFF4A3520;
    static final int TEXT = 0xFFEDE3CC;
    static final int MUTED = 0xFFA0928A;
    static final int GOLD = 0xFFFFD866;
    static final int GOOD = 0xFF8CE08C;
    static final int BAD = 0xFFFF7A5A;
    static final int AMBER = 0xFFFFB347;

    private static final Map<String, ItemStack> STACKS = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ItemStack> eldest) {
            return size() > 512;
        }
    };

    private EstateUi() {
    }

    /** A walnut panel with a brass rim and corner studs. */
    static void panel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x - 3, y - 3, x + w + 5, y + h + 5, SHADOW);
        graphics.fill(x - 4, y - 4, x + w + 4, y + h + 4, BRASS_DARK);
        graphics.fill(x - 3, y - 3, x + w + 3, y + h + 3, BRASS);
        graphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, WALNUT_DARK);
        graphics.fill(x, y, x + w, y + h, PANEL);
        for (int[] corner : new int[][]{{x - 4, y - 4}, {x + w, y - 4}, {x - 4, y + h}, {x + w, y + h}}) {
            graphics.fill(corner[0], corner[1], corner[0] + 4, corner[1] + 4, GOLD);
            graphics.fill(corner[0] + 1, corner[1] + 1, corner[0] + 3, corner[1] + 3, BRASS_DARK);
        }
    }

    /** An inset box (a list or a card). */
    static void inset(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, WALNUT);
        graphics.fill(x, y, x + w, y + h, PANEL_INNER);
    }

    /** A title with a brass rule under it. */
    static void title(GuiGraphics graphics, Font font, Component title, int centerX, int y, int width) {
        graphics.drawCenteredString(font, title, centerX, y, GOLD);
        graphics.fill(centerX - width / 2, y + 11, centerX + width / 2, y + 12, BRASS_DARK);
        graphics.fill(centerX - 3, y + 10, centerX + 3, y + 13, BRASS);
    }

    static String gold(long amount) {
        return String.format(Locale.ROOT, "%,d", amount);
    }

    static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    static String ellipsize(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    static void send(String action, JsonObject args) {
        ERNetwork.sendToServer(new UiActionPacket(action, args));
    }

    static List<JsonObject> objects(JsonObject data, String key) {
        List<JsonObject> list = new ArrayList<>();
        if (data != null && data.has(key) && data.get(key).isJsonArray()) {
            for (JsonElement element : data.getAsJsonArray(key)) {
                if (element.isJsonObject()) {
                    list.add(element.getAsJsonObject());
                }
            }
        }
        return list;
    }

    static List<Integer> ints(JsonObject data, String key) {
        List<Integer> list = new ArrayList<>();
        if (data != null && data.has(key) && data.get(key).isJsonArray()) {
            JsonArray array = data.getAsJsonArray(key);
            for (JsonElement element : array) {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                    list.add(element.getAsInt());
                }
            }
        }
        return list;
    }

    static String str(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive()
                ? object.get(key).getAsString() : "";
    }

    static long num(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive()
                && object.get(key).getAsJsonPrimitive().isNumber() ? object.get(key).getAsLong() : 0L;
    }

    static boolean bool(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive()
                && object.get(key).getAsJsonPrimitive().isBoolean() && object.get(key).getAsBoolean();
    }

    static JsonObject object(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key)
                : new JsonObject();
    }

    /** The stack a ledger line shows ({@code item} and, when sent, {@code tag}); cached. */
    static ItemStack stack(JsonObject line) {
        String item = str(line, "item");
        String tag = str(line, "tag");
        String cacheKey = item + "|" + tag;
        ItemStack cached = STACKS.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        ItemStack stack = ItemStack.EMPTY;
        try {
            CompoundTag compound = new CompoundTag();
            compound.putString("id", item);
            compound.putByte("Count", (byte) 1);
            if (!tag.isEmpty()) {
                compound.put("tag", TagParser.parseTag(tag));
            }
            stack = ItemStack.of(compound);
        } catch (Exception ignored) {
            // an unreadable line shows no picture
        }
        STACKS.put(cacheKey, stack);
        return stack;
    }

    /** The name a ledger line shows: the stack's own, or the one the server sent for data too long to send. */
    static Component name(JsonObject line, ItemStack stack) {
        String json = str(line, "name");
        if (!json.isEmpty()) {
            try {
                Component name = Component.Serializer.fromJson(json);
                if (name != null) {
                    return name;
                }
            } catch (RuntimeException ignored) {
                // fall back to the item's name
            }
        }
        return stack.isEmpty() ? Component.literal(str(line, "item")) : stack.getHoverName();
    }

    /** "収納箱 3", "樽 2", "書棚 4" for a storage piece name ({@code chest.3}). */
    static Component pieceName(String name) {
        int dot = name.indexOf('.');
        if (dot <= 0) {
            return Component.translatable("estate.eternalrealm.piece.other");
        }
        return Component.translatable("estate.eternalrealm.piece." + name.substring(0, dot), name.substring(dot + 1));
    }

    /** The colour of a label's tab and tag. */
    static int labelColor(String label) {
        return switch (label) {
            case "gear" -> 0xFFB8C4D8;
            case "potion" -> 0xFFE0607A;
            case "material" -> 0xFF8CC070;
            case "food" -> 0xFFE8B060;
            case "book" -> 0xFFC89A6A;
            case "treasure" -> 0xFFFFD040;
            case "misc" -> 0xFFA8A0C0;
            default -> MUTED;
        };
    }
}
