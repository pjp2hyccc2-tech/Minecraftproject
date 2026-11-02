package com.sounan.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sounan.Sounan;
import com.sounan.SounanConfig;
import com.sounan.client.ClientPacketHandler;
import com.sounan.client.ClientState;
import com.sounan.client.HorrorAudio;
import com.sounan.network.C2S;
import com.sounan.network.ModNetwork;
import com.sounan.registry.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/** The two ways the night ends: 下山 (down the mountain — but how long were you gone?) and 神隠し. */
public class EndingScreen extends Screen {
    private static final ResourceLocation LOGO = Sounan.id("textures/gui/title_logo.png");
    private static final ResourceLocation FACE = Sounan.id("textures/gui/jumpscare_ushiro.png");
    private static final RandomSource RAND = RandomSource.create();
    private final int kind;
    private final String name;
    private final int captures;
    private int ticks;
    private boolean sent;
    public boolean localOnly;

    /** {@code extra} = "name|captures|minutes". */
    public EndingScreen(int kind, String extra) {
        super(Component.translatable("ending.sounan.title"));
        this.kind = kind;
        String[] p = extra.split("\\|");
        this.name = p.length > 0 ? p[0] : "";
        int c = 0;
        try {
            c = p.length > 1 ? Integer.parseInt(p[1]) : 0;
        } catch (NumberFormatException ignored) {
        }
        this.captures = c;
    }

    public void setTicks(int t) {
        ticks = t;
    }

    private int logoAt() {
        return kind == 2 ? 600 : captures > 0 ? 720 : 580;
    }

    @Override
    public void tick() {
        ticks++;
        if (ticks == 1) {
            HorrorAudio.stopAll();
            ClientPacketHandler.ui(kind == 2 ? ModSounds.GAME_KAMIKAKUSHI.get() : ModSounds.GAME_CHECKPOINT.get(), 1.0f, 1.0f);
            ClientPacketHandler.ui(kind == 2 ? ModSounds.MUSIC_ENDING_BAD.get() : ModSounds.MUSIC_ENDING_GOOD.get(), 0.8f, 1.0f);
        }
        if (kind == 1) {
            if (ticks == 190) ClientPacketHandler.ui(ModSounds.GAME_CAR.get(), 0.9f, 1.0f);
            if (ticks == 290) ClientPacketHandler.ui(ModSounds.PHONE_SIGNAL.get(), 0.9f, 1.0f);
            if (captures > 0 && ticks == 640) {
                ClientPacketHandler.ui(ModSounds.GAME_JUMPSCARE.get(), 1.0f, 0.9f);
                ClientPacketHandler.ui(ModSounds.USHIRO_ATTACK.get(), 1.0f, 1.0f);
            }
        } else if (ticks == 300) {
            ClientPacketHandler.ui(ModSounds.PHONE_SHUTTER.get(), 0.8f, 1.0f);
        }
        if (ticks == logoAt()) ClientPacketHandler.ui(ModSounds.GAME_TITLE_HIT.get(), 1.0f, 1.0f);
    }

    private void line(GuiGraphics g, String text, int start, int length, int y) {
        int t = ticks - start;
        if (t < 0 || t > length) return;
        float a = t < 20 ? t / 20.0f : t > length - 20 ? (length - t) / 20.0f : 1.0f;
        int col = ((int) (a * 255) << 24) | 0xD8D0C4;
        g.drawCenteredString(font, text, width / 2, y, col);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, kind == 2 ? 0xFF0E1012 : 0xFF000000);
        int cy = height / 2 - 4;
        if (kind == 1) {
            if (ticks > 380 && captures == 0) {
                float dawn = Math.min(1.0f, (ticks - 380) / 300.0f);
                g.fillGradient(0, height / 2, width, height, 0x00000000, ((int) (dawn * 60) << 24) | 0x6A4A3A);
            }
            line(g, I18n.get("ending.sounan.good.1"), 20, 90, cy);
            line(g, I18n.get("ending.sounan.good.2"), 110, 90, cy);
            line(g, I18n.get("ending.sounan.good.3"), 200, 90, cy);
            line(g, I18n.get("ending.sounan.good.4"), 290, 90, cy);
            int[] d = ClientState.dateAfter(com.sounan.game.GameData.dayOffset(captures));
            String date = I18n.get("ending.sounan.date", d[0], d[1], d[2]);
            line(g, captures == 0 ? I18n.get("ending.sounan.good.5a") : I18n.get("ending.sounan.good.5b", date), 380, 90, cy);
            line(g, captures == 0 ? I18n.get("ending.sounan.good.6a") : I18n.get("ending.sounan.good.6b"), 470, 90, cy);
            if (captures > 0) {
                line(g, I18n.get("ending.sounan.good.7"), 560, 80, cy);
                int t = ticks - 640;
                if (t >= 0 && t < 10 && SounanConfig.jumpscares()) {
                    int s = (int) (Math.max(width, height) * (1.1f + t * 0.04f));
                    int jx = SounanConfig.flashReduction() ? 0 : RAND.nextInt(13) - 6;
                    RenderSystem.enableBlend();
                    g.blit(FACE, (width - s) / 2 + jx, (height - s) / 2, s, s, 0, 0, 256, 256, 256, 256);
                    RenderSystem.disableBlend();
                }
            }
        } else {
            float fog = Math.min(1.0f, ticks / 200.0f) * (ticks < 300 ? 1.0f : 0.25f);
            g.fill(0, 0, width, height, ((int) (fog * 120) << 24) | 0x9AA2A8);
            line(g, I18n.get("ending.sounan.bad.1"), 20, 90, cy);
            line(g, I18n.get("ending.sounan.bad.2"), 110, 90, cy);
            line(g, I18n.get("ending.sounan.bad.3"), 200, 90, cy);
            if (ticks >= 300 && ticks < logoAt()) {
                int m = 30, len = 18, col = 0xC0E0E8E0;
                g.fill(m, m, m + len, m + 1, col);
                g.fill(m, m, m + 1, m + len, col);
                g.fill(width - m - len, height - m - 1, width - m, height - m, col);
                g.fill(width - m - 1, height - m - len, width - m, height - m, col);
                if ((ticks / 10) % 2 == 0) g.fill(m + 8, m + 10, m + 14, m + 16, 0xFFE02020);
                g.drawString(font, "REC", m + 18, m + 9, 0xFFE0E0E0, false);
            }
            line(g, I18n.get("ending.sounan.bad.4"), 300, 90, cy);
            line(g, I18n.get("ending.sounan.bad.5"), 390, 90, cy);
            line(g, I18n.get("ending.sounan.bad.6", name), 480, 110, cy);
        }
        int lt = ticks - logoAt();
        if (lt >= 0) {
            float a = Math.min(1.0f, lt / 40.0f);
            int lw = Math.min(width - 40, 320), lh = lw / 2;
            RenderSystem.enableBlend();
            g.setColor(1.0f, 1.0f, 1.0f, a);
            g.blit(LOGO, (width - lw) / 2, height / 2 - lh + 10, lw, lh, 0, 0, 512, 256, 512, 256);
            g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
            String end = I18n.get(kind == 2 ? "ending.sounan.end_bad" : captures > 0 ? "ending.sounan.end_good_company" : "ending.sounan.end_good");
            g.drawCenteredString(font, end, width / 2, height / 2 + 24, ((int) (a * 255) << 24) | 0xC8C0B4);
            if (lt > 80) g.drawCenteredString(font, I18n.get("ending.sounan.continue"), width / 2, height - 30, 0x90A0A0A0);
        }
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ticks > logoAt() + 80) finish();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ticks > logoAt() + 80) finish();
        return true;
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
