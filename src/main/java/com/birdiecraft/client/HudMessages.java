package com.birdiecraft.client;

import com.birdiecraft.network.S2CMessagePacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Animated announcement texts. */
public final class HudMessages {
    private record Msg(int kind, Component text, int color, int duration, long startMs) {
    }

    private static final List<Msg> ACTIVE = new ArrayList<>();

    private HudMessages() {
    }

    public static void add(int kind, Component text, int color, int durationTicks) {
        // a new title replaces the previous title, same for subtitles
        if (kind != S2CMessagePacket.KIND_TOAST) ACTIVE.removeIf(m -> m.kind == kind);
        ACTIVE.add(new Msg(kind, text, color, Math.max(10, durationTicks), System.currentTimeMillis()));
        if (ACTIVE.size() > 8) ACTIVE.remove(0);
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static void render(GuiGraphics g, Font font, int width, int height) {
        long now = System.currentTimeMillis();
        ACTIVE.removeIf(m -> now - m.startMs > m.duration * 50L);
        int toastY = 70;
        for (Msg m : ACTIVE) {
            float t = (now - m.startMs) / 1000f;
            float life = m.duration * 0.05f;
            float fadeOut = Math.min(1f, Math.max(0f, (life - t) / 0.4f));
            int alpha = (int) (255 * Math.min(1f, Math.min(t / 0.12f, fadeOut)));
            if (alpha < 8) continue;
            int argb = (alpha << 24) | (m.color & 0xFFFFFF);
            switch (m.kind) {
                case S2CMessagePacket.KIND_TITLE -> {
                    // pop-in with overshoot
                    float pop;
                    if (t < 0.12f) pop = 0.4f + t / 0.12f * 0.75f;
                    else if (t < 0.32f) pop = 1.15f - (t - 0.12f) / 0.2f * 0.15f;
                    else pop = 1f;
                    float scale = 3.2f * pop;
                    int tw = font.width(m.text);
                    g.pose().pushPose();
                    g.pose().translate(width / 2f, height / 2f - 46, 0);
                    g.pose().scale(scale, scale, 1f);
                    g.drawString(font, m.text, -tw / 2 + 1, -4 + 1, (alpha << 24), false);
                    g.drawString(font, m.text, -tw / 2, -4, argb, false);
                    g.pose().popPose();
                }
                case S2CMessagePacket.KIND_SUB -> {
                    float scale = 1.6f;
                    int tw = font.width(m.text);
                    g.pose().pushPose();
                    g.pose().translate(width / 2f, height / 2f - 18, 0);
                    g.pose().scale(scale, scale, 1f);
                    g.drawString(font, m.text, -tw / 2, -4, argb, true);
                    g.pose().popPose();
                }
                default -> {
                    int tw = font.width(m.text);
                    int x = width - tw - 10;
                    g.fill(x - 4, toastY - 3, width - 6, toastY + 10, (alpha / 2 << 24) | 0x101820);
                    g.drawString(font, m.text, x, toastY, argb, true);
                    toastY += 15;
                }
            }
        }
    }
}
