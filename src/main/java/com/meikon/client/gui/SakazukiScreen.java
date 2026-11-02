package com.meikon.client.gui;

import com.meikon.network.C2S;
import com.meikon.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The three sake cups of the wedding. Drink, and you will never go home. */
public class SakazukiScreen extends Screen {
    public SakazukiScreen() {
        super(Component.translatable("gui.meikon.sakazuki"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.meikon.sakazuki.drink"), b -> {
            ModNetwork.sendToServer(new C2S.Action(C2S.SAKAZUKI));
            onClose();
        }).bounds(width / 2 - 154, height / 2 + 30, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.meikon.sakazuki.refuse"), b -> onClose())
                .bounds(width / 2 + 4, height / 2 + 30, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xE0200204);
        g.drawCenteredString(font, title, width / 2, height / 2 - 40, 0xC02020);
        g.drawCenteredString(font, Component.translatable("gui.meikon.sakazuki.text1"), width / 2, height / 2 - 18, 0xD8C8C0);
        g.drawCenteredString(font, Component.translatable("gui.meikon.sakazuki.text2"), width / 2, height / 2 - 6, 0x9A8A84);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
