package com.meikon.client.gui;

import com.meikon.Meikon;
import com.meikon.network.C2S;
import com.meikon.network.ModNetwork;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** The red envelope: an invitation with your name, and the choice to go. */
public class EnvelopeScreen extends Screen {
    private static final ResourceLocation PAPER = Meikon.id("textures/gui/paper.png");
    private final String name;
    private List<FormattedCharSequence> lines = List.of();
    private List<FormattedCharSequence> warning = List.of();

    public EnvelopeScreen(String name) {
        super(Component.translatable("note.meikon.1.title"));
        this.name = name;
    }

    @Override
    protected void init() {
        int pw = Math.min(280, width - 30);
        lines = font.split(Component.translatable("note.meikon.1.body", name, ""), pw - 48);
        warning = font.split(Component.translatable("gui.meikon.warning"), Math.min(360, width - 20));
        int by = height - 46;
        addRenderableWidget(Button.builder(Component.translatable("gui.meikon.envelope.go"), b -> {
            ModNetwork.sendToServer(new C2S.Action(C2S.START_GAME));
            onClose();
        }).bounds(width / 2 - 154, by, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.meikon.envelope.close"), b -> onClose())
                .bounds(width / 2 + 4, by, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xF0080305);
        int pw = Math.min(280, width - 30);
        int ph = Math.min(height - 120, 230);
        int px = (width - pw) / 2;
        int py = 14;
        RenderSystem.enableBlend();
        g.setColor(1.0f, 0.86f, 0.84f, 1.0f);
        g.blit(PAPER, px, py, pw, ph, 0, 0, 256, 256, 256, 256);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        g.drawCenteredString(font, title, width / 2, py + 18, 0x6A0A0A);
        int y = py + 36;
        for (FormattedCharSequence l : lines) {
            if (y > py + ph - 16) break;
            g.drawString(font, l, px + 24, y, 0x2B1D12, false);
            y += 11;
        }
        y = py + ph + 8;
        for (FormattedCharSequence l : warning) {
            g.drawCenteredString(font, l, width / 2, y, 0xA89A8A);
            y += 10;
        }
        super.render(g, mouseX, mouseY, partial);
    }
}
