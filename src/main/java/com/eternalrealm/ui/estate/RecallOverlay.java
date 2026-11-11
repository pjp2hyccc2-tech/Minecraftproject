package com.eternalrealm.ui.estate;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * The way home on the HUD: while the cast runs, a brass-framed bar fills above the quick slots with a little key that
 * turns as it goes; a broken cast flashes red for a moment, an arrival lets the bar glow out.
 */
public final class RecallOverlay implements IGuiOverlay {
    private static final long FLASH_NANOS = 1_400_000_000L;
    private static final int BAR_W = 140;

    private static boolean casting;
    private static long startedAt;
    private static long castNanos = 3_000_000_000L;
    private static long endedAt = Long.MIN_VALUE / 2;
    private static boolean broken;
    private static String reason = "";

    /** The server's word about the cast: {@code cast}, {@code broken}, {@code refused} or {@code done}. */
    public static void accept(JsonObject json) {
        String state = EstateUi.str(json, "state");
        long now = System.nanoTime();
        switch (state) {
            case "cast" -> {
                casting = true;
                startedAt = now;
                castNanos = Math.max(1L, EstateUi.num(json, "ticks")) * 50_000_000L;
                broken = false;
            }
            case "broken" -> {
                if (casting) {
                    casting = false;
                    broken = true;
                    reason = EstateUi.str(json, "reason");
                    endedAt = now;
                }
            }
            case "done" -> {
                casting = false;
                broken = false;
                endedAt = now;
            }
            default -> {
            }
        }
    }

    /** Whether a cast is running (pressing the key again cancels it). */
    public static boolean casting() {
        return casting;
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = gui.getMinecraft();
        if (minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        long now = System.nanoTime();
        boolean flashing = !casting && now - endedAt < FLASH_NANOS;
        if (!casting && !flashing) {
            return;
        }
        Font font = minecraft.font;
        int x = (screenWidth - BAR_W) / 2;
        int y = screenHeight - 86;
        float progress = casting ? Math.min(1.0F, (now - startedAt) / (float) castNanos) : broken ? 0.0F : 1.0F;
        int alpha = casting ? 255 : (int) (255 * (1.0F - (now - endedAt) / (float) FLASH_NANOS));
        if (alpha <= 8) {
            return;
        }
        int a = alpha << 24;
        graphics.fill(x - 2, y - 2, x + BAR_W + 2, y + 9, (alpha * 9 / 16) << 24);
        graphics.fill(x - 1, y - 1, x + BAR_W + 1, y + 8, a | (EstateUi.BRASS & 0xFFFFFF));
        graphics.fill(x, y, x + BAR_W, y + 7, a | 0x1A1014);
        int fill = (int) (BAR_W * progress);
        int color = broken ? 0xC03A2A : 0xF0A830;
        graphics.fill(x, y, x + fill, y + 7, a | color);
        graphics.fill(x, y, x + fill, y + 2, a | (broken ? 0xE06050 : 0xFFE08A));
        // a brass key that turns a quarter as the cast completes
        int kx = x - 14;
        int ky = y - 3;
        boolean turned = progress > 0.5F;
        graphics.fill(kx + 2, ky + 2, kx + 8, ky + 8, a | 0xD8A84A);
        graphics.fill(kx + 4, ky + 4, kx + 6, ky + 6, a | 0x1A1014);
        if (turned) {
            graphics.fill(kx + 4, ky + 8, kx + 6, ky + 14, a | 0xD8A84A);
            graphics.fill(kx + 6, ky + 11, kx + 8, ky + 13, a | 0xD8A84A);
        } else {
            graphics.fill(kx + 8, ky + 4, kx + 14, ky + 6, a | 0xD8A84A);
            graphics.fill(kx + 11, ky + 6, kx + 13, ky + 8, a | 0xD8A84A);
        }
        Component label = casting ? Component.translatable("hud.eternalrealm.recall.casting")
                : broken ? Component.translatable("message.eternalrealm.recall.broken." + reason)
                : Component.translatable("hud.eternalrealm.recall.done");
        graphics.drawCenteredString(font, label, screenWidth / 2, y - 12, a | (broken ? 0xFF8A70 : 0xFFE6B0));
    }
}
