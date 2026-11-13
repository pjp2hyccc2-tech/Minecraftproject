package com.eternalrealm.ui.gate;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.ui.GoldFrame;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * The look shared by the gate's screens (転移門, 出発の準備) and the ready check's HUD panel: the textures of
 * tools/art/gate_screen.py (the atlas layout is its {@code ATLAS}), colours, and the night panel in its gold frame.
 * Client only.
 */
final class GateArt {
    static final ResourceLocation ATLAS = new ResourceLocation(ModInfo.MOD_ID, "textures/gui/gate/atlas.png");
    static final ResourceLocation CIRCLE = new ResourceLocation(ModInfo.MOD_ID, "textures/gui/gate/circle.png");
    static final ResourceLocation SEALED = new ResourceLocation(ModInfo.MOD_ID, "textures/gui/gate/sealed.png");
    private static final Map<String, ResourceLocation> PICTURES = new HashMap<>();
    static final int TEX = 256;
    static final int PIC_TEX_W = 472;
    static final int PIC_TEX_H = 200;

    // the atlas
    static final int CORNER_V = 0;
    static final int GEM_U = 96;
    static final int HALO_U = 216;
    static final int EMBLEM_V = 24;
    static final int LOCK_U = 144;
    static final int LAUREL_U = 156;
    static final int POINTER_U = 172;
    static final int ICON_U = 180;
    static final int MEDAL_V = 40;
    static final int BADGE_U = 80;
    static final int STUD_U = 114;
    static final int DIVIDER_U = 121;
    static final int MARK_U = 121;
    static final int MARK_V = 50;
    static final int RIBBON_V = 60;
    static final int BUTTON_V = 90;
    static final int PILL_U = 96;
    static final int ROW_V = 162;
    static final int OK_BUTTON_U = 0;
    static final int WAIT_BUTTON_U = 96;
    static final int TOGGLE_V = 202;

    // colours
    static final int TEXT = 0xFFEDE3CC;
    static final int MUTED = 0xFFA89A86;
    static final int GOLD = 0xFFFFD866;
    static final int GOLD_DIM = 0xFFD2AE5A;
    static final int WARN = 0xFFFF8A6A;
    static final int READY = 0xFF8CE08C;
    static final int WAITING = 0xFFFFC266;

    private GateArt() {
    }

    static ResourceLocation picture(String id) {
        return PICTURES.computeIfAbsent(id, key -> new ResourceLocation(ModInfo.MOD_ID,
                "textures/gui/gate/" + key + ".png"));
    }

    static void atlas(GuiGraphics graphics, int x, int y, int u, int v, int w, int h) {
        graphics.blit(ATLAS, x, y, u, v, w, h, TEX, TEX);
    }

    static void text(GuiGraphics graphics, Font font, Component text, float x, float y, int color, float size) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(size, size, 1.0F);
        graphics.drawString(font, text, 0, 0, color, true);
        pose.popPose();
    }

    static void textSeq(GuiGraphics graphics, Font font, FormattedCharSequence text, float x, float y, int color,
                        float size) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(size, size, 1.0F);
        graphics.drawString(font, text, 0, 0, color, true);
        pose.popPose();
    }

    /** Draws {@code text} at up to {@code size}, shrunk (down to {@code min}) or shortened to fit {@code maxWidth}. */
    static float fitted(GuiGraphics graphics, Font font, Component text, float x, float y, float maxWidth, int color,
                        float size, float min) {
        int width = font.width(text);
        float s = width <= 0 ? size : Math.min(size, maxWidth / width);
        if (s >= min) {
            text(graphics, font, text, x, y, color, s);
            return width * s;
        }
        String plain = text.getString();
        String cut = font.plainSubstrByWidth(plain, Math.max(0, (int) (maxWidth / min) - font.width("…"))) + "…";
        text(graphics, font, Component.literal(cut).withStyle(text.getStyle()), x, y, color, min);
        return font.width(cut) * min;
    }

    static void centered(GuiGraphics graphics, Font font, Component text, float cx, float y, int color, float size) {
        text(graphics, font, text, cx - font.width(text) * size / 2.0F, y, color, size);
    }

    static float rand(int seed) {
        int h = seed * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA77;
        h ^= h >>> 13;
        h *= 0xC2B2AE3D;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    static int argb(int rgb, float alpha) {
        return (Mth.clamp((int) (alpha * 255.0F), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    static void dot(GuiGraphics graphics, float x, float y, int rgb, float alpha, boolean glow) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        if (glow) {
            graphics.fill(-1, -1, 2, 2, argb(rgb, alpha * 0.22F));
        }
        graphics.fill(0, 0, 1, 1, argb(rgb, alpha));
        pose.popPose();
    }

    static void circle(GuiGraphics graphics, float cx, float cy, int size, float degrees, float r, float g, float b,
                       float alpha) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0.0F);
        pose.mulPose(Axis.ZP.rotationDegrees(degrees));
        graphics.setColor(r, g, b, alpha);
        graphics.blit(CIRCLE, -size / 2, -size / 2, size, size, 0.0F, 0.0F, 256, 256, 256, 256);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
    }

    /**
     * The night panel {@code (0, 0, w, h)}: shadow, gradient, twinkling stars, two magic circles turning against each
     * other ({@code circles} 0 for none), rising motes, the gold frame with its running glint, jewelled corners and
     * studs.
     */
    static void panel(GuiGraphics graphics, int w, int h, float t, int circles) {
        graphics.fill(-3, -1, w + 5, h + 5, 0x70000000);
        graphics.fillGradient(0, 0, w, h, 0xF4161C34, 0xF40A0C1A);
        int stars = Math.max(8, w * h / 2200);
        for (int i = 0; i < stars; i++) {
            float x = 6 + rand(i * 7 + 1) * (w - 12);
            float y = 6 + rand(i * 7 + 2) * (h - 12);
            float twinkle = 0.5F + 0.5F * Mth.sin(t * (0.8F + rand(i * 7 + 3) * 1.6F) + i);
            dot(graphics, x, y, 0xE8E4FF, 0.12F + 0.4F * twinkle * rand(i * 7 + 4), false);
        }
        if (circles > 0) {
            circle(graphics, w / 2.0F, h / 2.0F + 6, circles, t * 5.0F, 0.95F, 0.78F, 0.4F, 0.085F);
            circle(graphics, w / 2.0F, h / 2.0F + 6, circles * 150 / 236, -t * 8.0F, 0.95F, 0.78F, 0.4F, 0.06F);
        }
        int motes = Math.max(6, w * h / 4700);
        for (int i = 0; i < motes; i++) {
            float speed = 4.0F + rand(i * 11 + 1) * 7.0F;
            float x = 10 + rand(i * 11 + 2) * (w - 20) + Mth.sin(t * 0.7F + i) * 4.0F;
            float y = h - 6 - ((rand(i * 11 + 3) * h + t * speed) % (h - 12));
            float edge = Math.min(1.0F, Math.min((y - 6) / 24.0F, (h - 6 - y) / 24.0F));
            dot(graphics, x, y, 0xFFD866, 0.45F * edge * (0.6F + 0.4F * Mth.sin(t * 2.0F + i)), true);
        }
        graphics.renderOutline(-1, -1, w + 2, h + 2, 0xFF2A1606);
        GoldFrame.draw(graphics, 1, 1, w - 2, h - 2, (long) (t * 20.0F));
        graphics.renderOutline(4, 4, w - 8, h - 8, 0x8A8A6420);
        atlas(graphics, -1, -1, 0, CORNER_V, 24, 24);
        atlas(graphics, w - 23, -1, 24, CORNER_V, 24, 24);
        atlas(graphics, -1, h - 23, 48, CORNER_V, 24, 24);
        atlas(graphics, w - 23, h - 23, 72, CORNER_V, 24, 24);
        atlas(graphics, -3, h / 2 - 3, STUD_U, MEDAL_V, 7, 7);
        atlas(graphics, w - 4, h / 2 - 3, STUD_U, MEDAL_V, 7, 7);
        atlas(graphics, w / 2 - 3, h - 4, STUD_U, MEDAL_V, 7, 7);
    }

    /** The red title ribbon hanging over the panel's top edge ({@code top} is its own top, usually -9). */
    static void ribbon(GuiGraphics graphics, Font font, Component title, int w, int top) {
        atlas(graphics, w / 2 - 100, top, 0, RIBBON_V, 200, 30);
        centered(graphics, font, title, w / 2.0F, top + 9.5F, 0xFFFFE9B0, 1.25F);
    }
}
