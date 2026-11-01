package com.frostbound.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** A themed widget button for vanilla screens (title screen). */
public final class FButton extends AbstractButton {
    private final Runnable action;

    public FButton(int x, int y, int w, int h, Component label, Runnable action) {
        super(x, y, w, h, label);
        this.action = action;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme.button(g, getX(), getY(), width, height, getMessage(), isHoveredOrFocused(), active, false);
        g.fill(getX() + 2, getY() + height - 3, getX() + width - 2, getY() + height - 2, Theme.ORANGE_DIM);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
