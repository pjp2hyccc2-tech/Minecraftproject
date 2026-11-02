package com.meikon.client;

import com.meikon.Meikon;
import com.meikon.client.render.HanayomeModel;
import com.meikon.client.render.HanayomeRenderer;
import com.meikon.client.render.IchimatsuModel;
import com.meikon.client.render.IchimatsuRenderer;
import com.meikon.client.render.KageboshiRenderer;
import com.meikon.client.render.MaboroshiRenderer;
import com.meikon.registry.ModEntities;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Meikon.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    public static final KeyMapping JOURNAL_KEY = new KeyMapping("key.meikon.journal", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J,
            "key.categories.meikon");

    private ClientSetup() {}

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ShaderPackInstaller::install);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.HANAYOME.get(), HanayomeRenderer::new);
        event.registerEntityRenderer(ModEntities.ICHIMATSU.get(), IchimatsuRenderer::new);
        event.registerEntityRenderer(ModEntities.KAGEBOSHI.get(), KageboshiRenderer::new);
        event.registerEntityRenderer(ModEntities.MABOROSHI.get(), MaboroshiRenderer::new);
        event.registerEntityRenderer(ModEntities.OHAJIKI.get(), ThrownItemRenderer::new);
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(HanayomeModel.LAYER, HanayomeModel::createLayer);
        event.registerLayerDefinition(IchimatsuModel.LAYER, IchimatsuModel::createLayer);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent event) {
        event.registerBelowAll("meikon_world", HorrorOverlay::renderWorld);
        event.registerAboveAll("meikon_hud", HorrorOverlay::renderHud);
        event.registerAboveAll("meikon_cinematic", HorrorOverlay::renderCinematic);
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(JOURNAL_KEY);
    }

    @SubscribeEvent
    public static void dimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(Meikon.id("kakuriyo"), new KakuriyoEffects());
    }
}
