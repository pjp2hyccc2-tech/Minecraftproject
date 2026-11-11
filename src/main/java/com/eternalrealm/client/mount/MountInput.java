package com.eternalrealm.client.mount;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.mount.MountRules;
import com.eternalrealm.mount.forge.MountEntity;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The mount keys (the server decides what happens: {@link MountRules#TOGGLE_ACTION}, {@link MountRules#CHOOSE_ACTION})
 * and the vanilla mount hearts, which a mount that cannot be hurt does not need.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MountInput {
    private MountInput() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        while (MountClient.CALL.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                ERNetwork.sendToServer(new UiActionPacket(MountRules.TOGGLE_ACTION, new JsonObject()));
            }
        }
        while (MountClient.CHOOSE.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                ERNetwork.sendToServer(new UiActionPacket(MountRules.CHOOSE_ACTION, new JsonObject()));
            }
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getOverlay().id().equals(VanillaGuiOverlay.MOUNT_HEALTH.id()) && minecraft.player != null
                && minecraft.player.getVehicle() instanceof MountEntity) {
            event.setCanceled(true);
        }
    }
}
