package com.huntcraft.monster.small.client;

import com.huntcraft.HuntCraft;
import com.huntcraft.client.model.HcModels;
import com.huntcraft.client.render.HcMonsterRenderer;
import com.huntcraft.monster.small.SmallMonsters;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client registration of the small monsters (model layers + renderers). */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SmallMonstersClient {
    private static final String[] MODELS = {
            "jagraptor", "blizraptor", "boarfang", "grasica", "gamenos", "buzzling", "yukimammo", "heatgecko", "gansekigani", "nussutto_mofu"
    };

    private SmallMonstersClient() {
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (String m : MODELS) HcModels.register(event, m);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SmallMonsters.JAGRAPTOR.get(), ctx -> new HcMonsterRenderer<>(ctx, "jagraptor", 0.5F));
        event.registerEntityRenderer(SmallMonsters.BLIZRAPTOR.get(), ctx -> new HcMonsterRenderer<>(ctx, "blizraptor", 0.5F));
        event.registerEntityRenderer(SmallMonsters.BOARFANG.get(), ctx -> new HcMonsterRenderer<>(ctx, "boarfang", 0.6F));
        event.registerEntityRenderer(SmallMonsters.GRASICA.get(), ctx -> new HcMonsterRenderer<>(ctx, "grasica", 0.5F));
        event.registerEntityRenderer(SmallMonsters.GAMENOS.get(), ctx -> new HcMonsterRenderer<>(ctx, "gamenos", 1.1F));
        event.registerEntityRenderer(SmallMonsters.BUZZLING.get(), ctx -> new HcMonsterRenderer<>(ctx, "buzzling", 0.4F));
        event.registerEntityRenderer(SmallMonsters.YUKIMAMMO.get(), ctx -> new HcMonsterRenderer<>(ctx, "yukimammo", 1.2F));
        event.registerEntityRenderer(SmallMonsters.HEATGECKO.get(), ctx -> new HcMonsterRenderer<>(ctx, "heatgecko", 0.45F));
        event.registerEntityRenderer(SmallMonsters.GANSEKIGANI.get(), ctx -> new HcMonsterRenderer<>(ctx, "gansekigani", 0.7F));
        event.registerEntityRenderer(SmallMonsters.NUSSUTTO_MOFU.get(), MofuRenderer::new);
    }
}
