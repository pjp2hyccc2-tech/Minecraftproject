package com.sounan.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sounan.Sounan;
import com.sounan.client.ClientPacketHandler;
import com.sounan.registry.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** The paper trail map from the bag: wet, torn at a fold, no "you are here". */
public class TrailMapScreen extends Screen {
    private static final ResourceLocation MAP = Sounan.id("textures/gui/trail_map.png");
    private boolean played;

    public TrailMapScreen() {
        super(Component.translatable("item.sounan.trail_map"));
    }

    @Override
    protected void init() {
        if (!played) {
            ClientPacketHandler.ui(ModSounds.ITEM_MAP.get(), 0.7f, 1.0f);
            played = true;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        int s = Math.min(width - 20, height - 20);
        int x = (width - s) / 2, y = (height - s) / 2;
        RenderSystem.enableBlend();
        g.setColor(0.82f, 0.80f, 0.76f, 1.0f);
        g.blit(MAP, x, y, s, s, 0, 0, 512, 512, 512, 512);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        onClose();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
