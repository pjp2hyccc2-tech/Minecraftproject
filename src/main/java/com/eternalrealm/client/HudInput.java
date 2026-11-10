package com.eternalrealm.client;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.ui.hud.HudFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Input that goes with the HUD (outside creative and spectator mode): the main hand stays on inventory slot 0 (the
 * equipped weapon), the number keys use the quick-use items instead of choosing a hotbar slot, the mouse wheel no
 * longer scrolls through a hotbar and the drop key does not throw the weapon away. The equipment key opens the
 * equipment screen in every mode.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HudInput {
    private HudInput() {
    }

    /** Before vanilla reads the keys this tick, so a number key never selects a hotbar slot. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (event.phase != TickEvent.Phase.START || player == null) {
            return;
        }
        if (minecraft.screen == null) {
            while (ClientKeyMappings.OPEN_EQUIPMENT.consumeClick()) {
                ClientScreens.openEquipment();
            }
        }
        if (!HudFrame.mmo(player)) {
            return;
        }
        if (minecraft.screen == null) {
            for (int i = 0; i < minecraft.options.keyHotbarSlots.length; i++) {
                while (minecraft.options.keyHotbarSlots[i].consumeClick()) {
                    if (i < QuickSlots.COUNT) {
                        QuickSlots.use(i);
                    }
                }
            }
            // the drop key would throw the equipped weapon away: weapons leave the hand only through the bag
            if (com.eternalrealm.combat.forge.WeaponItem.profileOf(player.getMainHandItem()) != null) {
                while (minecraft.options.keyDrop.consumeClick()) {
                    // swallowed
                }
            }
        }
        if (player.getInventory().selected != 0) {
            player.getInventory().selected = 0; // vanilla tells the server on its next tick
        }
    }

    /** No hotbar to scroll through. */
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null && HudFrame.mmo(minecraft.player)) {
            event.setCanceled(true);
        }
    }
}
