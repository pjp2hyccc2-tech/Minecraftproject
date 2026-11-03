package com.hitori.client;

import com.hitori.Hitori;
import com.hitori.client.particle.HitoriParticles;
import com.hitori.client.render.CameraRenderer;
import com.hitori.client.render.Models;
import com.hitori.client.render.OniDollRenderer;
import com.hitori.client.render.WomanRenderer;
import com.hitori.client.sound.Acoustics;
import com.hitori.client.sound.AcousticsState;
import com.hitori.registry.ModEntities;
import com.hitori.registry.ModKeys;
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
@Mod.EventBusSubscriber(modid = Hitori.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ShaderPackInstaller.install();
            Acoustics.setState(ACOUSTICS);
        });
    }

    /**
     * What the acoustics engine (docs/DESIGN.md §10) asks of the game every client tick: how shut in you are (1 in a
     * closet, 0.5 peeking through the fusuma's crack) and whether the listener is a camera of the app (the engine
     * plays {@code camera.mic_hiss} itself then).
     */
    public static final AcousticsState ACOUSTICS = new AcousticsState() {
        @Override
        public float enclosure() {
            if (!ClientState.hiding()) return 0.0f;
            return Peek.active() ? 0.5f : 1.0f;
        }

        @Override
        public boolean cameraView() {
            return CameraApp.viewing();
        }
    };

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        Models.layers(event);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ONI_DOLL.get(), OniDollRenderer::new);
        event.registerEntityRenderer(ModEntities.WOMAN.get(), WomanRenderer::new);
        event.registerEntityRenderer(ModEntities.CAMERA.get(), CameraRenderer::new);
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent event) {
        HitoriParticles.register(event);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent event) {
        event.registerBelowAll("hitori_world", HorrorOverlay::renderWorld);
        event.registerAboveAll("hitori_hud", HorrorOverlay::renderHud);
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(ModKeys.BREATH);
        event.register(ModKeys.ACTION);
        event.register(ModKeys.CAMERA);
        event.register(ModKeys.PRINTOUT);
    }

    @SubscribeEvent
    public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> Tex.clear());
    }
}
