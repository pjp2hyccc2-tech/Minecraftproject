package com.eternalrealm.client;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.job.fx.SkillShow;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * The screen of a finale (秘奥義) for the one performing it ({@link FinaleClient#screen}): the edges darkened in the
 * finale's colour, cinema bars sliding in at the top and the bottom, and its flashes over everything. Drawn under the
 * HUD (the skill bar and the player frame stay readable); the zoom is the field of view ({@link FinaleClient}).
 */
public final class FinaleCinema implements IGuiOverlay {
    private static final ResourceLocation VIGNETTE =
            new ResourceLocation(ModInfo.MOD_ID, "textures/gui/finale_vignette.png");
    private static final int VIGNETTE_SIZE = 256;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        SkillShow.ScreenState s = FinaleClient.screen(partialTick);
        if (s.none()) {
            return;
        }
        if (s.vignette() > 0.004F) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            int rgb = s.vignetteRgb();
            graphics.setColor(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F,
                    Math.min(1.0F, s.vignette()));
            graphics.blit(VIGNETTE, 0, 0, width, height, 0.0F, 0.0F, VIGNETTE_SIZE, VIGNETTE_SIZE, VIGNETTE_SIZE,
                    VIGNETTE_SIZE);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }
        if (s.bars() > 0.001F) {
            int bar = Math.round(height * s.bars());
            graphics.fill(0, 0, width, bar, 0xFF000000);
            graphics.fill(0, height - bar, width, height, 0xFF000000);
        }
        if (s.flash() > 0.004F) {
            int alpha = Math.round(255.0F * Math.min(0.9F, s.flash()));
            graphics.fill(0, 0, width, height, (alpha << 24) | (s.flashRgb() & 0xFFFFFF));
        }
    }
}
