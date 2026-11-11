package com.eternalrealm.ui.market;

import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared look and helpers of the board and auction screens (the bag's palette). Client thread only. */
public final class MarketUi {
    static final int PANEL = 0xF0141018;
    static final int PANEL_INNER = 0xFF1E1A26;
    static final int RIM = 0xFFC9A55A;
    static final int RIM_DARK = 0xFF3A2C18;
    static final int CELL_BACK = 0xFF262130;
    static final int ROW = 0xFF221D2B;
    static final int ROW_HOVER = 0xFF2E2838;
    static final int ROW_SELECTED = 0xFF4A3A20;
    static final int TEXT = 0xFFE8E0C8;
    static final int MUTED = 0xFF9A90A8;
    static final int GOLD = 0xFFFFD866;
    static final int GOOD = 0xFF7CE07C;
    static final int BAD = 0xFFFF6A50;
    static final int GOLD_FRAME = 0xFFFFC21A;

    private static final Map<String, ItemStack> STACKS = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ItemStack> eldest) {
            return size() > 256;
        }
    };

    private MarketUi() {
    }

    /** The item of a market entry (its NBT text), parsed once. */
    static ItemStack stack(String data) {
        if (data == null || data.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return STACKS.computeIfAbsent(data, text -> {
            try {
                return ItemStack.of(TagParser.parseTag(text));
            } catch (Exception e) {
                return ItemStack.EMPTY;
            }
        });
    }

    static void send(String action, JsonObject args) {
        ERNetwork.sendToServer(new UiActionPacket(action, args));
    }

    static void frame(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, RIM_DARK);
        graphics.fill(x, y, x + w, y + h, PANEL);
        graphics.renderOutline(x, y, w, h, RIM);
    }

    /** An item cell: the item, its count, a rarity-coloured rim, a thick gold rim for gold-frame pieces. */
    static void cell(GuiGraphics graphics, Font font, ItemStack stack, int x, int y, boolean gold) {
        graphics.fill(x, y, x + 18, y + 18, CELL_BACK);
        int rim = gold ? GOLD_FRAME : rarityColor(stack, 0xFF4A4256);
        graphics.renderOutline(x, y, 18, 18, rim);
        if (gold) {
            graphics.renderOutline(x - 1, y - 1, 20, 20, 0xFF8A6A10);
        }
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + 1, y + 1);
            graphics.renderItemDecorations(font, stack, x + 1, y + 1);
        }
    }

    /** The rarity colour of equipment (its name's colour), else {@code fallback}. */
    static int rarityColor(ItemStack stack, int fallback) {
        if (stack.isEmpty()) {
            return fallback;
        }
        Integer color = stack.getHoverName().getStyle().getColor() == null ? null
                : stack.getHoverName().getStyle().getColor().getValue();
        return color == null ? fallback : 0xFF000000 | color;
    }

    /** "12,345 G". */
    static String gold(long amount) {
        return String.format("%,d G", amount);
    }

    /** "残り 12:34" style: m:ss under an hour, else h時間mm分. */
    static Component remaining(long millis) {
        long seconds = Math.max(0L, millis / 1000);
        if (seconds < 3600) {
            return Component.translatable("screen.eternalrealm.market.time_ms", seconds / 60,
                    String.format("%02d", seconds % 60));
        }
        return Component.translatable("screen.eternalrealm.market.time_hm", seconds / 3600,
                String.format("%02d", seconds / 60 % 60));
    }

    /** "3分前" style. */
    static Component ago(long millis) {
        long minutes = Math.max(0L, millis / 60_000);
        if (minutes < 1) {
            return Component.translatable("screen.eternalrealm.market.ago_now");
        }
        if (minutes < 60) {
            return Component.translatable("screen.eternalrealm.market.ago_minutes", minutes);
        }
        if (minutes < 60 * 48) {
            return Component.translatable("screen.eternalrealm.market.ago_hours", minutes / 60);
        }
        return Component.translatable("screen.eternalrealm.market.ago_days", minutes / 60 / 24);
    }

    /** The item's own tooltip lines (name first), at most {@code max}. */
    static List<Component> tooltip(ItemStack stack, int max) {
        Minecraft minecraft = Minecraft.getInstance();
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, stack));
        return lines.size() > max ? lines.subList(0, max) : lines;
    }

    static List<JsonObject> objects(JsonObject data, String key) {
        List<JsonObject> list = new ArrayList<>();
        if (data != null && data.has(key) && data.get(key).isJsonArray()) {
            JsonArray array = data.getAsJsonArray(key);
            for (JsonElement element : array) {
                if (element.isJsonObject()) {
                    list.add(element.getAsJsonObject());
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
                && object.get(key).getAsBoolean();
    }

    /** Parses a gold amount typed by the player ("1,000", "1万", "2.5k" are not accepted: digits only). */
    static long parseGold(String text) {
        String digits = text.replace(",", "").replace("_", "").trim();
        if (digits.isEmpty() || digits.length() > 10 || !digits.chars().allMatch(Character::isDigit)) {
            return -1L;
        }
        return Long.parseLong(digits);
    }

    /** A name cut to {@code width} pixels with an ellipsis. */
    static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
