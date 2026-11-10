package com.eternalrealm.ui.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Arriving through a waystone: the screen flashes a pale blue-white that fades out over about a second while the new
 * place appears behind it.
 */
public final class TravelFlashOverlay implements IGuiOverlay {
    private static final long DURATION_MS = 1100L;
    private static long startedAt = -1L;

    /** Called when the server says the player arrived ({@code Waystones.ARRIVE_CHANNEL}). */
    public static void start() {
        startedAt = System.currentTimeMillis();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 1.4F);
        }
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        if (startedAt < 0L) {
            return;
        }
        long elapsed = System.currentTimeMillis() - startedAt;
        if (elapsed >= DURATION_MS) {
            startedAt = -1L;
            return;
        }
        double f = elapsed / (double) DURATION_MS;
        double alpha = f < 0.12 ? 0.9 : 0.9 * Math.pow(1.0 - (f - 0.12) / 0.88, 1.6);
        int a = (int) Math.round(Math.max(0.0, Math.min(1.0, alpha)) * 255.0);
        graphics.fill(0, 0, width, height, (a << 24) | 0xD8F4FF);
    }
}
