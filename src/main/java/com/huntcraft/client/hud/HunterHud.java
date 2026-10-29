package com.huntcraft.client.hud;

import com.huntcraft.client.ClientState;
import com.huntcraft.data.HunterData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * MH-style health (green, with recoverable red portion) and stamina (yellow) bars at the top-left.
 * Other modules draw below {@link #NEXT_Y} (sharpness gauge, weapon gauges) or at the top-right (quest info).
 */
@OnlyIn(Dist.CLIENT)
public final class HunterHud {
    public static final int X = 8;
    public static final int Y = 8;
    /** First free y below the bars. */
    public static final int NEXT_Y = 32;

    private HunterHud() {
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || p.isSpectator()) return;
        HunterData d = HunterData.get(p);

        float hp = p.getHealth();
        float maxHp = p.getMaxHealth();
        if (ClientState.displayedHealth < 0) ClientState.displayedHealth = hp;
        if (ClientState.displayedHealth < hp) ClientState.displayedHealth = hp;
        else ClientState.displayedHealth = Math.max(hp, ClientState.displayedHealth - 0.08F);

        int hpW = (int) Mth.clamp(100 + (maxHp - 20) * 4, 60, 220);
        int x = X, y = Y;
        // frame ornament
        g.fill(x - 3, y - 3, x + hpW + 3, y + 21, 0x88000000);
        // health
        drawBar(g, x, y, hpW, 8, 0xFF2A0E0E);
        int red = (int) (hpW * Mth.clamp(ClientState.displayedHealth / maxHp, 0, 1));
        int green = (int) (hpW * Mth.clamp(hp / maxHp, 0, 1));
        g.fill(x, y, x + red, y + 8, 0xFFB3261E);
        g.fillGradient(x, y, x + green, y + 8, 0xFF66E36B, 0xFF2E9E3A);
        g.fill(x, y, x + green, y + 1, 0x55FFFFFF);
        if (p.getAbsorptionAmount() > 0) {
            int ab = (int) (hpW * Mth.clamp(p.getAbsorptionAmount() / maxHp, 0, 1));
            g.fill(x, y + 6, x + ab, y + 8, 0xFFF5D76E);
        }
        // stamina
        int stW = (int) Mth.clamp(d.maxStamina, 40, 160);
        int sy = y + 11;
        drawBar(g, x, sy, Math.max(stW, 100), 7, 0xFF241C08);
        int st = (int) (stW * Mth.clamp(d.stamina / Math.max(1, d.maxStamina), 0, 1));
        int col1 = d.exhausted ? ((p.tickCount / 4) % 2 == 0 ? 0xFFE0602A : 0xFFB04020) : 0xFFFFD84A;
        int col2 = d.exhausted ? 0xFF903010 : 0xFFD49A1A;
        g.fillGradient(x, sy, x + st, sy + 7, col1, col2);
        g.fill(x, sy, x + st, sy + 1, 0x55FFFFFF);
        if (stW < 100) g.fill(x + stW, sy, x + 100, sy + 7, 0xFF3A3A3A);
    }

    private static void drawBar(GuiGraphics g, int x, int y, int w, int h, int bg) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + w, y + h, bg);
    }
}
