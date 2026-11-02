package com.sounan.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sounan.Sounan;
import com.sounan.client.model.KoyanushiModel;
import com.sounan.client.model.NozokiModel;
import com.sounan.client.model.OyamasamaModel;
import com.sounan.client.model.UshiroModel;
import com.sounan.client.model.UtsurumonoModel;
import com.sounan.client.model.YobigoeModel;
import com.sounan.client.render.MonsterRenderer;
import com.sounan.entity.Koyanushi;
import com.sounan.entity.Nozoki;
import com.sounan.entity.Oyamasama;
import com.sounan.entity.Ushiro;
import com.sounan.entity.Utsurumono;
import com.sounan.entity.Yobigoe;
import com.sounan.registry.ModEntities;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Sounan.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    public static final KeyMapping PHONE_KEY = new KeyMapping("key.sounan.phone", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P,
            "key.categories.sounan");
    public static final KeyMapping MEMO_KEY = new KeyMapping("key.sounan.memo", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J,
            "key.categories.sounan");

    private ClientSetup() {}

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ShaderPackInstaller::install);
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(NozokiModel.LAYER, NozokiModel::createBodyLayer);
        event.registerLayerDefinition(YobigoeModel.LAYER, YobigoeModel::createBodyLayer);
        event.registerLayerDefinition(UshiroModel.LAYER, UshiroModel::createBodyLayer);
        event.registerLayerDefinition(KoyanushiModel.LAYER, KoyanushiModel::createBodyLayer);
        event.registerLayerDefinition(OyamasamaModel.LAYER, OyamasamaModel::createBodyLayer);
        event.registerLayerDefinition(UtsurumonoModel.LAYER, UtsurumonoModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.NOZOKI.get(), c -> new MonsterRenderer<Nozoki, NozokiModel<Nozoki>>(
                c, new NozokiModel<Nozoki>(c.bakeLayer(NozokiModel.LAYER)), "nozoki", 0.5f));
        event.registerEntityRenderer(ModEntities.YOBIGOE.get(), c -> new MonsterRenderer<Yobigoe, YobigoeModel<Yobigoe>>(
                c, new YobigoeModel<Yobigoe>(c.bakeLayer(YobigoeModel.LAYER)), "yobigoe", 0.4f));
        event.registerEntityRenderer(ModEntities.USHIRO.get(), c -> new MonsterRenderer<Ushiro, UshiroModel<Ushiro>>(
                c, new UshiroModel<Ushiro>(c.bakeLayer(UshiroModel.LAYER)), "ushiro", 0.4f));
        event.registerEntityRenderer(ModEntities.KOYANUSHI.get(), c -> new MonsterRenderer<Koyanushi, KoyanushiModel<Koyanushi>>(
                c, new KoyanushiModel<Koyanushi>(c.bakeLayer(KoyanushiModel.LAYER)), "koyanushi", 0.4f));
        event.registerEntityRenderer(ModEntities.OYAMASAMA.get(), c -> new MonsterRenderer<Oyamasama, OyamasamaModel<Oyamasama>>(
                c, new OyamasamaModel<Oyamasama>(c.bakeLayer(OyamasamaModel.LAYER)), "oyamasama", 0.9f));
        event.registerEntityRenderer(ModEntities.UTSURUMONO.get(), c -> new MonsterRenderer<Utsurumono, UtsurumonoModel<Utsurumono>>(
                c, new UtsurumonoModel<Utsurumono>(c.bakeLayer(UtsurumonoModel.LAYER)), "utsurumono", 0.0f));
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent event) {
        event.registerBelowAll("sounan_world", HorrorOverlay::renderWorld);
        event.registerAboveAll("sounan_hud", HorrorOverlay::renderHud);
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(PHONE_KEY);
        event.register(MEMO_KEY);
    }

    @SubscribeEvent
    public static void dimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(Sounan.id("kaerazu"), new MountainEffects());
    }
}
