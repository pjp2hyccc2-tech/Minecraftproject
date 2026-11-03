package com.reisatsu.client;

import com.reisatsu.Reisatsu;
import com.reisatsu.client.render.block.AutoDoorRenderer;
import com.reisatsu.client.render.block.SwingDoorRenderer;
import com.reisatsu.registry.ModBlockEntities;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Client registrations of the restaurant (block entity renderers, the dimension's sky and fog). Owner: world. */
@Mod.EventBusSubscriber(modid = Reisatsu.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WorldClientSetup {
    private WorldClientSetup() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.AUTO_DOOR.get(), AutoDoorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SWING_DOOR.get(), SwingDoorRenderer::new);
    }

    @SubscribeEvent
    public static void effects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(Reisatsu.id("hidamari"), new HidamariEffects());
    }

    /**
     * The night over the restaurant without a shader pack: an overcast sky with no moon or stars (the sky is the
     * dark blue-grey fog), fog at mid distance, no clouds, never rain or snow (the mod drives the rain level as the
     * fear channel for shader packs). With a pack active the vanilla sky is kept so the pack can draw its own.
     */
    public static final class HidamariEffects extends DimensionSpecialEffects {
        private static final Vec3 FOG = new Vec3(0.072, 0.082, 0.105);

        public HidamariEffects() {
            super(Float.NaN, false, SkyType.NONE, false, false);
        }

        @Override
        public SkyType skyType() {
            return ShaderCompat.active() ? SkyType.NORMAL : SkyType.NONE;
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
            return FOG;
        }

        @Override
        public boolean isFoggyAt(int x, int y) {
            return true;
        }

        @Override
        public float[] getSunriseColor(float timeOfDay, float partialTicks) {
            return null;
        }

        @Override
        public boolean renderClouds(ClientLevel level, int ticks, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack, double camX,
                                    double camY, double camZ, Matrix4f projectionMatrix) {
            return true;
        }

        @Override
        public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY,
                                         double camZ) {
            return true;
        }

        @Override
        public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
            return true;
        }
    }
}
