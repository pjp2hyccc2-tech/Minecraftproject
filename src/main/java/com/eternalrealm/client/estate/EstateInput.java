package com.eternalrealm.client.estate;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.estate.RecallRules;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.eternalrealm.ui.estate.RecallOverlay;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The room key: asks the server to start the way home, or to stop it while it runs. */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class EstateInput {
    private EstateInput() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        while (EstateClient.RECALL.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                recall();
            }
        }
    }

    /** Starts the way home (or stops it while it runs); also the journal's "my room" button. */
    public static void recall() {
        JsonObject args = new JsonObject();
        if (RecallOverlay.casting()) {
            args.addProperty("cancel", true);
        }
        ERNetwork.sendToServer(new UiActionPacket(RecallRules.ACTION, args));
    }
}
