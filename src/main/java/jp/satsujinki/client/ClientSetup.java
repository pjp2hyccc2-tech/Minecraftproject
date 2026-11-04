package jp.satsujinki.client;

import jp.satsujinki.Satsujinki;
import jp.satsujinki.client.gui.HudOverlay;
import jp.satsujinki.client.particle.BloodDropParticle;
import jp.satsujinki.client.particle.BloodMistParticle;
import jp.satsujinki.client.particle.BloodSplatParticle;
import jp.satsujinki.client.particle.GoreBitParticle;
import jp.satsujinki.client.render.ContestantRenderer;
import jp.satsujinki.client.render.CorpseRenderer;
import jp.satsujinki.client.render.GibRenderer;
import jp.satsujinki.client.render.GuillotineRenderer;
import jp.satsujinki.client.render.MixerRenderer;
import jp.satsujinki.client.render.ModModelLayers;
import jp.satsujinki.client.render.PressRenderer;
import jp.satsujinki.client.render.SawRenderer;
import jp.satsujinki.registry.ModBlockEntities;
import jp.satsujinki.registry.ModEntities;
import jp.satsujinki.registry.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Satsujinki.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CONTESTANT.get(), ContestantRenderer::new);
        event.registerEntityRenderer(ModEntities.CORPSE.get(), CorpseRenderer::new);
        event.registerEntityRenderer(ModEntities.GIB.get(), GibRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MIXER.get(), MixerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PRESS.get(), PressRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SAW.get(), SawRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GUILLOTINE.get(), GuillotineRenderer::new);
    }

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ModModelLayers.MIXER_BLADES, ModModelLayers::mixerBlades);
        event.registerLayerDefinition(ModModelLayers.PRESS_PLATE, ModModelLayers::pressPlate);
        event.registerLayerDefinition(ModModelLayers.PRESS_ROD, ModModelLayers::pressRod);
        event.registerLayerDefinition(ModModelLayers.GUILLOTINE, ModModelLayers::guillotine);
        event.registerLayerDefinition(ModModelLayers.GIB, ModModelLayers::gib);
    }

    @SubscribeEvent
    public static void onParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.BLOOD_DROP.get(), BloodDropParticle.Provider::new);
        event.registerSpriteSet(ModParticles.BLOOD_SPLAT.get(), BloodSplatParticle.Provider::new);
        event.registerSpriteSet(ModParticles.BLOOD_MIST.get(), BloodMistParticle.Provider::new);
        event.registerSpriteSet(ModParticles.GORE_BIT.get(), GoreBitParticle.Provider::new);
    }

    @SubscribeEvent
    public static void onOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("satsujinki_hud", HudOverlay::render);
    }
}
