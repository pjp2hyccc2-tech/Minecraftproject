package com.clockworkdiver.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** A riveted brass plate button. {@code glow} makes it pulse to draw the eye (the 修理台 button). */
public class BrassButton extends Button {
    public boolean glow;
    public float textScale = 1.0f;
    private int age;

    public BrassButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        age++;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        boolean hover = isHoveredOrFocused() && active;
        float x0 = getX(), y0 = getY(), x1 = getX() + width, y1 = getY() + height;
        Gfx.begin(g);
        if (glow && active) {
            float p = 0.5f + 0.5f * (float) Math.sin(age * 0.12f);
            for (int i = 4; i >= 1; i--) {
                float e = i * 2.0f + p * 3.0f;
                Gfx.rect(x0 - e, y0 - e, x1 + e, y1 + e, Gfx.withAlpha(0xFFD27A, (int) (26 + 14 * p)));
            }
        }
        int top = !active ? 0xFF8A7F70 : hover ? 0xFFFFE08A : 0xFFE8B95A;
        int bottom = !active ? 0xFF5A5248 : hover ? 0xFFC08A2E : 0xFF9C6B26;
        Gfx.rect(x0, y0, x1, y1, 0xFF2E1D0E);
        Gfx.rectV(x0 + 1, y0 + 1, x1 - 1, y1 - 1, top, bottom);
        Gfx.rect(x0 + 2, y0 + 2, x1 - 2, y0 + 3, Gfx.withAlpha(0xFFFFFF, 70));
        float r = Math.min(2.0f, height * 0.12f);
        for (float[] p : new float[][]{{x0 + 4, y0 + 4}, {x1 - 4, y0 + 4}, {x0 + 4, y1 - 4}, {x1 - 4, y1 - 4}}) {
            Gfx.circle(p[0], p[1], r + 0.5f, 0xFF5A3A16);
            Gfx.circle(p[0] - 0.3f, p[1] - 0.3f, r, 0xFFFFF0C0);
        }
        Gfx.end();
        int color = active ? 0xFF2A1808 : 0xFF3A3530;
        g.pose().pushPose();
        float cx = x0 + width / 2.0f, cy = y0 + height / 2.0f;
        g.pose().translate(cx, cy, 0.0f);
        g.pose().scale(textScale, textScale, 1.0f);
        Component msg = getMessage();
        g.drawString(font, msg, -font.width(msg) / 2, -4, color, false);
        g.pose().popPose();
    }
}
