package com.reisatsu.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reisatsu.Reisatsu;
import com.reisatsu.client.ClientPacketHandler;
import com.reisatsu.client.HorrorAudio;
import com.reisatsu.client.HorrorOverlay;
import com.reisatsu.client.Tex;
import com.reisatsu.game.GameData;
import com.reisatsu.network.C2S;
import com.reisatsu.network.ModNetwork;
import com.reisatsu.registry.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The two endings (docs/DESIGN.md §1): 送り — dawn, the text by how many お札 burnt (none: 「完璧な夜」); 相席 — table 7,
 * the glass of water, the call button that rings twice. Then the credits.
 */
public class EndingScreen extends Screen {
    private static final ResourceLocation LOGO = Reisatsu.id("textures/gui/title_logo.png");
    private static final int CREDITS = 16;
    private final int kind;
    private final int ofudaUsed;
    private int ticks;
    private boolean sent;
    public boolean localOnly;

    public EndingScreen(int kind, String extra) {
        super(Component.translatable("ending.reisatsu.title"));
        this.kind = kind;
        int n = 0;
        try {
            n = Integer.parseInt(extra.trim());
        } catch (Exception ignored) {
        }
        this.ofudaUsed = Mth.clamp(n, 0, 3);
    }

    public void setTicks(int t) {
        ticks = t;
    }

    private boolean good() {
        return kind == GameData.ENDING_GOOD;
    }

    private int cardAt() {
        return good() ? 490 : 470;
    }

    private int creditsAt() {
        return cardAt() + 150;
    }

    private int endAt() {
        return creditsAt() + CREDITS * 36 + 160;
    }

    @Override
    public void tick() {
        ticks++;
        if (ticks == 1) {
            HorrorAudio.stopAll();
            ClientPacketHandler.ui(good() ? ModSounds.MUSIC_ENDING_GOOD.get() : ModSounds.MUSIC_ENDING_BAD.get(), 0.85f, 1.0f);
            if (!good()) ClientPacketHandler.ui(ModSounds.GAME_ENDING_BAD.get(), 0.8f, 1.0f);
        }
        if (!good() && (ticks == 330 || ticks == 352)) ClientPacketHandler.ui(ModSounds.FIXTURE_CALL_BUTTON.get(), 0.7f, 0.97f);
        if (ticks == cardAt()) ClientPacketHandler.ui(ModSounds.GAME_TITLE_HIT.get(), 0.9f, 1.0f);
    }

    private void line(GuiGraphics g, String text, int start, int length, int y, int color) {
        int t = ticks - start;
        if (t < 0 || t > length) return;
        float a = t < 20 ? t / 20.0f : t > length - 20 ? (length - t) / 20.0f : 1.0f;
        int lw = width - 60;
        var lines = font.split(Component.literal(text), lw);
        int yy = y - lines.size() * 6;
        for (var l : lines) {
            g.drawCenteredString(font, l, width / 2, yy, HorrorOverlay.argb(a, color));
            yy += 12;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        float t = ticks + partial;
        int cy = height / 2 - 4;
        if (good()) {
            float dawn = Mth.clamp(t / 420.0f, 0.0f, 1.0f);
            int top = lerpColor(0x05070C, 0x3A4A6A, dawn), bottom = lerpColor(0x080808, 0xE8A870, dawn);
            g.fillGradient(0, 0, width, height, 0xFF000000 | top, 0xFF000000 | bottom);
            int col = 0xF0E8DC;
            line(g, I18n.get("ending.reisatsu.good.1"), 20, 90, cy, col);
            line(g, I18n.get("ending.reisatsu.good.2"), 110, 90, cy, col);
            line(g, I18n.get("ending.reisatsu.good.3"), 200, 90, cy, col);
            line(g, I18n.get("ending.reisatsu.good.ofuda." + ofudaUsed), 290, 100, cy, ofudaUsed == 0 ? 0xFFF0C8 : col);
            line(g, I18n.get("ending.reisatsu.good.5"), 390, 100, cy, col);
        } else {
            g.fill(0, 0, width, height, 0xFF07080A);
            int col = 0xC8CCD0;
            line(g, I18n.get("ending.reisatsu.bad.1"), 20, 90, cy, col);
            line(g, I18n.get("ending.reisatsu.bad.2"), 110, 90, cy, 0xA9BCCB);
            line(g, I18n.get("ending.reisatsu.bad.3"), 200, 90, cy, col);
            line(g, I18n.get("ending.reisatsu.bad.4"), 300, 150, cy, col);
        }
        int lt = ticks - cardAt();
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
                HorrorOverlayAccess.big(g, font, "霊撮", width / 2, height / 2 - 50, HorrorOverlay.argb(a, good() ? 0x2A1810 : 0xE8E0D0), 4.0f);
            }
            String end = I18n.get(good() ? "ending.reisatsu.end_good" : "ending.reisatsu.end_bad");
            g.drawCenteredString(font, end, width / 2, height / 2 + 22, HorrorOverlay.argb(a, good() ? 0x3A1A10 : 0xC8C0B4));
        }
        // credits
        int ct = ticks - creditsAt();
        if (ct >= 0) {
            float scrollY = height + 10 - (ct + partial) * 0.55f;
            for (int i = 1; i <= CREDITS; i++) {
                String key = "credits.reisatsu." + i;
                if (!I18n.exists(key)) continue;
                float y = scrollY + (i - 1) * 20;
                if (y < -10 || y > height + 10) continue;
                float a = Mth.clamp(Math.min((y + 10) / 40.0f, (height - y) / 40.0f), 0.0f, 1.0f);
                g.drawCenteredString(font, I18n.get(key), width / 2, (int) y, HorrorOverlay.argb(a, good() ? 0x2A1A12 : 0xC8C8C8));
            }
        }
        if (ticks > cardAt() + 60) {
            g.drawCenteredString(font, I18n.get("ending.reisatsu.continue"), width / 2, height - 20, HorrorOverlay.argb(0.6f, good() ? 0x3A2A20 : 0xA0A0A0));
        }
        super.render(g, mouseX, mouseY, partial);
    }

    private static int lerpColor(int a, int b, float t) {
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
        if (ticks < cardAt() + 60) return;
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

    /** Big centred text (package helper). */
    static final class HorrorOverlayAccess {
        static void big(GuiGraphics g, net.minecraft.client.gui.Font font, String text, int cx, int y, int color, float scale) {
            g.pose().pushPose();
            g.pose().translate(cx, y, 0);
            g.pose().scale(scale, scale, 1.0f);
            g.drawString(font, text, -font.width(text) / 2, 0, color, false);
            g.pose().popPose();
        }
    }
}
