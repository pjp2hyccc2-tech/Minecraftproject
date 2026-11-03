package com.reisatsu.client;

import com.reisatsu.Reisatsu;
import com.reisatsu.client.particle.ReisatsuParticles;
import com.reisatsu.client.render.CarRenderer;
import com.reisatsu.client.render.MediumRenderer;
import com.reisatsu.client.render.Models;
import com.reisatsu.client.render.SpiritRenderer;
import com.reisatsu.registry.ModEntities;
import com.reisatsu.registry.ModKeys;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client registrations of the game (renderers, overlays, keys, particles). Owner: game. */
@Mod.EventBusSubscriber(modid = Reisatsu.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ShaderPackInstaller::install);
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        Models.layers(event);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.VISITOR.get(), c -> new SpiritRenderer<>(c, "visitor", "Visitor", 1.0f));
        event.registerEntityRenderer(ModEntities.WAITRESS.get(), c -> new SpiritRenderer<>(c, "waitress", "Waitress", 0.95f));
        event.registerEntityRenderer(ModEntities.CHEF.get(), c -> new SpiritRenderer<>(c, "chef", "Chef", 1.15f));
        event.registerEntityRenderer(ModEntities.CHILD.get(), c -> new SpiritRenderer<>(c, "child", "Child", 0.58f));
        event.registerEntityRenderer(ModEntities.MIRROR_WOMAN.get(), c -> new SpiritRenderer<>(c, "mirror_woman", "MirrorWoman", 0.95f));
        event.registerEntityRenderer(ModEntities.MANAGER.get(), c -> new SpiritRenderer<>(c, "manager", "Manager", 0.98f));
        event.registerEntityRenderer(ModEntities.MEDIUM.get(), MediumRenderer::new);
        event.registerEntityRenderer(ModEntities.MEDIUM_CAR.get(), CarRenderer::new);
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent event) {
        ReisatsuParticles.register(event);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent event) {
        event.registerBelowAll("reisatsu_world", HorrorOverlay::renderWorld);
        event.registerAboveAll("reisatsu_hud", HorrorOverlay::renderHud);
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(ModKeys.BREATH);
        event.register(ModKeys.ALBUM);
    }

    @SubscribeEvent
    public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> Tex.clear());
    }
}
