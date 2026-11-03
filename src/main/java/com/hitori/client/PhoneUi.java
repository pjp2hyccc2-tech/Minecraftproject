package com.hitori.client;

import com.hitori.game.Clock;
import com.hitori.game.GameManager;
import com.hitori.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;

/**
 * The phone on screen (docs/DESIGN.md §3.6): message banners from a friend, the incoming call from your own number,
 * the little lock screen while you hold it (the time, the battery, the silent switch), and in the 外へ ending the map
 * that puts you inside 203.
 */
public final class PhoneUi {
    public static final List<String> MESSAGES = new ArrayList<>();
    private static String banner = "";
    private static int bannerTicks = -1;
    /** 0 none, 1 ringing, 2 on the line. */
    public static int callState;
    private static int callTicks;
    private static String callEnd = "";
    private static int callEndTicks = -1;

    private PhoneUi() {}

    public static void message(String key, boolean silent) {
        MESSAGES.add(key);
        banner = key;
        bannerTicks = 0;
        if (!silent) ClientPacketHandler.ui(ModSounds.PHONE_NOTIFY.get(), 0.5f, 1.0f);
    }

    public static void call(int state, String text) {
        if (state == 0) {
            callState = 0;
            callEnd = text;
            callEndTicks = 0;
            if (text.equals("missed")) {
                banner = "msg.hitori.missed";
                bannerTicks = 0;
                MESSAGES.add("msg.hitori.missed");
            }
        } else {
            callState = state;
            callTicks = 0;
        }
    }

    public static void tick() {
        if (bannerTicks >= 0 && ++bannerTicks > 140) bannerTicks = -1;
        if (callState > 0) callTicks++;
        if (callEndTicks >= 0 && ++callEndTicks > 60) callEndTicks = -1;
    }

    public static boolean holdingPhone(Minecraft mc) {
        return mc.player != null && Peek.heldScreen(mc) == 1;
    }

    public static void render(GuiGraphics g, Font font, int w, int h, float partial) {
        Minecraft mc = Minecraft.getInstance();
        boolean silent = ClientState.flag(GameManager.F_SILENT);
        // the banner: a friend's message
        if (bannerTicks >= 0 && !banner.isEmpty()) {
            float k = bannerTicks + partial;
            float a = k < 8 ? k / 8.0f : k > 120 ? Math.max(0.0f, (140 - k) / 20.0f) : 1.0f;
            String from = I18n.get(banner.equals("msg.hitori.missed") ? "phone.hitori.missed_from" : "phone.hitori.friend");
            String text = I18n.get(banner);
            int bw = Math.max(font.width(text), font.width(from)) + 30;
            int x = w / 2 - bw / 2, y = 6;
            g.fill(x, y, x + bw, y + 26, HorrorOverlay.argb(a * 0.82f, 0x1A1C22));
            g.fill(x, y, x + 3, y + 26, HorrorOverlay.argb(a, 0x3FB950));
            g.drawString(font, from, x + 10, y + 4, HorrorOverlay.argb(a * 0.8f, 0xA8B0BC), false);
            g.drawString(font, text, x + 10, y + 15, HorrorOverlay.argb(a, 0xF0F0F0), false);
        }
        // the call from your own number
        if (callState > 0) {
            float k = callTicks + partial;
            int bw = 190, bh = 54;
            int x = w / 2 - bw / 2, y = h / 2 - bh - 30;
            boolean pulse = callState == 1 && ((int) (k / 10)) % 2 == 0;
            g.fill(x, y, x + bw, y + bh, HorrorOverlay.argb(0.85f, 0x101216));
            g.fill(x, y, x + bw, y + 2, HorrorOverlay.argb(1.0f, pulse ? 0x3FB950 : 0x2A7A3A));
            String title = I18n.get(callState == 1 ? "phone.hitori.incoming" : "phone.hitori.on_call");
            HorrorOverlay.centered(g, font, title, w / 2, y + 7, 0xFFC8D0D8, 1.0f);
            HorrorOverlay.centered(g, font, I18n.get("phone.hitori.own_number"), w / 2, y + 20, 0xFFF0F0F0, 1.2f);
            String sub = callState == 1 ? I18n.get("phone.hitori.answer_hint")
                    : String.format(java.util.Locale.ROOT, "%02d:%02d", (int) (k / 20) / 60, (int) (k / 20) % 60);
            HorrorOverlay.centered(g, font, sub, w / 2, y + 38, 0xFF8890A0, 1.0f);
        }
        if (callEndTicks >= 0 && !callEnd.isEmpty() && !callEnd.equals("missed")) {
            HorrorOverlay.centered(g, font, I18n.get("phone.hitori.call_" + callEnd), w / 2, h / 2 - 60, HorrorOverlay.argb(0.8f, 0xC0C0C0), 1.0f);
        }
        // holding the phone: its lock screen in the corner
        if (holdingPhone(mc) && !CameraApp.isOpen()) {
            int pw = 74, ph = 112;
            int x = w - pw - 10, y = h - ph - 30;
            g.fill(x - 3, y - 3, x + pw + 3, y + ph + 3, 0xF0101114);
            g.fill(x, y, x + pw, y + ph, ClientState.flag(GameManager.F_LIGHT) ? 0xFF2A3038 : 0xFF141820);
            String time = Clock.hhmm(ClientState.clock);
            HorrorOverlay.centered(g, font, time, x + pw / 2, y + 16, 0xFFE8EEF4, 2.0f);
            HorrorOverlay.centered(g, font, I18n.get("phone.hitori.date"), x + pw / 2, y + 38, 0xFF9AA4B0, 0.75f);
            int ly = y + 52;
            for (int i = Math.max(0, MESSAGES.size() - 3); i < MESSAGES.size(); i++) {
                String m = I18n.get(MESSAGES.get(i));
                if (font.width(m) * 0.7f > pw - 8) m = font.plainSubstrByWidth(m, (int) ((pw - 14) / 0.7f)) + "…";
                g.pose().pushPose();
                g.pose().translate(x + 4, ly, 0);
                g.pose().scale(0.7f, 0.7f, 1.0f);
                g.drawString(font, m, 0, 0, 0xFFD0D6DE, false);
                g.pose().popPose();
                ly += 9;
            }
            g.drawString(font, silent ? "🔇" : "", x + 4, y + 4, 0xFFE0E0E0, false);
            String s = I18n.get(silent ? "phone.hitori.silent_on" : "phone.hitori.silent_off");
            g.pose().pushPose();
            g.pose().translate(x + 4, y + ph - 20, 0);
            g.pose().scale(0.7f, 0.7f, 1.0f);
            g.drawString(font, s, 0, 0, silent ? 0xFFE0B040 : 0xFF7A8490, false);
            g.drawString(font, I18n.get("phone.hitori.screen_hint"), 0, 11, 0xFF7A8490, false);
            g.pose().popPose();
        }
    }

    public static void reset() {
        MESSAGES.clear();
        bannerTicks = -1;
        callState = 0;
        callEndTicks = -1;
    }
}
