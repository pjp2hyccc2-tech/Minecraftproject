package com.hitori.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.hitori.Hitori;
import com.hitori.client.ClientPacketHandler;
import com.hitori.client.FrameCapture;
import com.hitori.client.HorrorAudio;
import com.hitori.client.HorrorOverlay;
import com.hitori.client.Tex;
import com.hitori.game.Clock;
import com.hitori.game.GameData;
import com.hitori.network.C2S;
import com.hitori.network.ModNetwork;
import com.hitori.registry.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * The four endings (docs/DESIGN.md §1). 終了: morning; the text by how many rules you broke (none: 「完璧な終わり」);
 * the next afternoon, the cameras' recordings played back on the laptop — every feed, fast — and CAM 3's last seconds,
 * after the doll burned: the closet's fusuma sliding open by itself. 鬼: 「つぎは、あなたがおに」; the cameras keep
 * recording… and you are standing in the bathtub. 外へ: the corridor that goes on forever. 未完: the last feed, the doll
 * on the balcony looking in. Then the title and the credits.
 */
public class EndingScreen extends Screen {
    private static final ResourceLocation LOGO = Hitori.id("textures/gui/title_logo.png");
    private static final ResourceLocation LAPTOP = Hitori.id("textures/gui/laptop_frame.png");
    private static final RandomSource RAND = RandomSource.create();
    private static final int CREDITS = 14;
    private final int kind;
    private final int broken;
    private int ticks;
    private boolean sent;
    public boolean localOnly;
    /** The playback's frames (good ending): ids in order, their camera, their clock. */
    private final List<FrameCapture.Frame> play = new ArrayList<>();
    private int playStart, finalStart, cardAt;

    public EndingScreen(int kind, String extra) {
        super(Component.translatable("ending.hitori.title"));
        this.kind = kind;
        int n = 0;
        try {
            n = Integer.parseInt(extra.trim());
        } catch (Exception ignored) {
        }
        this.broken = Math.max(0, n);
        if (kind == GameData.ENDING_GOOD) {
            List<FrameCapture.Frame> all = new ArrayList<>(FrameCapture.frames());
            all.sort((a, b) -> a.camera() != b.camera() ? Integer.compare(a.camera(), b.camera()) : Float.compare(a.clock(), b.clock()));
            // at most 48, evenly picked, the tagged ones always kept
            int keep = 48;
            if (all.size() > keep) {
                List<FrameCapture.Frame> picked = new ArrayList<>();
                double step = all.size() / (double) keep;
                for (int i = 0; i < all.size(); i++) {
                    FrameCapture.Frame f = all.get(i);
                    if (!f.label().isEmpty() || Math.floor(i / step) != Math.floor((i - 1) / step)) picked.add(f);
                }
                all = picked;
            }
            play.addAll(all);
            playStart = 470;
            finalStart = playStart + 40 + play.size() * 9;
            cardAt = finalStart + Math.max(1, FrameCapture.finals("fusuma").size()) * 40 + 90;
        } else if (kind == GameData.ENDING_ONI) {
            cardAt = 380;
        } else if (kind == GameData.ENDING_MIKAN) {
            cardAt = 400;
        } else {
            cardAt = 300;
        }
    }

    public void setTicks(int t) {
        ticks = t;
    }

    public int cardAt() {
        return cardAt;
    }

    public int playStart() {
        return playStart;
    }

    public int finalStart() {
        return finalStart;
    }

    private int creditsAt() {
        return cardAt + 150;
    }

    private int endAt() {
        return creditsAt() + CREDITS * 36 + 160;
    }

    @Override
    public void tick() {
        ticks++;
        if (ticks == 1) {
            HorrorAudio.stopAll();
            ClientPacketHandler.ui(kind == GameData.ENDING_GOOD ? ModSounds.MUSIC_ENDING_GOOD.get() : ModSounds.MUSIC_ENDING_BAD.get(), 0.85f, 1.0f);
        }
        if (kind == GameData.ENDING_GOOD && ticks == playStart) ClientPacketHandler.ui(ModSounds.MUSIC_PLAYBACK.get(), 0.8f, 1.0f);
        if (kind == GameData.ENDING_GOOD && ticks == finalStart + 60) ClientPacketHandler.ui(ModSounds.DOOR_FUSUMA.get(), 0.6f, 0.9f);
        if (kind == GameData.ENDING_GOOD && ticks == cardAt - 40) ClientPacketHandler.ui(ModSounds.CAMERA_GLITCH.get(), 0.8f, 0.8f);
        if (kind == GameData.ENDING_ONI && ticks == 20) ClientPacketHandler.ui(ModSounds.DOLL_VOICE_TSUGI.get(), 0.9f, 1.0f);
        if (ticks == cardAt) ClientPacketHandler.ui(ModSounds.GAME_TITLE_HIT.get(), 0.9f, 1.0f);
    }

    private void line(GuiGraphics g, String text, int start, int length, int y, int color, float scale) {
        int t = ticks - start;
        if (t < 0 || t > length) return;
        float a = t < 20 ? t / 20.0f : t > length - 20 ? (length - t) / 20.0f : 1.0f;
        int lw = (int) ((width - 60) / scale);
        var lines = font.split(Component.literal(text), lw);
        int yy = y - (int) (lines.size() * 6 * scale);
        for (var l : lines) {
            g.pose().pushPose();
            g.pose().translate(width / 2.0f, yy, 0);
            g.pose().scale(scale, scale, 1.0f);
            g.drawString(font, l, -font.width(l) / 2, 0, HorrorOverlay.argb(a, color), false);
            g.pose().popPose();
            yy += (int) (12 * scale);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        float t = ticks + partial;
        int cy = height / 2 - 4;
        switch (kind) {
            case GameData.ENDING_GOOD -> good(g, t, cy);
            case GameData.ENDING_ONI -> oni(g, t, cy);
            case GameData.ENDING_SOTO -> soto(g, t, cy);
            default -> mikan(g, t, cy);
        }
        // the title card
        int lt = ticks - cardAt;
        if (lt >= 0 && ticks < creditsAt() + 20) {
            float a = Math.min(1.0f, lt / 30.0f) * Mth.clamp((creditsAt() + 20 - t) / 20.0f, 0.0f, 1.0f);
            int lw = Math.min(width - 40, 300), lh = lw / 2;
            if (Tex.has(LOGO)) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                g.setColor(1.0f, 1.0f, 1.0f, a);
                g.blit(LOGO, (width - lw) / 2, height / 2 - lh + 6, lw, lh, 0, 0, 512, 256, 512, 256);
                g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
                RenderSystem.disableBlend();
            } else {
                HorrorOverlay.centered(g, font, "ひとりかくれんぼ", width / 2, height / 2 - 40, HorrorOverlay.argb(a, 0xE8E0D8), 3.0f);
                HorrorOverlay.centered(g, font, "― HITORI KAKURENBO ―", width / 2, height / 2 - 8, HorrorOverlay.argb(a * 0.8f, 0xB01818), 1.2f);
            }
            String end = I18n.get("ending.hitori.end_" + key());
            g.drawCenteredString(font, end, width / 2, height / 2 + 22, HorrorOverlay.argb(a, 0xC8C0B4));
        }
        int ct = ticks - creditsAt();
        if (ct >= 0) {
            float scrollY = height + 10 - (ct + partial) * 0.55f;
            for (int i = 1; i <= CREDITS; i++) {
                String key = "credits.hitori." + i;
                if (!I18n.exists(key)) continue;
                float y = scrollY + (i - 1) * 20;
                if (y < -10 || y > height + 10) continue;
                float a = Mth.clamp(Math.min((y + 10) / 40.0f, (height - y) / 40.0f), 0.0f, 1.0f);
                g.drawCenteredString(font, I18n.get(key), width / 2, (int) y, HorrorOverlay.argb(a, 0xC8C8C8));
            }
        }
        if (ticks > cardAt + 60) g.drawCenteredString(font, I18n.get("ending.hitori.continue"), width / 2, height - 20, HorrorOverlay.argb(0.6f, 0xA0A0A0));
        super.render(g, mouseX, mouseY, partial);
    }

    private String key() {
        return switch (kind) {
            case GameData.ENDING_GOOD -> "good";
            case GameData.ENDING_ONI -> "oni";
            case GameData.ENDING_SOTO -> "soto";
            default -> "mikan";
        };
    }

    // ------------------------------------------------------------------------------------------------ 終了
    private void good(GuiGraphics g, float t, int cy) {
        if (ticks < playStart) {
            float dawn = Mth.clamp(t / 300.0f, 0.0f, 1.0f);
            int top = lerp(0xF4F0E8, 0xB8C4D0, dawn), bottom = lerp(0xFFFFFF, 0xE8D0B0, dawn);
            g.fillGradient(0, 0, width, height, 0xFF000000 | top, 0xFF000000 | bottom);
            int col = 0x2A2420;
            line(g, I18n.get("ending.hitori.good.1"), 20, 90, cy, col, 1.0f);
            line(g, I18n.get("ending.hitori.good.2"), 110, 90, cy, col, 1.0f);
            line(g, I18n.get("ending.hitori.good.3"), 200, 90, cy, col, 1.0f);
            String rk = "ending.hitori.good.rules." + Math.min(3, broken);
            line(g, I18n.get(rk), 290, 110, cy, broken == 0 ? 0x8A5A10 : col, broken == 0 ? 1.4f : 1.0f);
            if (ticks > 400) {
                HorrorOverlay.under(g);
                g.fill(0, 0, width, height, HorrorOverlay.argb(Mth.clamp((t - 400) / 30.0f, 0.0f, 1.0f), 0x000000));
                line(g, I18n.get("ending.hitori.good.afternoon"), 410, 60, cy, 0xC8C0B4, 1.2f);
            }
            return;
        }
        // the playback on the laptop, the next afternoon
        g.fill(0, 0, width, height, 0xFF0A0A0C);
        int vx, vy, vw, vh;
        if (Tex.has(LAPTOP)) {
            // the laptop frame's screen: the same rect as the camera app's feed
            vx = width / 22;
            vy = height / 14;
            vw = width - width / 22 - vx;
            vh = height - height / 9 - vy;
        } else {
            int bx = width / 14, by = height / 12;
            int fw = width - 2 * bx, fh = height - 2 * by - 10;
            vw = Math.min(fw, fh * 16 / 9);
            vh = vw * 9 / 16;
            vx = bx + (fw - vw) / 2;
            vy = by + (fh - vh) / 2;
        }
        g.fill(vx, vy, vx + vw, vy + vh, 0xFF000000);
        int pt = ticks - playStart;
        String id = null;
        int cam = 0;
        float clock = 0;
        boolean fin = ticks >= finalStart;
        List<String> finals = FrameCapture.finals("fusuma");
        if (!fin) {
            int i = Math.max(0, pt - 40) / 9;
            if (pt >= 40 && i < play.size()) {
                FrameCapture.Frame f = play.get(i);
                id = f.id();
                cam = f.camera();
                clock = f.clock();
            }
        } else if (!finals.isEmpty()) {
            int i = Math.min(finals.size() - 1, (ticks - finalStart) / 40);
            id = finals.get(i);
            cam = 3;
            clock = (float) (Clock.DAWN - 600 + i * 2);
        }
        ResourceLocation tex = id == null ? null : FrameCapture.texture(id);
        if (tex != null) {
            // fills the screen, cropped to its shape
            float sc = Math.max(vw / (float) FrameCapture.W, vh / (float) FrameCapture.H);
            int uw = Math.min(FrameCapture.W, Math.round(vw / sc)), uh = Math.min(FrameCapture.H, Math.round(vh / sc));
            RenderSystem.enableBlend();
            g.blit(tex, vx, vy, vw, vh, (FrameCapture.W - uw) / 2.0f, (FrameCapture.H - uh) / 2.0f, uw, uh, FrameCapture.W, FrameCapture.H);
            RenderSystem.disableBlend();
        } else if (pt >= 40) {
            HorrorOverlay.grainIn(g, vx, vy, vx + vw, vy + vh, 0.5f);
            if (!fin && play.isEmpty()) HorrorOverlay.centered(g, font, I18n.get("ending.hitori.no_footage"), width / 2, height / 2, 0xFFA0A0A0, 1.0f);
        }
        // the feed's overlay
        if (pt >= 40) {
            HorrorOverlay.grainIn(g, vx, vy, vx + vw, vy + vh, 0.12f);
            for (int y = vy; y < vy + vh; y += 3) g.fill(vx, y, vx + vw, y + 1, 0x14000000);
            g.drawString(font, "▶ PLAY", vx + 8, vy + 8, 0xFFF0F0F0, true);
            g.drawString(font, fin ? "×1" : "×8  ▶▶", vx + 8, vy + 20, 0xFFC0C0C0, true);
            if (cam > 0) g.drawString(font, "CAM" + cam, vx + vw - 8 - font.width("CAM" + cam), vy + 8, 0xFFF0F0F0, true);
            String stamp = Clock.stamp(clock);
            g.drawString(font, stamp, vx + vw - 8 - font.width(stamp), vy + vh - 16, 0xFFF0F0F0, true);
            if (fin && ticks > cardAt - 45) {
                // the last frame: the picture tears and goes
                for (int i = 0; i < 6; i++) {
                    int y = vy + RAND.nextInt(Math.max(1, vh));
                    g.fill(vx, y, vx + vw, y + 1 + RAND.nextInt(6), RAND.nextBoolean() ? 0xC0000000 : 0x60FFFFFF);
                }
                if (ticks > cardAt - 20) g.fill(vx, vy, vx + vw, vy + vh, 0xFF000000);
            }
        }
        // the laptop around it
        if (Tex.has(LAPTOP)) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.blit(LAPTOP, 0, 0, width, height, 0, 0, 256, 160, 256, 160);
            RenderSystem.disableBlend();
        } else {
            g.fill(0, 0, width, vy - 2, 0xFF16171A);
            g.fill(0, vy + vh + 2, width, height, 0xFF16171A);
            g.fill(0, vy - 2, vx - 2, vy + vh + 2, 0xFF16171A);
            g.fill(vx + vw + 2, vy - 2, width, vy + vh + 2, 0xFF16171A);
        }
        if (pt < 40) HorrorOverlay.centered(g, font, I18n.get("ending.hitori.good.playback"), width / 2, height / 2, HorrorOverlay.argb(Mth.clamp(pt / 15.0f, 0, 1), 0xD0D0D0), 1.0f);
        if (ticks >= cardAt) {
            HorrorOverlay.under(g);
            g.fill(0, 0, width, height, 0xFF000000);
        }
    }

    // ------------------------------------------------------------------------------------------------ 鬼
    private void oni(GuiGraphics g, float t, int cy) {
        g.fill(0, 0, width, height, 0xFF000000);
        line(g, I18n.get("ending.hitori.oni.1"), 10, 110, cy, 0xC01818, 2.0f);
        if (ticks > 130 && ticks < cardAt) {
            List<String> frames = FrameCapture.finals("you_tub");
            int vw = Math.min(width - 80, (height - 60) * 16 / 9), vh = vw * 9 / 16;
            int vx = (width - vw) / 2, vy = (height - vh) / 2;
            float a = Mth.clamp((t - 130) / 30.0f, 0.0f, 1.0f);
            if (!frames.isEmpty()) {
                int i = Math.min(frames.size() - 1, (ticks - 130) / 50);
                ResourceLocation tex = FrameCapture.texture(frames.get(i));
                if (tex != null) {
                    RenderSystem.enableBlend();
                    g.setColor(a, a, a, 1.0f);
                    g.blit(tex, vx, vy, vw, vh, 0, 0, FrameCapture.W, FrameCapture.H, FrameCapture.W, FrameCapture.H);
                    g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
                    RenderSystem.disableBlend();
                }
            }
            HorrorOverlay.grainIn(g, vx, vy, vx + vw, vy + vh, 0.15f);
            if (((int) (t / 12)) % 2 == 0) g.fill(vx + 8, vy + 9, vx + 14, vy + 15, 0xFFE02020);
            g.drawString(font, "REC", vx + 17, vy + 8, HorrorOverlay.argb(a, 0xF0F0F0), true);
            String stamp = Clock.stamp(Clock.DAWN - 1200 + (t - 130) / 20.0f);
            g.drawString(font, stamp, vx + vw - 8 - font.width(stamp), vy + vh - 16, HorrorOverlay.argb(a, 0xF0F0F0), true);
            line(g, I18n.get("ending.hitori.oni.2"), 160, 200, vy + vh + 18, 0xB0B0B0, 1.0f);
        }
    }

    // ------------------------------------------------------------------------------------------------ 外へ
    private void soto(GuiGraphics g, float t, int cy) {
        g.fill(0, 0, width, height, 0xFF050608);
        line(g, I18n.get("ending.hitori.soto.1"), 10, 110, cy, 0xC8CCD0, 1.0f);
        line(g, I18n.get("ending.hitori.soto.2"), 120, 110, cy, 0xC8CCD0, 1.0f);
        line(g, I18n.get("ending.hitori.soto.3"), 230, 70, cy, 0xA9BCCB, 1.0f);
    }

    // ------------------------------------------------------------------------------------------------ 未完
    private void mikan(GuiGraphics g, float t, int cy) {
        g.fill(0, 0, width, height, 0xFF0A0B0D);
        line(g, I18n.get("ending.hitori.mikan.1"), 10, 100, cy, 0xC8CCD0, 1.0f);
        line(g, I18n.get("ending.hitori.mikan.2"), 110, 90, cy, 0xC8CCD0, 1.0f);
        if (ticks > 200 && ticks < cardAt) {
            List<String> frames = FrameCapture.finals("balcony");
            int vw = Math.min(width - 80, (height - 70) * 16 / 9), vh = vw * 9 / 16;
            int vx = (width - vw) / 2, vy = (height - vh) / 2 - 10;
            if (!frames.isEmpty()) {
                int i = Math.min(frames.size() - 1, (ticks - 200) / 60);
                ResourceLocation tex = FrameCapture.texture(frames.get(i));
                if (tex != null) {
                    RenderSystem.enableBlend();
                    g.blit(tex, vx, vy, vw, vh, 0, 0, FrameCapture.W, FrameCapture.H, FrameCapture.W, FrameCapture.H);
                    RenderSystem.disableBlend();
                }
            } else {
                HorrorOverlay.grainIn(g, vx, vy, vx + vw, vy + vh, 0.6f);
            }
            HorrorOverlay.grainIn(g, vx, vy, vx + vw, vy + vh, 0.12f);
            String stamp = Clock.stamp(Clock.DAWN + (t - 200) / 20.0f);
            g.drawString(font, stamp, vx + vw - 8 - font.width(stamp), vy + vh - 16, 0xFFF0F0F0, true);
            line(g, I18n.get("ending.hitori.mikan.3"), 220, 170, vy + vh + 16, 0xB0B0B0, 1.0f);
        }
    }

    private static int lerp(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int gg = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bb = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (gg << 8) | bb;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        advance();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        advance();
        return true;
    }

    private void advance() {
        if (ticks < cardAt + 60) {
            // the text can be hurried, the footage cannot
            if (kind == GameData.ENDING_GOOD && ticks < playStart - 80) ticks = playStart - 80;
            return;
        }
        if (ticks < endAt()) {
            ticks = Math.max(ticks, endAt() - 40);
            return;
        }
        finish();
    }

    private void finish() {
        if (!sent && !localOnly) ModNetwork.sendToServer(new C2S.Action(C2S.ENDING_DONE, 0));
        sent = true;
        onClose();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
