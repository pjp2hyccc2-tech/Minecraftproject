package com.reisatsu.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reisatsu.Reisatsu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Spoken lines at the bottom of the screen (docs/DESIGN.md §3.13 subtitles): the medium's with her portrait, the
 * spirits' voices in cold grey, the store PA, the phone. Lines queue; short environmental subtitles have their own slot.
 */
public final class Dialogue {
    private static final ResourceLocation PORTRAIT = Reisatsu.id("textures/gui/medium_portrait.png");

    private record Line(String speaker, String key, int ticks) {}

    private static final Deque<Line> QUEUE = new ArrayDeque<>();
    private static Line current;
    private static int t;
    private static String sub = "";
    private static int subTicks = -1, subLength;

    private Dialogue() {}

    public static void play(String speaker, List<String> lines, List<Integer> ticks) {
        // a spirit's voice interrupts nothing: it waits, unless the queue is long
        for (int i = 0; i < lines.size(); i++) QUEUE.add(new Line(speaker, lines.get(i), ticks.get(i)));
        if (current == null) advance();
    }

    public static void subtitle(String key, int ticks) {
        sub = key;
        subTicks = 0;
        subLength = ticks;
    }

    public static void clear() {
        QUEUE.clear();
        current = null;
        subTicks = -1;
    }

    public static boolean speaking() {
        return current != null;
    }

    @javax.annotation.Nullable
    public static String currentKey() {
        return current == null ? null : current.key();
    }

    private static void advance() {
        current = QUEUE.poll();
        t = 0;
    }

    public static void tick() {
        if (current != null && ++t >= current.ticks()) advance();
        if (subTicks >= 0 && ++subTicks > subLength) subTicks = -1;
    }

    private static int color(String speaker) {
        return switch (speaker) {
            case "medium" -> 0xF0E6D2;
            case "pa" -> 0xD8CFA8;
            case "phone" -> 0xA8B4BC;
            case "" -> 0xD8D8D8;
            default -> 0xA9BCCB;
        };
    }

    public static void render(GuiGraphics g, Font font, int w, int h, float partial) {
        int y = h - 58;
        if (current != null) {
            float k = t + partial;
            float a = Math.min(1.0f, k / 6.0f) * Math.min(1.0f, (current.ticks() - k) / 8.0f);
            a = Mth.clamp(a, 0.0f, 1.0f);
            String text = I18n.get(current.key(), ClientState.introName);
            int shown = (int) Math.min(text.length(), text.length() * (k / Math.max(1.0f, current.ticks() * 0.45f)) + 1);
            String visible = text.substring(0, Math.max(0, Math.min(text.length(), shown)));
            boolean medium = current.speaker().equals("medium");
            int maxW = Math.min(w - 120, 340);
            List<FormattedCharSequence> lines = font.split(Component.literal(visible), maxW);
            List<FormattedCharSequence> full = font.split(Component.literal(text), maxW);
            int boxH = Math.max(full.size() * 11 + 10, medium ? 44 : 0);
            int boxW = maxW + 16 + (medium ? 48 : 0);
            int x0 = (w - boxW) / 2;
            int y0 = y - boxH + 14;
            g.fill(x0, y0, x0 + boxW, y0 + boxH, HorrorOverlay.argb(a * 0.62f, 0x06070A));
            int tx = x0 + 8;
            if (medium) {
                if (Tex.has(PORTRAIT)) {
                    RenderSystem.enableBlend();
                    g.setColor(1.0f, 1.0f, 1.0f, a);
                    g.blit(PORTRAIT, x0 + 4, y0 + 2, 40, 40, 0, 0, 64, 64, 64, 64);
                    g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
                    RenderSystem.disableBlend();
                } else {
                    // a stand-in: dark kimono, pale face, grey bun
                    int px = x0 + 4, py = y0 + 2;
                    g.fill(px, py, px + 40, py + 40, HorrorOverlay.argb(a, 0x15121A));
                    g.fill(px + 8, py + 28, px + 32, py + 40, HorrorOverlay.argb(a, 0x3A2440));
                    g.fill(px + 13, py + 10, px + 27, py + 28, HorrorOverlay.argb(a, 0xC9B9A8));
                    g.fill(px + 12, py + 4, px + 28, py + 11, HorrorOverlay.argb(a, 0x8E8A86));
                    g.fill(px + 16, py + 1, px + 24, py + 5, HorrorOverlay.argb(a, 0x7A7672));
                    g.fill(px + 15, py + 17, px + 18, py + 18, HorrorOverlay.argb(a, 0x2A2020));
                    g.fill(px + 22, py + 17, px + 25, py + 18, HorrorOverlay.argb(a, 0x2A2020));
                }
                tx += 46;
            }
            String name = speakerName(current.speaker());
            if (!name.isEmpty()) g.drawString(font, name, tx, y0 - 10, HorrorOverlay.argb(a * 0.8f, color(current.speaker())), false);
            int ly = y0 + 5;
            for (FormattedCharSequence l : lines) {
                g.drawString(font, l, tx, ly, HorrorOverlay.argb(a, color(current.speaker())), false);
                ly += 11;
            }
            y = y0 - 16;
        }
        if (subTicks >= 0 && !sub.isEmpty()) {
            float k = subTicks + partial;
            float a = Mth.clamp(Math.min(k / 6.0f, (subLength - k) / 10.0f), 0.0f, 1.0f);
            String text = I18n.get(sub);
            int tw = font.width(text);
            g.fill(w / 2 - tw / 2 - 5, y - 2, w / 2 + tw / 2 + 5, y + 10, HorrorOverlay.argb(a * 0.5f, 0x050505));
            g.drawString(font, text, w / 2 - tw / 2, y, HorrorOverlay.argb(a * 0.9f, 0xC8C8C8), false);
        }
    }

    private static String speakerName(String speaker) {
        if (speaker.isEmpty()) return "";
        String key = "speaker.reisatsu." + speaker;
        return I18n.exists(key) ? I18n.get(key) : "";
    }
}
