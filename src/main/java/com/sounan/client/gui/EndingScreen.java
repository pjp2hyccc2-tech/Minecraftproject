package com.sounan.client.gui;

import com.sounan.client.ClientPacketHandler;
import com.sounan.network.C2S;
import com.sounan.network.ModNetwork;
import com.sounan.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** The ending: lines of text rising out of the dark, then home. */
public class EndingScreen extends Screen {
    public static final int GOOD_LINES = 14;
    public static final int BAD_LINES = 9;
    private final boolean good;
    private final String name;
    private int ticks;
    private boolean sent;
    public boolean localOnly;

    public EndingScreen(boolean good, String name) {
        super(Component.translatable(good ? "ending.meikon.good.title" : "ending.meikon.bad.title"));
        this.good = good;
        this.name = name;
    }

    public void setTicks(int t) {
        this.ticks = t;
    }

    @Override
    public void tick() {
        if (ticks == 0) ClientPacketHandler.ui(good ? ModSounds.MUSIC_ENDING.get() : ModSounds.MUSIC_BAD_ENDING.get(), 1.0f, 1.0f);
        ticks++;
    }

    private int lines() {
        return good ? GOOD_LINES : BAD_LINES;
    }

    private int doneAt() {
        return 60 + lines() * 50 + 80;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        float t = ticks + partial;
        g.fill(0, 0, width, height, good ? 0xFF000000 : 0xFF140203);
        if (good && t > doneAt() - 60) {
            float dawn = Mth.clamp((t - (doneAt() - 60)) / 120.0f, 0.0f, 1.0f);
            g.fillGradient(0, height / 2, width, height, 0x00000000, ((int) (dawn * 90) << 24) | 0x6A4A6A);
        }
        float titleA = Mth.clamp(t / 40.0f, 0.0f, 1.0f);
        g.pose().pushPose();
        g.pose().translate(width / 2.0f, 30, 0);
        g.pose().scale(2.0f, 2.0f, 1.0f);
        g.drawCenteredString(font, title, 0, 0, argb(titleA, good ? 0xD8D0C4 : 0xB01818));
        g.pose().popPose();
        int baseY = 70;
        for (int i = 0; i < lines(); i++) {
            float start = 60 + i * 50;
            if (t < start) break;
            float a = Mth.clamp((t - start) / 30.0f, 0.0f, 1.0f);
            int y = baseY + i * 13;
            if (y > height - 40) {
                y = height - 40 - (lines() - 1 - i) * 13;
            }
            String key = (good ? "ending.meikon.good." : "ending.meikon.bad.") + (i + 1);
            g.drawCenteredString(font, Component.translatable(key, name), width / 2, y, argb(a, good ? 0xCFC6BA : 0xC8A8A0));
        }
        if (t > doneAt()) {
            float a = Mth.clamp((t - doneAt()) / 30.0f, 0.0f, 1.0f);
            g.drawCenteredString(font, Component.translatable(good ? "ending.meikon.fin" : "ending.meikon.bad.fin"), width / 2, height - 34, argb(a, 0x9E1818));
            g.drawCenteredString(font, Component.translatable("ending.meikon.click"), width / 2, height - 20, argb(a * 0.7f, 0x8A8078));
        }
    }

    private static int argb(float a, int rgb) {
        return (Mth.clamp((int) (a * 255.0f), 4, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private void finish() {
        if (!sent && !localOnly) {
            sent = true;
            ModNetwork.sendToServer(new C2S.Action(C2S.ENDING_DONE));
        }
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ticks > doneAt()) finish();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ticks > doneAt() && (key == 256 || key == 257 || key == 32)) {
            finish();
            return true;
        }
        return false;
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
