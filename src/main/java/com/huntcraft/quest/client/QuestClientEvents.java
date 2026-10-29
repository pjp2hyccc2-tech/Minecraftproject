package com.huntcraft.quest.client;

import com.huntcraft.HuntCraft;
import com.huntcraft.quest.QuestHudState;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client forge-bus events of the quest module: quest map key, HUD reset on disconnect. */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class QuestClientEvents {
    private QuestClientEvents() {
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (QuestClient.QUEST_MAP.consumeClick()) {
            if (mc.screen == null && mc.player != null && QuestHudState.active) mc.setScreen(new QuestMapScreen());
        }
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        QuestHudState.reset();
        QuestHudState.AREAS.clear();
    }
}
