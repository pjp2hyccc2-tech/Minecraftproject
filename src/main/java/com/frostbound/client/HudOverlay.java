package com.frostbound.client;

import com.frostbound.core.resources.ResourceType;
import com.frostbound.world.Dims;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** In-game HUD in the frozen city: resources, time, temperature, weather, hope/discontent, generator, toasts. */
public final class HudOverlay implements IGuiOverlay {
    public static final HudOverlay INSTANCE = new HudOverlay();
    public static boolean visible = true;

    private HudOverlay() {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!visible || mc.options.hideGui || mc.level == null || !mc.level.dimension().equals(Dims.FROZEN_WASTES)
                || mc.screen instanceof ManagementScreen) {
            return;
        }
        if (!ClientCity.hasCity()) {
            if (ClientCity.frostbound()) {
                Component c = Component.translatable("frostbound.ui.not_founded");
                Theme.panel(g, 4, 4, 12 + mc.font.width(c), 20);
                g.drawString(mc.font, c, 8, 8, Theme.EMBER, false);
            }
            toasts(g, width, height - 40, true);
            return;
        }
        Font font = mc.font;
        CompoundTag s = ClientCity.snap();
        int x0 = 4;
        int y0 = 4;
        int w = 168;
        Theme.panel(g, x0, y0, x0 + w, y0 + 62);
        String time = String.format(Locale.ROOT, "%02d:%02d", s.getInt("hour"), s.getInt("minute"));
        g.drawString(font, Component.translatable("frostbound.ui.day", s.getInt("day")), x0 + 6, y0 + 5, Theme.EMBER, false);
        g.drawString(font, time, x0 + 52, y0 + 5, Theme.TEXT, false);
        double outside = s.getDouble("outside");
        String weather = s.getString("weather");
        Theme.iconAt(g, ManagementScreen.weatherIcon(weather), x0 + 90, y0 + 3, 12);
        g.drawString(font, Theme.temp(outside), x0 + 104, y0 + 5, Theme.tempColor(outside), false);
        if (s.getBoolean("paused")) {
            g.drawString(font, "II", x0 + w - 12, y0 + 5, Theme.WARN, false);
        } else if (s.getInt("speed") > 1) {
            g.drawString(font, s.getInt("speed") + "x", x0 + w - 16, y0 + 5, Theme.EMBER, false);
        }
        int i = 0;
        for (ResourceType r : ResourceType.values()) {
            int cx = x0 + 6 + (i % 3) * 54;
            int cy = y0 + 17 + (i / 3) * 12;
            CompoundTag res = ClientCity.resource(r.id());
            double days = res.getDouble("days");
            boolean consumed = res.getDouble("cons") > 0;
            Theme.iconAt(g, r.id(), cx, cy, 10);
            g.drawString(font, Theme.num(res.getDouble("amount")), cx + 12, cy + 1,
                    consumed && days < 1.5 ? Theme.BAD : consumed && days < 3 ? Theme.WARN : Theme.TEXT, false);
            i++;
        }
        CompoundTag pop = ClientCity.pop();
        int by = y0 + 43;
        Theme.iconAt(g, "hope", x0 + 6, by - 1, 9);
        Theme.bar(g, x0 + 17, by, 56, 6, pop.getDouble("hope") / 100.0, 0xFF6FB0E0);
        Theme.iconAt(g, "discontent", x0 + 80, by - 1, 9);
        Theme.bar(g, x0 + 91, by, 56, 6, pop.getDouble("disc") / 100.0, 0xFFD8604A);
        CompoundTag gen = ClientCity.generator();
        String genState = gen.getBoolean("broken") ? "broken" : gen.getBoolean("fuelOut") ? "fuel_out" : gen.getInt("level") == 0 ? "off" : "running";
        Theme.iconAt(g, "generator", x0 + 6, y0 + 51, 9);
        Component gl = Component.translatable("frostbound.ui.hud_gen", gen.getInt("level"),
                Component.translatable("frostbound.ui.gen_state." + genState, String.format(Locale.ROOT, "%.0f", gen.getDouble("repairHours"))));
        g.drawString(font, gl, x0 + 17, y0 + 52, genState.equals("running") ? Theme.TEXT_DIM : Theme.BAD, false);
        Theme.iconAt(g, "population", x0 + 112, y0 + 51, 9);
        g.drawString(font, String.valueOf(pop.getInt("alive")), x0 + 123, y0 + 52, Theme.TEXT, false);
        if (pop.getInt("sick") > 0) {
            Theme.iconAt(g, "sick", x0 + 140, y0 + 51, 9);
            g.drawString(font, String.valueOf(pop.getInt("sick")), x0 + 151, y0 + 52, Theme.WARN, false);
        }
        int ny = y0 + 66;
        CompoundTag ev = ClientCity.event();
        if (ev != null) {
            Component c = Component.translatable("frostbound.ui.event_pending");
            Theme.panel(g, x0, ny, x0 + w, ny + 16);
            Theme.iconAt(g, "warning", x0 + 4, ny + 3, 10);
            g.drawString(font, c, x0 + 17, ny + 4, (System.currentTimeMillis() / 500) % 2 == 0 ? Theme.EMBER : Theme.TEXT, false);
            ny += 18;
        }
        g.drawString(font, Component.translatable("frostbound.ui.open_hint"), x0 + 2, ny + 2, Theme.TEXT_FAINT, false);
        toasts(g, width, height - 40, true);
        tutorial(g, 4, height - 46, Math.min(240, width / 2 - 100));
    }

    /** Recent notifications stacked upwards from {@code bottom}, right-aligned. */
    public static void toasts(GuiGraphics g, int width, int bottom, boolean right) {
        Font font = Minecraft.getInstance().font;
        List<ClientCity.Toast> list = ClientCity.toasts();
        int y = bottom;
        for (int i = list.size() - 1; i >= 0; i--) {
            ClientCity.Toast t = list.get(i);
            int maxW = Math.min(220, width / 2);
            List<FormattedCharSequence> lines = font.split(t.text(), maxW - 12);
            int h = lines.size() * 10 + 6;
            int w = 0;
            for (FormattedCharSequence l : lines) {
                w = Math.max(w, font.width(l));
            }
            w += 12;
            int x = right ? width - w - 4 : 4;
            y -= h;
            g.fill(x, y, x + w, y + h, 0xE0121417);
            g.fill(x, y, x + 2, y + h, t.color());
            for (int k = 0; k < lines.size(); k++) {
                g.drawString(font, lines.get(k), x + 7, y + 3 + k * 10, Theme.TEXT, false);
            }
            y -= 2;
        }
    }

    /** The current tutorial hint, drawn upwards from {@code bottom}. */
    public static void tutorial(GuiGraphics g, int x, int bottom, int maxW) {
        CompoundTag tut = ClientCity.snap().getCompound("tutorial");
        if (!tut.getBoolean("enabled") || tut.getString("step").isEmpty() || tut.getString("step").equals("DONE") || maxW < 80) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        Component text = Component.translatable("frostbound.tutorial." + tut.getString("step").toLowerCase(Locale.ROOT));
        List<FormattedCharSequence> lines = font.split(text, maxW - 12);
        int h = 14 + lines.size() * 10 + 4;
        int y = bottom - h;
        Theme.titled(g, x, y, x + maxW, bottom, Component.translatable("frostbound.ui.tutorial"));
        for (int k = 0; k < lines.size(); k++) {
            g.drawString(font, lines.get(k), x + 6, y + 16 + k * 10, Theme.TEXT, false);
        }
    }
}
