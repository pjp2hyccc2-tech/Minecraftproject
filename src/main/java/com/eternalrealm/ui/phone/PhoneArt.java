package com.eternalrealm.ui.phone;

import com.eternalrealm.core.ModInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The 魔導スマホ's pictures ({@code textures/gui/phone/phone.png}, drawn by {@code tools/art/phone.py}; the constants
 * follow its ATLAS): the body, the wallpaper, the app icons, small icons and the pieces of the apps (buttons, tabs,
 * speech bubbles, cards, input fields, badges).
 */
public final class PhoneArt {
    public static final ResourceLocation TEXTURE = new ResourceLocation(ModInfo.MOD_ID, "textures/gui/phone/phone.png");
    static final int TEX = 512;
    public static final int BODY_W = 168;
    public static final int BODY_H = 280;
    /** The screen inside the body. */
    public static final int SX0 = 10;
    public static final int SY0 = 22;
    public static final int SX1 = 158;
    public static final int SY1 = 254;
    /** The rune button at the bottom (centre, radius). */
    public static final int HOME_X = 84;
    public static final int HOME_Y = 267;
    public static final int HOME_R = 9;
    public static final int APP = 28;
    public static final int ICON = 10;

    public enum App { FRIENDS, MESSAGES, CAMERA, FEED, ACTIVITY }

    public enum Icon { BACK, HEART, HEART_FULL, COMMENT, TRASH, SEND, PHOTO, CHECK, CROSS, PLUS, BELL, PERSON }

    /** Nine-slice pieces: u, v, width, height, corner. */
    public enum Piece {
        BUTTON(320, 48, 40, 14, 4),
        BUTTON_HOVER(320, 62, 40, 14, 4),
        BUTTON_OFF(320, 76, 40, 14, 4),
        TAB(364, 48, 40, 12, 4),
        TAB_OFF(364, 60, 40, 12, 4),
        BUBBLE_MINE(408, 48, 12, 12, 4),
        BUBBLE_THEIRS(424, 48, 12, 12, 4),
        CARD(440, 48, 16, 16, 5),
        INPUT(456, 48, 16, 12, 3);

        final int u;
        final int v;
        final int w;
        final int h;
        final int corner;

        Piece(int u, int v, int w, int h, int corner) {
            this.u = u;
            this.v = v;
            this.w = w;
            this.h = h;
            this.corner = corner;
        }
    }

    private PhoneArt() {
    }

    static void begin() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    public static void body(GuiGraphics graphics) {
        begin();
        graphics.blit(TEXTURE, 0, 0, 0, 0, BODY_W, BODY_H, TEX, TEX);
    }

    public static void wallpaper(GuiGraphics graphics) {
        graphics.blit(TEXTURE, SX0, SY0, 168, 0, SX1 - SX0, SY1 - SY0, TEX, TEX);
    }

    public static void app(GuiGraphics graphics, App app, int x, int y) {
        begin();
        graphics.blit(TEXTURE, x, y, 320 + app.ordinal() * APP, 0, APP, APP, TEX, TEX);
    }

    /** A small icon (white, tinted by the colour). */
    public static void icon(GuiGraphics graphics, Icon icon, int x, int y, int rgb) {
        begin();
        graphics.setColor((rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F, 1.0F);
        graphics.blit(TEXTURE, x, y, 320 + icon.ordinal() * ICON, 32, ICON, ICON, TEX, TEX);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** The full red heart keeps its own colour. */
    public static void heart(GuiGraphics graphics, boolean full, int x, int y) {
        if (full) {
            begin();
            graphics.blit(TEXTURE, x, y, 320 + Icon.HEART_FULL.ordinal() * ICON, 32, ICON, ICON, TEX, TEX);
        } else {
            icon(graphics, Icon.HEART, x, y, 0xE8DCF8);
        }
    }

    public static void badge(GuiGraphics graphics, int x, int y) {
        begin();
        graphics.blit(TEXTURE, x, y, 472, 48, 9, 9, TEX, TEX);
    }

    public static void dot(GuiGraphics graphics, boolean online, int x, int y) {
        begin();
        graphics.blit(TEXTURE, x, y, online ? 484 : 490, 48, 5, 5, TEX, TEX);
    }

    public static void homeGlow(GuiGraphics graphics, float alpha) {
        begin();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(TEXTURE, HOME_X - 10, HOME_Y - 10, 320, 96, 20, 20, TEX, TEX);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void noPhoto(GuiGraphics graphics, int x, int y) {
        begin();
        graphics.blit(TEXTURE, x, y, 344, 96, 24, 18, TEX, TEX);
    }

    /** A nine-slice piece stretched over {@code x, y, width, height}. */
    public static void piece(GuiGraphics graphics, Piece piece, int x, int y, int width, int height) {
        int c = Math.min(piece.corner, Math.min(width, height) / 2);
        if (c <= 0) {
            return;
        }
        begin();
        int u = piece.u;
        int v = piece.v;
        int far = piece.w - c;
        int low = piece.h - c;
        int innerW = piece.w - 2 * c;
        int innerH = piece.h - 2 * c;
        int midW = width - 2 * c;
        int midH = height - 2 * c;
        graphics.blit(TEXTURE, x, y, u, v, c, c, TEX, TEX);
        graphics.blit(TEXTURE, x + width - c, y, u + far, v, c, c, TEX, TEX);
        graphics.blit(TEXTURE, x, y + height - c, u, v + low, c, c, TEX, TEX);
        graphics.blit(TEXTURE, x + width - c, y + height - c, u + far, v + low, c, c, TEX, TEX);
        if (midW > 0) {
            graphics.blit(TEXTURE, x + c, y, midW, c, u + c, v, innerW, c, TEX, TEX);
            graphics.blit(TEXTURE, x + c, y + height - c, midW, c, u + c, v + low, innerW, c, TEX, TEX);
        }
        if (midH > 0) {
            graphics.blit(TEXTURE, x, y + c, c, midH, u, v + c, c, innerH, TEX, TEX);
            graphics.blit(TEXTURE, x + width - c, y + c, c, midH, u + far, v + c, c, innerH, TEX, TEX);
        }
        if (midW > 0 && midH > 0) {
            graphics.blit(TEXTURE, x + c, y + c, midW, midH, u + c, v + c, innerW, innerH, TEX, TEX);
        }
    }
}
