package com.eternalrealm.ui.hud;

import com.eternalrealm.core.ModInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/**
 * Vanilla HUD elements the game replaces or has no use for. There is no hunger (the server keeps the food bar full,
 * see {@code player.forge.Vitals}), so the food bar is never drawn.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VanillaHud {
    /** Overlays that are never drawn. */
    static final Set<ResourceLocation> HIDDEN = Set.of(VanillaGuiOverlay.FOOD_LEVEL.id());

    private VanillaHud() {
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (HIDDEN.contains(event.getOverlay().id())) {
            event.setCanceled(true);
        }
    }
}
