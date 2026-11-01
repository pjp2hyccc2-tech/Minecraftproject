package com.frostbound.client;

import com.frostbound.FrostboundMod;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/** The FROSTBOUND look: dark riveted iron panels with ember-orange accents. All drawing is code + small icons. */
public final class Theme {
    public static final int PANEL = 0xF21A1D21;
    public static final int PANEL_SOLID = 0xFF1A1D21;
    public static final int PANEL_INNER = 0xF2111316;
    public static final int EDGE_LIGHT = 0xFF4C5259;
    public static final int EDGE_DARK = 0xFF07080A;
    public static final int RIVET = 0xFF737880;
    public static final int ORANGE = 0xFFE8892B;
    public static final int ORANGE_DIM = 0xFF9C5A1C;
    public static final int EMBER = 0xFFFFB45A;
    public static final int TEXT = 0xFFE8E2D6;
    public static final int TEXT_DIM = 0xFF9C968C;
    public static final int TEXT_FAINT = 0xFF686460;
    public static final int GOOD = 0xFF86CF6E;
    public static final int BAD = 0xFFE5604E;
    public static final int WARN = 0xFFE9C24B;
    public static final int COLD = 0xFF71B6EC;
    public static final int BUTTON = 0xFF2B2F35;
    public static final int BUTTON_HOVER = 0xFF3A3F47;
    public static final int BUTTON_OFF = 0xFF1E2024;

    private Theme() {
    }

    public static ResourceLocation icon(String name) {
        return FrostboundMod.id("textures/gui/icon/" + name + ".png");
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    /** Riveted iron panel. */
    public static void panel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, PANEL);
        g.fill(x0, y0, x1, y0 + 1, EDGE_LIGHT);
        g.fill(x0, y0, x0 + 1, y1, EDGE_LIGHT);
        g.fill(x0, y1 - 1, x1, y1, EDGE_DARK);
        g.fill(x1 - 1, y0, x1, y1, EDGE_DARK);
        rivet(g, x0 + 2, y0 + 2);
        rivet(g, x1 - 4, y0 + 2);
        rivet(g, x0 + 2, y1 - 4);
        rivet(g, x1 - 4, y1 - 4);
    }

    /** Panel with an orange title strip. */
    public static void titled(GuiGraphics g, int x0, int y0, int x1, int y1, Component title) {
        panel(g, x0, y0, x1, y1);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 12, 0xFF24282D);
        g.fill(x0 + 1, y0 + 12, x1 - 1, y0 + 13, ORANGE_DIM);
        g.drawString(font(), title, x0 + 7, y0 + 3, EMBER, false);
    }

    public static void inset(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, PANEL_INNER);
        g.fill(x0, y0, x1, y0 + 1, EDGE_DARK);
        g.fill(x0, y0, x0 + 1, y1, EDGE_DARK);
        g.fill(x0, y1 - 1, x1, y1, 0xFF30343A);
        g.fill(x1 - 1, y0, x1, y1, 0xFF30343A);
    }

    private static void rivet(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 2, y + 2, RIVET);
        g.fill(x + 1, y + 1, x + 2, y + 2, 0xFF3A3D42);
    }

    public static void button(GuiGraphics g, int x, int y, int w, int h, Component label, boolean hover, boolean enabled,
            boolean selected) {
        int bg = !enabled ? BUTTON_OFF : selected ? 0xFF4A3218 : hover ? BUTTON_HOVER : BUTTON;
        g.fill(x, y, x + w, y + h, bg);
        int top = selected ? ORANGE : hover && enabled ? ORANGE_DIM : EDGE_LIGHT;
        g.fill(x, y, x + w, y + 1, top);
        g.fill(x, y, x + 1, y + h, top);
        g.fill(x, y + h - 1, x + w, y + h, EDGE_DARK);
        g.fill(x + w - 1, y, x + w, y + h, EDGE_DARK);
        if (label != null) {
            Font f = font();
            int tw = f.width(label);
            int color = !enabled ? TEXT_FAINT : selected || hover ? EMBER : TEXT;
            if (tw > w - 4) {
                String s = f.plainSubstrByWidth(label.getString(), w - 6);
                g.drawString(f, s, x + 3, y + (h - 8) / 2, color, false);
            } else {
                g.drawString(f, label, x + (w - tw) / 2, y + (h - 8) / 2, color, false);
            }
        }
    }

    public static void bar(GuiGraphics g, int x, int y, int w, int h, double fraction, int color) {
        g.fill(x, y, x + w, y + h, 0xFF0B0C0E);
        int fw = (int) Math.round(Math.max(0, Math.min(1, fraction)) * (w - 2));
        if (fw > 0) {
            g.fill(x + 1, y + 1, x + 1 + fw, y + h - 1, color);
            g.fill(x + 1, y + 1, x + 1 + fw, y + 2, brighten(color));
        }
    }

    public static int brighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 40);
        int gr = Math.min(255, ((argb >> 8) & 0xFF) + 40);
        int b = Math.min(255, (argb & 0xFF) + 40);
        return (argb & 0xFF000000) | (r << 16) | (gr << 8) | b;
    }

    public static void iconAt(GuiGraphics g, String name, int x, int y, int size) {
        g.blit(icon(name), x, y, size, size, 0, 0, 16, 16, 16, 16);
    }

    /** Temperature colour from deep blue (-50 °C) to orange (+25 °C). */
    public static int tempColor(double t) {
        double[][] stops = {{-50, 0x2B, 0x3F, 0x9E}, {-30, 0x3D, 0x7D, 0xD8}, {-10, 0x7C, 0xC7, 0xEE}, {0, 0xB8, 0xE6, 0xE0},
                {10, 0x9B, 0xD9, 0x6B}, {18, 0xF0, 0xD0, 0x50}, {25, 0xF0, 0x8A, 0x2E}};
        if (t <= stops[0][0]) {
            return rgb(stops[0]);
        }
        for (int i = 1; i < stops.length; i++) {
            if (t <= stops[i][0]) {
                double f = (t - stops[i - 1][0]) / (stops[i][0] - stops[i - 1][0]);
                int r = (int) (stops[i - 1][1] + (stops[i][1] - stops[i - 1][1]) * f);
                int gr = (int) (stops[i - 1][2] + (stops[i][2] - stops[i - 1][2]) * f);
                int b = (int) (stops[i - 1][3] + (stops[i][3] - stops[i - 1][3]) * f);
                return 0xFF000000 | (r << 16) | (gr << 8) | b;
            }
        }
        return rgb(stops[stops.length - 1]);
    }

    private static int rgb(double[] s) {
        return 0xFF000000 | ((int) s[1] << 16) | ((int) s[2] << 8) | (int) s[3];
    }

    public static String temp(double t) {
        return String.format(Locale.ROOT, "%+.0f°C", t);
    }

    public static String num(double v) {
        double a = Math.abs(v);
        if (a >= 10000) {
            return String.format(Locale.ROOT, "%.0fk", v / 1000);
        }
        if (a >= 1000) {
            return String.format(Locale.ROOT, "%.1fk", v / 1000);
        }
        return String.format(Locale.ROOT, "%.0f", Math.floor(v));
    }

    public static String signed(double v) {
        return String.format(Locale.ROOT, "%+.0f", v);
    }

    /** Draws wrapped text and returns the height used. */
    public static int wrapped(GuiGraphics g, Component text, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = font().split(text, width);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font(), lines.get(i), x, y + i * 10, color, false);
        }
        return lines.size() * 10;
    }

    public static int wrappedHeight(Component text, int width) {
        return font().split(text, width).size() * 10;
    }
}
