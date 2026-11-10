package com.eternalrealm.ui.hud;

import com.eternalrealm.core.ModInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/**
 * Vanilla HUD elements the game replaces: the hotbar (the main hand always holds the equipped weapon and the number
 * keys use the quick-use items), hearts, armor, air, experience bar, selected item name and potion icons (all in the
 * player frame, see {@link PlayerFrameOverlay}). There is no hunger, so the food bar is never drawn. In creative and
 * spectator mode the vanilla HUD comes back ({@link HudFrame#mmo}).
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VanillaHud {
    /** Never drawn. */
    static final Set<ResourceLocation> NEVER = Set.of(VanillaGuiOverlay.FOOD_LEVEL.id());
    /** Replaced by the game's HUD (outside creative and spectator mode). */
    static final Set<ResourceLocation> REPLACED = Set.of(VanillaGuiOverlay.HOTBAR.id(),
            VanillaGuiOverlay.PLAYER_HEALTH.id(), VanillaGuiOverlay.ARMOR_LEVEL.id(), VanillaGuiOverlay.AIR_LEVEL.id(),
            VanillaGuiOverlay.EXPERIENCE_BAR.id(), VanillaGuiOverlay.ITEM_NAME.id(), VanillaGuiOverlay.POTION_ICONS.id());

    private VanillaHud() {
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        ResourceLocation id = event.getOverlay().id();
        if (NEVER.contains(id) || (REPLACED.contains(id) && HudFrame.mmo(Minecraft.getInstance().player))) {
            event.setCanceled(true);
        }
    }
}
