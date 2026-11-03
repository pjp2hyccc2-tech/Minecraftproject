package com.hitori.client;

import com.hitori.Hitori;
import com.hitori.world.Layout;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Client registrations of the apartment (the dimension's sky and fog). Owner: world. */
@Mod.EventBusSubscriber(modid = Hitori.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WorldClientSetup {
    private WorldClientSetup() {}

    @SubscribeEvent
    public static void effects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(Hitori.id("shirakaba"), new ShirakabaEffects());
    }

    /**
     * The rainy night over コーポ白樺 without a shader pack: an overcast sky with no moon or stars (the sky is the dark
     * blue-grey fog), no clouds, and never vanilla rain or snow (the mod drives the rain level as the fear channel for
     * shader packs; the rain is drawn on the windows by the pack and heard through our sounds). With a pack active the
     * vanilla sky is kept so the pack can draw its own.
     */
    public static final class ShirakabaEffects extends DimensionSpecialEffects {
        private static final Vec3 FOG = new Vec3(0.055, 0.062, 0.078);

        public ShirakabaEffects() {
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
            return false;
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

    /**
     * The street's rainy fog: dark blue-grey, from near to a short distance (the street vanishes into it both ways;
     * the building stays readable from the street). The game may tighten it further (fear).
     */
    @Mod.EventBusSubscriber(modid = Hitori.MODID, value = Dist.CLIENT)
    public static final class Fog {
        private Fog() {}

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void fog(ViewportEvent.RenderFog event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.level.dimension() != Layout.DIMENSION) return;
            if (ShaderCompat.active()) return;
            if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;
            float far = Math.min(event.getFarPlaneDistance(), 72.0f);
            event.setNearPlaneDistance(Math.min(6.0f, far * 0.1f));
            event.setFarPlaneDistance(far);
            event.setCanceled(true);
        }
    }
}
