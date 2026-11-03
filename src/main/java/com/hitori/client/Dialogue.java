package com.hitori.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Spoken lines at the bottom centre (docs/DESIGN.md §3.8 subtitles): your own ritual lines (warm white, the doll's name
 * in them), the doll's voice (cold red-grey), the woman's whisper, the TV, the phone. Lines queue; short environmental
 * subtitles have their own slot.
 */
public final class Dialogue {
    private record Line(String speaker, String text, int ticks) {}

    private static final Deque<Line> QUEUE = new ArrayDeque<>();
    private static Line current;
    private static int t;
    private static String sub = "";
    private static int subTicks = -1, subLength;

    private Dialogue() {}

    public static void line(String speaker, String key, List<String> args, int ticks) {
        String text = I18n.get(key, args.toArray());
        QUEUE.add(new Line(speaker, text, Math.max(30, ticks)));
        if (QUEUE.size() > 4) QUEUE.poll();
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
            case "me" -> 0xF0E8DC;
            case "doll" -> 0xD0A0A0;
            case "woman", "whisper" -> 0xA8B4C4;
            case "phone" -> 0xA8C0B4;
            case "tv" -> 0xB8B8C8;
            default -> 0xD8D8D8;
        };
    }

    public static void render(GuiGraphics g, Font font, int w, int h, float partial) {
        int y = h - 62;
        if (current != null) {
            float k = t + partial;
            float a = Mth.clamp(Math.min(1.0f, k / 6.0f) * Math.min(1.0f, (current.ticks() - k) / 8.0f), 0.0f, 1.0f);
            String text = current.text();
            int maxW = Math.min(w - 80, 360);
            List<FormattedCharSequence> lines = font.split(Component.literal(text), maxW);
            int boxH = lines.size() * 11 + 8;
            int boxW = 0;
            for (FormattedCharSequence l : lines) boxW = Math.max(boxW, font.width(l));
            boxW += 16;
            int x0 = (w - boxW) / 2, y0 = y - boxH + 12;
            g.fill(x0, y0, x0 + boxW, y0 + boxH, HorrorOverlay.argb(a * 0.55f, 0x06070A));
            int ly = y0 + 4;
            for (FormattedCharSequence l : lines) {
                g.drawString(font, l, (w - font.width(l)) / 2, ly, HorrorOverlay.argb(a, color(current.speaker())), false);
                ly += 11;
            }
            y = y0 - 14;
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
}
