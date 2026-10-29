package com.huntcraft.monster.large.a.client;

import com.huntcraft.HuntCraft;
import com.huntcraft.client.model.HcModels;
import com.huntcraft.monster.large.a.LargeMonstersA;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client registration for Frostgarm and Voltrix (model layers + renderers with custom emissive layers). */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LargeMonstersAClient {
    private LargeMonstersAClient() {
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        HcModels.register(event, "frostgarm");
        HcModels.register(event, "voltrix");
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(LargeMonstersA.FROSTGARM.get(), ctx -> new PulseGlowRenderer<>(ctx, "frostgarm", 2.4F, PulseGlowRenderer.Style.FROST));
        event.registerEntityRenderer(LargeMonstersA.VOLTRIX.get(), ctx -> new PulseGlowRenderer<>(ctx, "voltrix", 2.2F, PulseGlowRenderer.Style.VOLT));
    }
}
