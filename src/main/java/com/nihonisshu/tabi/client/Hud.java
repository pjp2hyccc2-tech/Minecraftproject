package com.nihonisshu.tabi.client;

import com.nihonisshu.tabi.data.Route;
import com.nihonisshu.tabi.entity.VehicleEntity;
import com.nihonisshu.tabi.game.Areas;
import com.nihonisshu.tabi.registry.TabiWorldgen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** Money + clock + place name, and the in-car passenger information display while riding a train or bus. */
public final class Hud {
    private Hud() {}

    private static int ticks;
    private static String area = "";
    private static String guide = "";
    private static int guideUntil;

    public static void showGuide(String text) {
        guide = text;
        guideUntil = ticks + 200;
    }

    public static void tick(Minecraft mc) {
        ticks++;
        if (mc.player == null || ticks % 10 != 0) return;
        if (mc.player.level().dimension() != TabiWorldgen.JAPAN) {
            area = "";
            return;
        }
        Areas.Area a = Areas.at(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        area = a == null ? "" : a.name();
    }

    static String clock(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        int hour = (int) ((t / 1000 + 6) % 24);
        int min = (int) ((t % 1000) * 60 / 1000);
        return (hour < 12 ? "午前 " : "午後 ") + (hour % 12 == 0 ? 12 : hour % 12) + ":" + String.format("%02d", min);
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null) return;
        if (mc.player.level().dimension() != TabiWorldgen.JAPAN) return;
        if (mc.options.renderDebug) return;
        Font f = mc.font;
        String money = String.format("%,d", ClientState.yen()) + "円";
        String time = clock(mc.level.getDayTime());
        int bw = Math.max(f.width(money), f.width(time)) + 14;
        if (!area.isEmpty()) bw = Math.max(bw, f.width(area) + 14);
        int bh = area.isEmpty() ? 26 : 37;
        g.fill(4, 4, 4 + bw, 4 + bh, 0x7A000000);
        g.fill(4, 4, 6, 4 + bh, 0xFFE0B040);
        g.drawString(f, money, 10, 8, 0xFFFFE9A0, false);
        g.drawString(f, time, 10, 18, 0xFFE0E0E0, false);
        if (!area.isEmpty()) g.drawString(f, area, 10, 29, 0xFFA8D8FF, false);

        if (mc.player.getVehicle() instanceof VehicleEntity v) renderDisplay(g, f, v, w);
        if (!guide.isEmpty() && ticks < guideUntil) renderGuide(g, f, w, h);
    }

    /** tourist-train style guide card at the bottom centre */
    private static void renderGuide(GuiGraphics g, Font f, int w, int h) {
        int left = guideUntil - ticks;
        float a = Math.min(1f, Math.min(left / 20f, (200 - left) / 10f));
        int alpha = (int) (a * 220);
        if (alpha < 8) return;
        java.util.List<net.minecraft.util.FormattedCharSequence> lines = f.split(net.minecraft.network.chat.Component.literal(guide), 300);
        int bw = 24;
        for (var l : lines) bw = Math.max(bw, f.width(l) + 24);
        int bh = 18 + lines.size() * 11;
        int x = (w - bw) / 2, y = h - 92 - bh;
        g.fill(x, y, x + bw, y + bh, (alpha << 24) | 0x10161E);
        g.fill(x, y, x + 3, y + bh, (alpha << 24) | 0xE0A030);
        g.drawString(f, "車窓ガイド", x + 10, y + 4, (alpha << 24) | 0xE0B050, false);
        int yy = y + 15;
        for (var l : lines) {
            g.drawString(f, l, x + 10, yy, (alpha << 24) | 0xFFFFFF, false);
            yy += 11;
        }
    }

    private static void renderDisplay(GuiGraphics g, Font f, VehicleEntity v, int w) {
        Route r = v.route();
        if (r == null) return;
        boolean bus = "bus".equals(r.type);
        int pw = 200, ph = 62;
        int x = w - pw - 6, y = 6;
        int color = 0xFF000000 | r.color;
        g.fill(x - 2, y - 2, x + pw + 2, y + ph + 2, 0xFF2A2E33);
        g.fill(x, y, x + pw, y + ph, 0xFF0B0D10);
        g.fill(x, y, x + pw, y + 12, color);
        String head = r.name;
        String toward = v.getDir() > 0 ? r.towardEnd : r.towardStart;
        if (!toward.isEmpty()) head += "  " + toward;
        g.drawString(f, head, x + 4, y + 2, 0xFFFFFFFF, false);

        Route.Station st;
        String label;
        boolean stopped = v.getState() == VehicleEntity.STOPPED;
        if (stopped) {
            st = v.currentStation();
            label = bus ? "停留所" : "ただいま";
        } else {
            st = r.nextStation(v.getS(), v.getDir());
            label = "つぎは";
        }
        if (st != null) {
            int phase = (ticks / 70) % 3;
            String big = phase == 0 ? st.name : phase == 1 ? (st.kana.isEmpty() ? st.name : st.kana) : (st.en.isEmpty() ? st.name : st.en);
            String small = phase == 2 ? (stopped ? "Now stopping at" : "Next") : label;
            g.drawString(f, small, x + 6, y + 17, 0xFFFFB347, false);
            g.pose().pushPose();
            g.pose().translate(x + 6, y + 28, 0);
            g.pose().scale(1.6f, 1.6f, 1f);
            g.drawString(f, big, 0, 0, 0xFFFFFFFF, false);
            g.pose().popPose();
            if (stopped) {
                String d = (ticks / 20) % 2 == 0 ? "ドアが開いています" : "Doors open";
                g.drawString(f, d, x + pw - 6 - f.width(d), y + 17, 0xFF7CFC9A, false);
            }
        }
        // progress strip with station marks
        int sx0 = x + 6, sx1 = x + pw - 6, sy = y + ph - 8;
        g.fill(sx0, sy, sx1, sy + 2, color);
        if (r.length > 0) {
            for (Route.Station s : r.stations) {
                int px = sx0 + (int) ((sx1 - sx0) * (s.s / r.length));
                g.fill(px - 1, sy - 2, px + 2, sy + 4, 0xFFFFFFFF);
            }
            int tx = sx0 + (int) ((sx1 - sx0) * Math.max(0, Math.min(1, v.getS() / r.length)));
            g.fill(tx - 2, sy - 3, tx + 3, sy + 5, 0xFFFFC233);
        }
        if (!stopped) {
            int kmh = Math.round(v.getSpeed() * 20f * 3.6f);
            String sp = kmh + " km/h";
            g.drawString(f, sp, x + pw - 6 - f.width(sp), y + 17, 0xFF9FB0BF, false);
        }
    }
}
