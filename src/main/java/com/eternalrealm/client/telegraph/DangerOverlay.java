package com.eternalrealm.client.telegraph;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * While the player stands inside a charging attack marker, the screen edges pulse red: a second cue for markers that
 * are behind the camera or under the player's feet.
 */
public final class DangerOverlay implements IGuiOverlay {
    private float strength;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean danger = !minecraft.options.hideGui && ClientTelegraphs.localPlayerInDanger();
        strength = Mth.clamp(strength + (danger ? 0.15F : -0.08F), 0.0F, 1.0F);
        if (strength <= 0.0F) {
            return;
        }
        float time = ClientTelegraphs.now() + partialTick;
        float pulse = 0.65F + 0.35F * Mth.sin(time * 0.9F);
        int alpha = Math.round(150.0F * strength * pulse);
        int edge = (alpha << 24) | 0xE01810;
        int clear = 0x00E01810;
        int depth = Math.max(18, height / 7);
        graphics.fillGradient(0, 0, width, depth, edge, clear);
        graphics.fillGradient(0, height - depth, width, height, clear, edge);
        // left and right: draw thin vertical slices with decreasing alpha
        int side = Math.max(18, width / 10);
        for (int i = 0; i < side; i += 2) {
            int a = Math.round(alpha * (1.0F - (float) i / side));
            int color = (a << 24) | 0xE01810;
            graphics.fill(i, 0, i + 2, height, color);
            graphics.fill(width - i - 2, 0, width - i, height, color);
        }
    }
}
