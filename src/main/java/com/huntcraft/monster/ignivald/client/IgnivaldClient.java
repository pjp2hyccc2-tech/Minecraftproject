package com.huntcraft.monster.ignivald.client;

import com.huntcraft.HuntCraft;
import com.huntcraft.client.model.HcModels;
import com.huntcraft.client.render.HcMonsterRenderer;
import com.huntcraft.monster.ignivald.IgnivaldModule;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class IgnivaldClient {
    private IgnivaldClient() {
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        HcModels.register(event, "ignivald");
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(IgnivaldModule.IGNIVALD.get(), ctx -> new HcMonsterRenderer<>(ctx, "ignivald", 2.6F));
    }
}
