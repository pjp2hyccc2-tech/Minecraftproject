package com.eternalrealm.client.market;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The auction hall key: asks the server to show the hall (it answers with the hall's state and opens the screen). */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MarketInput {
    private MarketInput() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        while (MarketClient.OPEN_AUCTION.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                ERNetwork.sendToServer(new UiActionPacket("auction.open", new JsonObject()));
            }
        }
    }
}
