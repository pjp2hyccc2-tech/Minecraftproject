package com.eternalrealm.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The gold frame (金枠) of a relic, wherever one is shown: the bag, the equipment slots, the pickup feed and the
 * tooltip border. A double rim of bright and dark gold with a glint that runs around it. Client only.
 */
public final class GoldFrame {
    /** Bright gold (ARGB). */
    public static final int GOLD = 0xFFFFD84A;
    /** Dark gold (ARGB). */
    public static final int GOLD_DARK = 0xFF9A6A12;
    /** The glint. */
    public static final int GLINT = 0xFFFFF6C8;

    private GoldFrame() {
    }

    /**
     * Draws the frame around the box {@code (x, y, w, h)} (outside it by one pixel); {@code tick} moves the glint.
     */
    public static void draw(GuiGraphics graphics, int x, int y, int w, int h, long tick) {
        graphics.renderOutline(x - 1, y - 1, w + 2, h + 2, GOLD_DARK);
        graphics.renderOutline(x, y, w, h, GOLD);
        // corner studs
        graphics.fill(x - 1, y - 1, x + 2, y + 2, GOLD);
        graphics.fill(x + w - 2, y - 1, x + w + 1, y + 2, GOLD);
        graphics.fill(x - 1, y + h - 2, x + 2, y + h + 1, GOLD);
        graphics.fill(x + w - 2, y + h - 2, x + w + 1, y + h + 1, GOLD);
        // the glint: a short bright run that travels around the rim
        int perimeter = 2 * (w + h);
        if (perimeter <= 0) {
            return;
        }
        int at = (int) Math.floorMod(tick * 2L, (long) perimeter);
        for (int i = 0; i < 4; i++) {
            int p = (at + i) % perimeter;
            int px;
            int py;
            if (p < w) {
                px = x + p;
                py = y;
            } else if (p < w + h) {
                px = x + w - 1;
                py = y + (p - w);
            } else if (p < 2 * w + h) {
                px = x + w - 1 - (p - w - h);
                py = y + h - 1;
            } else {
                px = x;
                py = y + h - 1 - (p - 2 * w - h);
            }
            graphics.fill(px, py, px + 1, py + 1, GLINT);
        }
    }

    /** The frame's colours for a tooltip border (top, bottom). */
    public static int borderTop() {
        return GOLD;
    }

    public static int borderBottom() {
        return GOLD_DARK;
    }
}
