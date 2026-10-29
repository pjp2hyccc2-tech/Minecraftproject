package com.huntcraft.quest.client;

import com.huntcraft.HuntCraft;
import com.huntcraft.quest.QuestDef;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/** Shared drawing helpers / palette for the quest screens and HUD (guild parchment style). */
@OnlyIn(Dist.CLIENT)
public final class QuestUi {
    public static final int BG = 0xE6231C15;
    public static final int PANEL = 0xF02E261C;
    public static final int PANEL_DARK = 0xF01C1611;
    public static final int GOLD = 0xFFD8B35A;
    public static final int GOLD_DARK = 0xFF8A6A2A;
    public static final int TEXT = 0xFFF2E8D2;
    public static final int TEXT_DIM = 0xFFA89A80;
    public static final int RED = 0xFFE05A4A;
    public static final int GREEN = 0xFF7FD06A;
    public static final int BLUE = 0xFF6AB4E8;
    public static final int HIGHLIGHT = 0xFF5C4824;
    /** Map textures cover tile coordinates [24, 489) (playable square + 8 block margin) on 256 pixels. */
    public static final int MAP_U0 = 24;
    public static final int MAP_SPAN = 465;

    private QuestUi() {
    }

    public static ResourceLocation mapTexture(String mapId) {
        return HuntCraft.id("textures/gui/quest_map/" + (mapId == null || mapId.isEmpty() ? "verdant_hills" : mapId) + ".png");
    }

    public static void frame(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, GOLD);
        g.fill(x, y + h - 1, x + w, y + h, GOLD);
        g.fill(x, y, x + 1, y + h, GOLD);
        g.fill(x + w - 1, y, x + w, y + h, GOLD);
        g.fill(x + 2, y + 2, x + w - 2, y + 3, GOLD_DARK);
        g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, GOLD_DARK);
        // corner studs
        g.fill(x - 1, y - 1, x + 3, y + 3, GOLD);
        g.fill(x + w - 3, y - 1, x + w + 1, y + 3, GOLD);
        g.fill(x - 1, y + h - 3, x + 3, y + h + 1, GOLD);
        g.fill(x + w - 3, y + h - 3, x + w + 1, y + h + 1, GOLD);
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL_DARK);
        g.fill(x, y, x + w, y + 1, GOLD_DARK);
        g.fill(x, y + h - 1, x + w, y + h, GOLD_DARK);
        g.fill(x, y, x + 1, y + h, GOLD_DARK);
        g.fill(x + w - 1, y, x + w, y + h, GOLD_DARK);
    }

    /** Draws word-wrapped text, returns the y below the last line. */
    public static int wrap(GuiGraphics g, Font font, Component text, int x, int y, int width, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(text, width);
        int n = 0;
        for (FormattedCharSequence l : lines) {
            if (n++ >= maxLines) break;
            g.drawString(font, l, x, y, color, false);
            y += font.lineHeight + 1;
        }
        return y;
    }

    public static String time(int ticks) {
        int s = Math.max(0, ticks / 20);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    public static String stars(int n) {
        return "★".repeat(Math.max(0, n));
    }

    public static int typeColor(QuestDef.Type t) {
        return switch (t) {
            case HUNT -> 0xFFE8704A;
            case CAPTURE -> 0xFF6AB4E8;
            case SLAY -> 0xFFE8B84A;
            case GATHER -> 0xFF7FD06A;
        };
    }

    public static int climateColor(int climate) {
        return switch (climate) {
            case 1 -> 0xFF9FD8FF;
            case 2 -> 0xFFFF8A4A;
            default -> TEXT;
        };
    }
}
