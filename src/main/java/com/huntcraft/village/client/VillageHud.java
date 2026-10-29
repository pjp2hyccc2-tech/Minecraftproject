package com.huntcraft.village.client;

import com.huntcraft.village.client.screen.VillageScreen;
import com.huntcraft.village.companion.MofuEntity;
import com.huntcraft.village.meal.Ingredients;
import com.huntcraft.village.meal.MealSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** Left-middle HUD block: the active companion's health and the current meal's remaining time. */
@OnlyIn(Dist.CLIENT)
public final class VillageHud {
    private static int mofuId = -1;
    private static long lastScan;

    private VillageHud() {
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.options.hideGui || p.isSpectator()) return;
        Font font = mc.font;
        int x = 6, y = height / 2 + 12;
        MofuEntity mofu = findMofu(mc, p);
        if (mofu != null) {
            g.fill(x - 2, y - 2, x + 92, y + 20, 0x80000000);
            g.blit(VillageScreen.WIDGETS, x, y, 16, 16, VillageScreen.FACE_U + mofu.getPattern() * 24, VillageScreen.FACE_V, 24, 24,
                    VillageScreen.ATLAS, VillageScreen.ATLAS);
            String name = mofu.getDisplayName().getString();
            if (font.width(name) > 52) name = font.plainSubstrByWidth(name, 50) + "…";
            g.drawString(font, name, x + 19, y, 0xFFF4E9D0, true);
            g.drawString(font, "Lv" + mofu.getMofuLevel(), x + 73, y, 0xFFFFD46A, true);
            float frac = Mth.clamp(mofu.getHealth() / Math.max(1.0F, mofu.getMaxHealth()), 0, 1);
            g.fill(x + 19, y + 11, x + 89, y + 16, 0xFF1A120A);
            int col = frac > 0.5F ? 0xFF6FD06A : frac > 0.25F ? 0xFFF2C94A : 0xFFE0503C;
            g.fill(x + 20, y + 12, x + 20 + (int) (68 * frac), y + 15, col);
            y += 24;
        }
        Ingredients.Meal meal = MealSystem.active(p);
        if (meal != null) {
            long ticks = MealSystem.remainingTicks(p);
            int secs = (int) (ticks / 20);
            String time = String.format("%d:%02d", secs / 60, secs % 60);
            g.fill(x - 2, y - 2, x + 92, y + 18, 0x80000000);
            int icon = Ingredients.DISH_IDS.indexOf(meal.dish().id());
            g.blit(VillageScreen.WIDGETS, x, y, 16, 16, (icon % 16) * 32, VillageScreen.DISH_V + (icon / 16) * 32, 32, 32,
                    VillageScreen.ATLAS, VillageScreen.ATLAS);
            String dn = meal.dish().displayName().getString();
            if (font.width(dn) > 66) dn = font.plainSubstrByWidth(dn, 64) + "…";
            g.drawString(font, dn, x + 19, y, 0xFFFFD46A, true);
            g.drawString(font, time, x + 19, y + 9, 0xFFD8C8A8, true);
        }
    }

    private static MofuEntity findMofu(Minecraft mc, LocalPlayer p) {
        long now = mc.level.getGameTime();
        if (mofuId >= 0) {
            Entity e = mc.level.getEntity(mofuId);
            if (e instanceof MofuEntity m && m.isAlive() && p.getUUID().equals(m.getSyncedOwner())) return m;
            mofuId = -1;
        }
        if (now - lastScan < 20 && now >= lastScan) return null;
        lastScan = now;
        for (MofuEntity m : mc.level.getEntitiesOfClass(MofuEntity.class, p.getBoundingBox().inflate(48.0D))) {
            if (p.getUUID().equals(m.getSyncedOwner())) {
                mofuId = m.getId();
                return m;
            }
        }
        return null;
    }
}
