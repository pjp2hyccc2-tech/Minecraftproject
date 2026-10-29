package com.huntcraft.monster.large.b.client;

import com.huntcraft.HuntCraft;
import com.huntcraft.client.model.HcModels;
import com.huntcraft.client.render.HcMonsterRenderer;
import com.huntcraft.monster.large.b.LargeMonstersB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client registration for Gravadon + Azragos (hcmodel layers and renderers). */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LargeMonstersBClient {
    private LargeMonstersBClient() {
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        HcModels.register(event, "gravadon");
        HcModels.register(event, "azragos");
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(LargeMonstersB.GRAVADON.get(), ctx -> new HcMonsterRenderer<>(ctx, "gravadon", 2.4F));
        event.registerEntityRenderer(LargeMonstersB.AZRAGOS.get(), ctx -> new HcMonsterRenderer<>(ctx, "azragos", 2.8F));
    }
}
