package com.eternalrealm.client.map;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.ui.map.AtlasTexture;
import com.eternalrealm.ui.map.WorldMapScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client game-bus handlers of the map: its keys, the minimap's per-tick work, and forgetting a world on leaving. */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MapClientEvents {
    private MapClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        while (MapKeys.WORLD_MAP.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                WorldMapScreen.open();
            }
        }
        while (MapKeys.MINIMAP_TOGGLE.consumeClick()) {
            Minimap.toggle();
        }
        while (MapKeys.MINIMAP_ZOOM_IN.consumeClick()) {
            Minimap.zoom(-1);
        }
        while (MapKeys.MINIMAP_ZOOM_OUT.consumeClick()) {
            Minimap.zoom(1);
        }
        Minimap.tick(minecraft);
        AtlasTexture.tick();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        Minimap.clear();
        ClientMapState.clear();
    }
}
