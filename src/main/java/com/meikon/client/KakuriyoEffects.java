package com.meikon.client;

import com.meikon.Meikon;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The look of 隠り世 without shaders: crimson-black fog, a blood-red moon at the zenith, no clouds, no rain
 * (the mod drives the rain level to carry fear to shader packs), a cold dark lightmap with warm lamp light.
 * With a shader pack in use the vanilla sky is kept so the pack can colour the moon itself.
 */
public final class KakuriyoEffects extends DimensionSpecialEffects {
    private static final ResourceLocation MOON = Meikon.id("textures/environment/red_moon.png");

    public KakuriyoEffects() {
        super(Float.NaN, false, SkyType.NONE, false, false);
    }

    @Override
    public SkyType skyType() {
        return ShaderCompat.active() ? SkyType.NORMAL : SkyType.NONE;
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return new Vec3(0.032, 0.012, 0.016);
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
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY, double camZ,
                                Matrix4f projectionMatrix) {
        return true;
    }

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
        return true;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Camera camera, Matrix4f projectionMatrix,
                             boolean isFoggy, Runnable setupFog) {
        if (ShaderCompat.active()) return false;
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-30.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(-32.0f));
        Matrix4f m = poseStack.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, MOON);
        float fear = ClientState.fearShown / 100.0f;
        RenderSystem.setShaderColor(1.0f, 0.85f - fear * 0.25f, 0.85f - fear * 0.25f, 1.0f);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        float s = 34.0f;
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.vertex(m, -s, 100.0f, -s).uv(0.0f, 0.0f).endVertex();
        b.vertex(m, s, 100.0f, -s).uv(1.0f, 0.0f).endVertex();
        b.vertex(m, s, 100.0f, s).uv(1.0f, 1.0f).endVertex();
        b.vertex(m, -s, 100.0f, s).uv(0.0f, 1.0f).endVertex();
        BufferUploader.drawWithShader(b.end());
        poseStack.popPose();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        return true;
    }

    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker, float skyLight,
                                     int pixelX, int pixelY, Vector3f colors) {
        float block = pixelX / 15.0f;
        float sky = pixelY / 15.0f;
        float fear = ClientState.fearShown / 100.0f;
        // desaturate and cool the dark, warm the lamp light
        float lum = colors.x * 0.3f + colors.y * 0.59f + colors.z * 0.11f;
        float warm = block * block;
        float r = Mth.lerp(0.35f, colors.x, lum);
        float g = Mth.lerp(0.35f, colors.y, lum);
        float b = Mth.lerp(0.35f, colors.z, lum);
        r *= Mth.lerp(warm, 0.80f, 1.10f);
        g *= Mth.lerp(warm, 0.84f, 0.88f);
        b *= Mth.lerp(warm, 1.02f, 0.62f);
        float dark = 1.0f - 0.22f * fear;
        // moonlight stays faint and blue
        if (sky > block) {
            b += 0.02f * sky;
        }
        colors.set(Mth.clamp(r * dark, 0.0f, 1.0f), Mth.clamp(g * dark, 0.0f, 1.0f), Mth.clamp(b * dark, 0.0f, 1.0f));
    }
}
