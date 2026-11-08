package com.eternalrealm.client.boss;

import com.eternalrealm.client.CameraShake;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Screen effects of boss fights sent by the server on the UI data channel {@link #CHANNEL}
 * ({@code boss.forge.BossFx}): camera shake for heavy hits, transformations and the collapse, and a full-screen flash
 * that fades out (the colour of a transformation, white when a defeated boss dissolves). Client thread only.
 */
public final class BossScreenEffects implements IGuiOverlay {
    public static final String CHANNEL = "boss_fx";
    private static long flashStart = Long.MIN_VALUE / 2;
    private static int flashTicks;
    private static int flashRgb;
    private static float flashAlpha;

    /** One effect message from the server. */
    public static void accept(JsonObject data) {
        float shake = number(data, "shake");
        int shakeTicks = Math.round(number(data, "shakeTicks"));
        if (shake > 0.0F && shakeTicks > 0) {
            CameraShake.add(Math.min(3.0F, shake), Math.min(60, shakeTicks));
        }
        float alpha = number(data, "alpha");
        int ticks = Math.round(number(data, "flashTicks"));
        if (alpha > 0.0F && ticks > 0) {
            flashStart = now();
            flashTicks = Math.min(80, ticks);
            flashRgb = Math.round(number(data, "rgb")) & 0xFFFFFF;
            flashAlpha = Mth.clamp(alpha, 0.0F, 0.9F);
        }
    }

    private static float number(JsonObject data, String key) {
        JsonElement element = data.get(key);
        try {
            return element == null || !element.isJsonPrimitive() ? 0.0F : element.getAsFloat();
        } catch (NumberFormatException e) {
            return 0.0F;
        }
    }

    private static long now() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        if (flashTicks <= 0) {
            return;
        }
        float elapsed = now() - flashStart + partialTick;
        if (elapsed < 0.0F || elapsed > flashTicks) {
            return;
        }
        float fade = 1.0F - elapsed / flashTicks;
        int alpha = Math.round(255.0F * flashAlpha * fade * fade);
        if (alpha > 0) {
            graphics.fill(0, 0, width, height, (alpha << 24) | flashRgb);
        }
    }
}
