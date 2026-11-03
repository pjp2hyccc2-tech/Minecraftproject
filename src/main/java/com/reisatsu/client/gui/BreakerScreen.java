package com.reisatsu.client.gui;

import com.reisatsu.client.ClientPacketHandler;
import com.reisatsu.network.C2S;
import com.reisatsu.network.ModNetwork;
import com.reisatsu.registry.ModSounds;
import com.reisatsu.world.Circuit;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/** The breaker panel 分電盤 (docs/DESIGN.md §3.9): a grey box of big switches; every throw is a loud clunk. */
public class BreakerScreen extends Screen {
    private int on;
    private int x0, y0, pw, ph;
    private int cooldown;

    public BreakerScreen(int on) {
        super(Component.translatable("gui.reisatsu.breaker"));
        this.on = on;
    }

    public void update(int mask) {
        on = mask;
    }

    @Override
    protected void init() {
        pw = Math.min(width - 40, 330);
        ph = 170;
        x0 = (width - pw) / 2;
        y0 = (height - ph) / 2;
        ClientPacketHandler.ui(ModSounds.DOOR_UNLOCK.get(), 0.5f, 1.3f);
    }

    @Override
    public void tick() {
        if (cooldown > 0) cooldown--;
    }

    private int slotX(int i) {
        int n = Circuit.values().length;
        return x0 + 16 + i * ((pw - 32) / n);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.fill(x0 - 4, y0 - 4, x0 + pw + 4, y0 + ph + 4, 0xFF2C2E30);
        g.fill(x0, y0, x0 + pw, y0 + ph, 0xFF8A8E90);
        g.fill(x0 + 6, y0 + 6, x0 + pw - 6, y0 + 22, 0xFF6E7274);
        g.drawCenteredString(font, title, width / 2, y0 + 10, 0xFFF0F0F0);
        int n = Circuit.values().length;
        int sw = (pw - 32) / n;
        for (int i = 0; i < n; i++) {
            Circuit c = Circuit.values()[i];
            boolean lit = (on & (1 << c.ordinal())) != 0;
            int x = slotX(i), y = y0 + 40;
            g.fill(x + 4, y, x + sw - 4, y + 80, 0xFF3A3C3E);
            int lever = lit ? y + 8 : y + 44;
            g.fill(x + 8, lever, x + sw - 8, lever + 28, lit ? 0xFFD8D4C8 : 0xFF9A9690);
            g.fill(x + 8, lever + 26, x + sw - 8, lever + 28, 0xFF505050);
            g.fill(x + sw / 2 - 2, y + 86, x + sw / 2 + 2, y + 90, lit ? 0xFF40E060 : 0xFF402020);
            String label = I18n.get("circuit.reisatsu." + c.key());
            g.drawCenteredString(font, font.plainSubstrByWidth(label, sw), x + sw / 2, y + 96, 0xFF1A1A1A);
            g.drawCenteredString(font, lit ? "ON" : "OFF", x + sw / 2, y + 108, lit ? 0xFF1A5A2A : 0xFF5A1A1A);
        }
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int n = Circuit.values().length;
        int sw = (pw - 32) / n;
        for (int i = 0; i < n; i++) {
            int x = slotX(i), y = y0 + 40;
            if (mouseX >= x && mouseX < x + sw && mouseY >= y && mouseY < y + 90 && cooldown == 0) {
                Circuit c = Circuit.values()[i];
                boolean lit = (on & (1 << c.ordinal())) != 0;
                ModNetwork.sendToServer(new C2S.Action(C2S.BREAKER, (c.ordinal() << 1) | (lit ? 0 : 1)));
                on ^= 1 << c.ordinal();
                cooldown = 6;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
