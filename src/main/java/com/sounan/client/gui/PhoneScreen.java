package com.sounan.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.sounan.Sounan;
import com.sounan.client.ClientPacketHandler;
import com.sounan.client.ClientState;
import com.sounan.game.GameManager;
import com.sounan.game.Notes;
import com.sounan.network.C2S;
import com.sounan.network.ModNetwork;
import com.sounan.registry.ModSounds;
import com.sounan.world.Pois;
import com.sounan.world.TerrainData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The smartphone. No signal, 31 % battery, the clock stuck somewhere after two in the morning. Home screen with the
 * summit photo, GPS without map data (until the pylon), messages, the phone (圏外です), the torch, the camera and the
 * notes you have found. The game keeps running while you look at it.
 */
public class PhoneScreen extends Screen {
    private static final ResourceLocation FRAME = Sounan.id("textures/gui/phone/frame.png");
    private static final ResourceLocation ICONS = Sounan.id("textures/gui/phone/icons.png");
    private static final ResourceLocation[] WALLPAPERS = {Sounan.id("textures/gui/phone/wallpaper_0.png"),
            Sounan.id("textures/gui/phone/wallpaper_1.png"), Sounan.id("textures/gui/phone/wallpaper_2.png")};
    private static final int FW = 160, FH = 320, SX = 8, SY = 24, SW = 144, SH = 272;
    private static final String[] APPS = {"map", "camera", "light", "messages", "call", "memo"};
    private static final RandomSource RAND = RandomSource.create();
    private static ResourceLocation mapTexture;

    private enum App { HOME, MAP, MESSAGES, THREAD, DIAL, MEMO }

    private App app = App.HOME;
    private int thread;
    private int scroll;
    private float zoom = 1.0f;
    private float scale;
    private int ox, oy;
    private int ticks;

    public PhoneScreen() {
        super(Component.translatable("phone.sounan.title"));
    }

    public PhoneScreen memo() {
        app = App.MEMO;
        return this;
    }

    @Override
    protected void init() {
        scale = Math.min(1.5f, (height - 16) / (float) FH);
        ox = (int) (width * 0.62f - FW * scale / 2);
        oy = (int) ((height - FH * scale) / 2);
        if (ticks == 0) {
            ModNetwork.sendToServer(new C2S.Action(C2S.PHONE_OPEN, 1));
            ClientPacketHandler.ui(ModSounds.PHONE_UNLOCK.get(), 0.5f, 1.0f);
        }
    }

    @Override
    public void tick() {
        ticks++;
        if (ClientState.callTicks >= 0 && ClientState.callKind != -2) {
            int t = ClientState.callTicks;
            if (ClientState.callKind > 0) {
                if (t % 120 == 1 && t < 20 * 26) ClientPacketHandler.ui(ModSounds.PHONE_CALL_STATIC.get(), 0.6f, 1.0f);
                if (t > 50 && t % 70 == 30) ClientPacketHandler.ui(ModSounds.PHONE_VOICE.get(), 0.7f, 0.9f + RAND.nextFloat() * 0.15f);
                if (t == 20 * 26) ClientPacketHandler.ui(ModSounds.PHONE_HANGUP.get(), 0.7f, 1.0f);
            }
            if (t > callLength()) ClientState.callKind = -2;
        }
    }

    private int callLength() {
        return ClientState.callKind == -1 ? 50 : 20 * 29;
    }

    @Override
    public void removed() {
        ModNetwork.sendToServer(new C2S.Action(C2S.PHONE_OPEN, 0));
    }

    private float px(double v) {
        return (float) v;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0x60000000);
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        g.pose().scale(scale, scale, 1.0f);
        // the screen (drawn first, the frame covers its edges)
        g.enableScissor(ox + (int) (SX * scale), oy + (int) (SY * scale), ox + (int) ((SX + SW) * scale), oy + (int) ((SY + SH) * scale));
        g.fill(SX, SY, SX + SW, SY + SH, 0xFF050506);
        boolean dead = ClientState.flag(GameManager.F_PHONE_DEAD);
        if (!dead) {
            g.pose().pushPose();
            g.pose().translate(SX, SY, 0);
            if (ClientState.callKind != -2 && ClientState.callTicks >= 0) drawCall(g);
            else switch (app) {
                case HOME -> drawHome(g);
                case MAP -> drawMap(g, partial);
                case MESSAGES -> drawMessages(g);
                case THREAD -> drawThread(g);
                case DIAL -> drawDial(g);
                case MEMO -> drawMemo(g);
            }
            drawStatus(g);
            g.pose().popPose();
        } else if ((ticks / 10) % 2 == 0) {
            g.fill(SX + 60, SY + 126, SX + 84, SY + 138, 0xFF2A2A2A);
            g.fill(SX + 61, SY + 127, SX + 64, SY + 137, 0xFFC03020);
        }
        g.disableScissor();
        RenderSystem.enableBlend();
        g.blit(FRAME, 0, 0, FW, FH, 0, 0, FW, FH, FW, FH);
        RenderSystem.disableBlend();
        g.pose().popPose();
        g.drawString(font, Component.translatable("phone.sounan.help"), 8, height - 14, 0x80A0A0A0, false);
        super.render(g, mouseX, mouseY, partial);
    }

    private void text(GuiGraphics g, String s, int x, int y, int color, float sc) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(sc, sc, 1.0f);
        g.drawString(font, s, 0, 0, color, false);
        g.pose().popPose();
    }

    private void textC(GuiGraphics g, String s, int cx, int y, int color, float sc) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(sc, sc, 1.0f);
        g.drawString(font, s, -font.width(s) / 2, 0, color, false);
        g.pose().popPose();
    }

    private void drawStatus(GuiGraphics g) {
        g.fill(0, 0, SW, 11, 0x70000000);
        text(g, ClientState.clock(), 5, 2, 0xFFEFEFEF, 0.75f);
        String bat = Math.round(ClientState.phone) + "%";
        text(g, bat, SW - 5 - (int) (font.width(bat) * 0.75f), 2, ClientState.phone < 15 ? 0xFFE04030 : 0xFFEFEFEF, 0.75f);
        if (ClientState.flag(GameManager.F_SIGNAL)) {
            for (int i = 0; i < 4; i++) {
                int h = 2 + i * 2;
                g.fill(SW - 46 + i * 3, 9 - h, SW - 44 + i * 3, 9, i < 1 + (ticks / 20) % 2 ? 0xFFEFEFEF : 0x50EFEFEF);
            }
        } else {
            String s = I18n.get("phone.sounan.no_signal");
            text(g, s, SW - 30 - (int) (font.width(s) * 0.7f), 2, 0xFFB0B0B0, 0.7f);
        }
    }

    // ---------------------------------------------------------------- home
    private int wallpaper() {
        int c = ClientState.captures;
        return c <= 0 ? 0 : c <= 2 ? 1 : 2;
    }

    private void drawHome(GuiGraphics g) {
        RenderSystem.enableBlend();
        g.setColor(0.75f, 0.75f, 0.78f, 1.0f);
        g.blit(WALLPAPERS[wallpaper()], 0, 0, SW, SH, 0, 0, 144, 272, 144, 272);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        g.fill(0, 26, SW, 74, 0x40000000);
        textC(g, ClientState.clock(), SW / 2, 32, 0xFFF4F4F4, 2.4f);
        int[] d = ClientState.date();
        String[] dow = {"日", "月", "火", "水", "木", "金", "土"};
        textC(g, I18n.get("phone.sounan.date", d[1], d[2], dow[d[3]]), SW / 2, 58, 0xFFE0E0E0, 0.85f);
        g.fill(6, 160, SW - 6, 262, 0x50000000);
        for (int i = 0; i < 6; i++) {
            int x = 14 + (i % 3) * 44, y = 168 + (i / 3) * 48;
            RenderSystem.enableBlend();
            g.blit(ICONS, x, y, 32, 32, i * 32, 0, 32, 32, 192, 32);
            RenderSystem.disableBlend();
            textC(g, I18n.get("phone.sounan.app." + APPS[i]), x + 16, y + 34, 0xFFE8E8E8, 0.62f);
            if (i == 3 && ClientState.unreadMessages > 0) {
                g.fill(x + 24, y - 3, x + 36, y + 7, 0xFFE03030);
                textC(g, String.valueOf(Math.min(99, ClientState.unreadMessages)), x + 30, y - 1, 0xFFFFFFFF, 0.6f);
            }
            if (i == 2 && ClientState.flag(GameManager.F_INGAME) && isTorchOn()) g.fill(x, y + 31, x + 32, y + 32, 0xFFF0E070);
        }
    }

    private boolean isTorchOn() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return com.sounan.item.GameItem.kindOf(mc.player.getMainHandItem()) == com.sounan.item.GameItem.Kind.PHONE_LIGHT
                || com.sounan.item.GameItem.kindOf(mc.player.getOffhandItem()) == com.sounan.item.GameItem.Kind.PHONE_LIGHT;
    }

    // ---------------------------------------------------------------- GPS
    private static ResourceLocation mapTexture() {
        if (mapTexture != null) return mapTexture;
        TerrainData t = TerrainData.get();
        NativeImage img = new NativeImage(t.size, t.size, false);
        for (int j = 0; j < t.size; j++) {
            for (int i = 0; i < t.size; i++) {
                int x = i + t.origin, z = j + t.origin;
                int h = t.height(x, z);
                int shade = Mth.clamp(128 + (h - t.height(x - 1, z - 1)) * 18, 40, 230);
                int area = t.area(x, z);
                int r = shade * 70 / 255, gr = shade * 92 / 255, b = shade * 74 / 255;
                if (area == TerrainData.AREA_OUT) {
                    r /= 2;
                    gr /= 2;
                    b /= 2;
                }
                int s = t.surface(x, z);
                if (s == TerrainData.SURF_TRAIL) {
                    r = 210;
                    gr = 180;
                    b = 120;
                } else if (s == TerrainData.SURF_ASPHALT || s == TerrainData.SURF_GRAVEL) {
                    r = gr = b = 170;
                }
                if (t.water(x, z) > 0) {
                    r = 60;
                    gr = 110;
                    b = 170;
                }
                img.setPixelRGBA(i, j, 0xFF000000 | (b << 16) | (gr << 8) | r);
            }
        }
        mapTexture = Minecraft.getInstance().getTextureManager().register("sounan_phone_map", new DynamicTexture(img));
        return mapTexture;
    }

    private void drawMap(GuiGraphics g, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean data = ClientState.flag(GameManager.F_MAP);
        int cx = SW / 2, cy = 14 + (SH - 14) / 2;
        double px = mc.player.getX(), pz = mc.player.getZ();
        float jitter = ClientState.dangerShown > 0.3f ? (ClientState.dangerShown - 0.3f) * 12.0f : 0.0f;
        px += (RAND.nextFloat() - 0.5f) * jitter;
        pz += (RAND.nextFloat() - 0.5f) * jitter;
        g.fill(0, 11, SW, SH, 0xFF1C1E21);
        if (data) {
            TerrainData t = TerrainData.get();
            float k = zoom;
            int size = (int) (t.size * k);
            int left = (int) (cx - (px - t.origin) * k), top = (int) (cy - (pz - t.origin) * k);
            g.blit(mapTexture(), left, top, size, size, 0, 0, t.size, t.size, t.size, t.size);
            for (Pois.Point p : Pois.get().points.values()) {
                String key = "phone.sounan.poi." + p.id();
                if (!I18n.exists(key)) continue;
                int x = (int) (cx + (p.x() - px) * k), y = (int) (cy + (p.z() - pz) * k);
                g.fill(x - 1, y - 1, x + 2, y + 2, 0xFFE8D8A0);
                text(g, I18n.get(key), x + 3, y - 3, 0xFFF0E8D0, 0.55f);
            }
        } else {
            for (int x = (int) Math.floorMod((int) (-px * zoom), 16); x < SW; x += 16) g.fill(x, 11, x + 1, SH, 0xFF26292D);
            for (int y = 11 + Math.floorMod((int) (-pz * zoom), 16); y < SH; y += 16) g.fill(0, y, SW, y + 1, 0xFF26292D);
            textC(g, I18n.get("phone.sounan.map.nodata"), cx, 22, 0xFF9AA0A8, 0.7f);
        }
        // breadcrumbs
        double[] prev = null;
        for (double[] p : ClientState.TRAIL) {
            int x = (int) (cx + (p[0] - px) * zoom), y = (int) (cy + (p[1] - pz) * zoom);
            if (prev != null) {
                int x0 = (int) (cx + (prev[0] - px) * zoom), y0 = (int) (cy + (prev[1] - pz) * zoom);
                int n = Math.max(1, Math.max(Math.abs(x - x0), Math.abs(y - y0)));
                for (int s = 0; s <= n; s += 2) {
                    int xx = x0 + (x - x0) * s / n, yy = y0 + (y - y0) * s / n;
                    g.fill(xx, yy, xx + 1, yy + 1, 0xFF4AA0F0);
                }
            }
            prev = p;
        }
        // you (heading arrow)
        float yaw = mc.player.getYRot() * Mth.DEG_TO_RAD;
        for (int r = 0; r < 6; r++) {
            int x = (int) (cx - Mth.sin(yaw) * r), y = (int) (cy + Mth.cos(yaw) * r);
            g.fill(x - 1, y - 1, x + 1, y + 1, 0xFFFFFFFF);
        }
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF2A80E0);
        // compass (spins near danger)
        float north = ClientState.dangerShown > 0.3f ? (ticks * 23 + RAND.nextInt(40)) * Mth.DEG_TO_RAD : 0.0f;
        int nx = (int) (SW - 16 + Mth.sin(north) * 8), ny = (int) (26 - Mth.cos(north) * 8);
        g.fill(SW - 17, 25, SW - 15, 27, 0xFFAAAAAA);
        text(g, "N", nx - 2, ny - 3, 0xFFE04040, 0.7f);
        String acc = data ? "GPS ±" + (6 + RAND.nextInt(3)) + "m" : "GPS ±" + (38 + RAND.nextInt(20)) + "m";
        text(g, acc, 5, SH - 10, 0xFF9AA0A8, 0.6f);
    }

    // ---------------------------------------------------------------- messages
    private void drawMessages(GuiGraphics g) {
        List<PhoneMessages.Thread> threads = PhoneMessages.threads();
        g.fill(0, 11, SW, SH, 0xFF101114);
        text(g, I18n.get("phone.sounan.app.messages"), 6, 16, 0xFFEFEFEF, 1.0f);
        int y = 32;
        for (PhoneMessages.Thread t : threads) {
            g.fill(4, y, SW - 4, y + 30, 0xFF1C1E22);
            text(g, I18n.get(t.sender()), 10, y + 4, 0xFFEFEFEF, 0.8f);
            PhoneMessages.Msg last = t.last();
            text(g, PhoneMessages.date(last.day()) + " " + last.time(), SW - 50, y + 4, 0xFF8A9098, 0.6f);
            String preview = I18n.get("phone.sounan.msg." + last.key());
            text(g, font.plainSubstrByWidth(preview, (int) ((SW - 20) / 0.7f)), 10, y + 17, 0xFFB0B4BA, 0.7f);
            y += 34;
        }
    }

    private void drawThread(GuiGraphics g) {
        List<PhoneMessages.Thread> threads = PhoneMessages.threads();
        if (thread >= threads.size()) {
            app = App.MESSAGES;
            return;
        }
        PhoneMessages.Thread t = threads.get(thread);
        g.fill(0, 11, SW, SH, 0xFF0C0D10);
        g.fill(0, 11, SW, 28, 0xFF1C1E22);
        textC(g, I18n.get(t.sender()), SW / 2, 16, 0xFFEFEFEF, 0.85f);
        int y = 34 - scroll;
        int lastDay = Integer.MIN_VALUE;
        for (PhoneMessages.Msg m : t.msgs()) {
            if (m.day() != lastDay) {
                textC(g, PhoneMessages.date(m.day()), SW / 2, y, 0xFF6A7078, 0.6f);
                y += 9;
                lastDay = m.day();
            }
            String body = I18n.get("phone.sounan.msg." + m.key(), ClientState.introName);
            List<FormattedCharSequence> lines = font.split(Component.literal(body), (int) (96 / 0.72f));
            int bh = lines.size() * 8 + 6;
            int bx = m.mine() ? SW - 104 : 6;
            g.fill(bx, y, bx + 98, y + bh, m.mine() ? 0xFF2F7D46 : 0xFF2A2C31);
            int ly = y + 3;
            for (FormattedCharSequence l : lines) {
                g.pose().pushPose();
                g.pose().translate(bx + 4, ly, 0);
                g.pose().scale(0.72f, 0.72f, 1.0f);
                g.drawString(font, l, 0, 0, 0xFFEFEFEF, false);
                g.pose().popPose();
                ly += 8;
            }
            text(g, m.time(), m.mine() ? bx - 22 : bx + 100, y + bh - 7, 0xFF6A7078, 0.55f);
            y += bh + 5;
        }
    }

    // ---------------------------------------------------------------- phone
    private void drawDial(GuiGraphics g) {
        g.fill(0, 11, SW, SH, 0xFF101114);
        text(g, I18n.get("phone.sounan.app.call"), 6, 16, 0xFFEFEFEF, 1.0f);
        String[] labels = {"110", "119", I18n.get("phone.sounan.who.mom")};
        for (int i = 0; i < 3; i++) {
            int y = 40 + i * 40;
            g.fill(14, y, SW - 14, y + 30, 0xFF1F3A26);
            textC(g, labels[i], SW / 2, y + 10, 0xFFEFEFEF, i < 2 ? 1.5f : 1.0f);
        }
        textC(g, I18n.get("phone.sounan.call.hint"), SW / 2, 170, 0xFF7A8088, 0.6f);
    }

    private void drawCall(GuiGraphics g) {
        int t = ClientState.callTicks;
        g.fill(0, 0, SW, SH, 0xFF0A0B0D);
        if (ClientState.callKind == -1) {
            textC(g, I18n.get("phone.sounan.call.no_signal"), SW / 2, SH / 2 - 10, 0xFFE0E0E0, 1.0f);
            return;
        }
        String who = ClientState.callKind == 1 ? I18n.get("phone.sounan.who.mom") : String.valueOf(ClientState.callKind);
        textC(g, who, SW / 2, 40, 0xFFF0F0F0, 1.6f);
        String target = ClientState.callKind == 1 ? "mom" : String.valueOf(ClientState.callKind);
        if (t < 60) {
            textC(g, I18n.get("phone.sounan.call.ringing") + ".".repeat((t / 10) % 4), SW / 2, 64, 0xFF9AA0A8, 0.8f);
        } else {
            int sec = (t - 60) / 20;
            textC(g, String.format("%02d:%02d", sec / 60, sec % 60), SW / 2, 64, 0xFF9AA0A8, 0.8f);
            // the script, one line after another
            int line = Math.min(8, (t - 60) / 60 + 1);
            int y = 90;
            for (int i = 1; i <= line; i++) {
                String key = "call.sounan." + target + "." + i;
                if (!I18n.exists(key)) break;
                List<FormattedCharSequence> lines = font.split(Component.literal(I18n.get(key, ClientState.introName)), (int) ((SW - 16) / 0.72f));
                for (FormattedCharSequence l : lines) {
                    g.pose().pushPose();
                    g.pose().translate(8, y, 0);
                    g.pose().scale(0.72f, 0.72f, 1.0f);
                    g.drawString(font, l, 0, 0, i == line ? 0xFFE8E8E8 : 0xFF8A8E94, false);
                    g.pose().popPose();
                    y += 9;
                }
                y += 4;
            }
        }
        g.fill(SW / 2 - 14, SH - 40, SW / 2 + 14, SH - 20, 0xFFC03030);
        textC(g, I18n.get("phone.sounan.call.end"), SW / 2, SH - 34, 0xFFFFFFFF, 0.7f);
    }

    // ---------------------------------------------------------------- notes
    private void drawMemo(GuiGraphics g) {
        g.fill(0, 11, SW, SH, 0xFF15130F);
        text(g, I18n.get("phone.sounan.app.memo"), 6, 16, 0xFFEFEFEF, 1.0f);
        int y = 32;
        int count = 0;
        for (int id = 1; id <= Notes.COUNT; id++) {
            if ((ClientState.notesRead & (1 << id)) == 0) continue;
            g.fill(4, y, SW - 4, y + 16, 0xFF24211B);
            text(g, font.plainSubstrByWidth(I18n.get("note.sounan." + id + ".title"), (int) ((SW - 16) / 0.75f)), 8, y + 5, 0xFFE0D8C8, 0.75f);
            y += 19;
            count++;
        }
        if (count == 0) textC(g, I18n.get("phone.sounan.memo.empty"), SW / 2, 60, 0xFF8A8478, 0.7f);
    }

    // ---------------------------------------------------------------- input
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double lx = (mouseX - ox) / scale - SX, ly = (mouseY - oy) / scale - SY;
        if (button == 1) {
            back();
            return true;
        }
        if (ClientState.flag(GameManager.F_PHONE_DEAD)) return true;
        if (ClientState.callKind != -2 && ClientState.callTicks >= 0) {
            if (lx > SW / 2.0 - 16 && lx < SW / 2.0 + 16 && ly > SH - 42 && ly < SH - 18) ClientState.callKind = -2;
            return true;
        }
        tap();
        switch (app) {
            case HOME -> {
                for (int i = 0; i < 6; i++) {
                    int x = 14 + (i % 3) * 44, y = 168 + (i / 3) * 48;
                    if (lx >= x && lx < x + 32 && ly >= y && ly < y + 40) openApp(i);
                }
            }
            case MESSAGES -> {
                int idx = (int) ((ly - 32) / 34);
                if (ly >= 32 && idx >= 0 && idx < PhoneMessages.threads().size()) {
                    thread = idx;
                    scroll = 0;
                    app = App.THREAD;
                    ClientState.unreadMessages = 0;
                }
            }
            case DIAL -> {
                for (int i = 0; i < 3; i++) {
                    int y = 40 + i * 40;
                    if (lx >= 14 && lx < SW - 14 && ly >= y && ly < y + 30) {
                        int target = i == 0 ? 110 : i == 1 ? 119 : 1;
                        ModNetwork.sendToServer(new C2S.Action(C2S.PHONE_CALL, target));
                    }
                }
            }
            case MEMO -> {
                int y = 32;
                for (int id = 1; id <= Notes.COUNT; id++) {
                    if ((ClientState.notesRead & (1 << id)) == 0) continue;
                    if (ly >= y && ly < y + 16) {
                        Minecraft.getInstance().setScreen(new NoteScreen(id, ClientState.introName + "|" + ClientState.captures + "|"
                                + ClientState.days));
                        return true;
                    }
                    y += 19;
                }
            }
            default -> {
            }
        }
        return true;
    }

    private void openApp(int i) {
        switch (APPS[i]) {
            case "map" -> app = App.MAP;
            case "camera" -> {
                ModNetwork.sendToServer(new C2S.Action(C2S.PHONE_CAMERA, 0));
                onClose();
            }
            case "light" -> ModNetwork.sendToServer(new C2S.Action(C2S.PHONE_TORCH, 0));
            case "messages" -> app = App.MESSAGES;
            case "call" -> app = App.DIAL;
            case "memo" -> app = App.MEMO;
            default -> {
            }
        }
    }

    private void tap() {
        ClientPacketHandler.ui(ModSounds.PHONE_TAP.get(), 0.35f, 1.0f + RAND.nextFloat() * 0.1f);
    }

    private void back() {
        if (app == App.THREAD) app = App.MESSAGES;
        else if (app != App.HOME) app = App.HOME;
        else onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (app == App.MAP) zoom = Mth.clamp(zoom * (delta > 0 ? 1.25f : 0.8f), 0.4f, 3.0f);
        else if (app == App.THREAD) scroll = Math.max(0, scroll - (int) (delta * 12));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            back();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
