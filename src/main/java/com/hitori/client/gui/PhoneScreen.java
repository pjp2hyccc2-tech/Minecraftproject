package com.hitori.client.gui;

import com.hitori.client.CameraApp;
import com.hitori.client.ClientState;
import com.hitori.client.PhoneUi;
import com.hitori.game.Clock;
import com.hitori.game.GameManager;
import com.hitori.network.C2S;
import com.hitori.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/** The phone's screen (docs/DESIGN.md §3.6): the camera app, the flashlight, the silent switch, the messages. */
public class PhoneScreen extends Screen {
    public PhoneScreen() {
        super(Component.translatable("gui.hitori.phone"));
    }

    @Override
    protected void init() {
        int pw = 120, x = width / 2 - pw / 2 + 10, y = height / 2 - 40;
        addRenderableWidget(Button.builder(Component.translatable("gui.hitori.phone_camera"), b -> {
            onClose();
            CameraApp.open(1);
        }).bounds(x, y, pw - 20, 18).build());
        addRenderableWidget(Button.builder(Component.translatable(ClientState.flag(GameManager.F_LIGHT) ? "gui.hitori.phone_light_off" : "gui.hitori.phone_light_on"), b -> {
            ModNetwork.sendToServer(new C2S.Action(C2S.PHONE, 1));
            onClose();
        }).bounds(x, y + 22, pw - 20, 18).build());
        addRenderableWidget(Button.builder(Component.translatable(ClientState.flag(GameManager.F_SILENT) ? "gui.hitori.phone_sound" : "gui.hitori.phone_silent"), b -> {
            ModNetwork.sendToServer(new C2S.Action(C2S.PHONE, 0));
            onClose();
        }).bounds(x, y + 44, pw - 20, 18).build());
        if (PhoneUi.callState == 1) {
            addRenderableWidget(Button.builder(Component.translatable("gui.hitori.phone_decline"), b -> {
                ModNetwork.sendToServer(new C2S.Action(C2S.PHONE, 3));
                onClose();
            }).bounds(x, y + 66, pw - 20, 18).build());
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        int pw = 140, ph = 230;
        int x0 = width / 2 - pw / 2, y0 = height / 2 - ph / 2;
        g.fill(x0 - 4, y0 - 4, x0 + pw + 4, y0 + ph + 4, 0xFF0C0D10);
        g.fill(x0, y0, x0 + pw, y0 + ph, 0xFF161B24);
        g.drawCenteredString(font, Clock.hhmm(ClientState.clock), width / 2, y0 + 10, 0xFFE8EEF4);
        g.drawCenteredString(font, I18n.get("phone.hitori.date"), width / 2, y0 + 22, 0xFF8A94A0);
        int y = height / 2 + 52;
        g.drawString(font, I18n.get("phone.hitori.messages"), x0 + 8, y, 0xFF8A94A0, false);
        y += 11;
        for (int i = Math.max(0, PhoneUi.MESSAGES.size() - 4); i < PhoneUi.MESSAGES.size(); i++) {
            String m = font.plainSubstrByWidth(I18n.get(PhoneUi.MESSAGES.get(i)), pw - 16);
            g.drawString(font, m, x0 + 8, y, 0xFFD8DEE6, false);
            y += 10;
        }
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
