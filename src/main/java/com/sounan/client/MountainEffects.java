package com.sounan.client;

import com.sounan.Sounan;
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
 * The look of 帰らず山 without shaders: cold blue-grey fog, a pale moon high behind thin cloud, no clouds rendered,
 * no rain (the mod drives the rain level to carry fear to shader packs), a cold dark lightmap where the flashlight
 * is the only warm thing. With a shader pack in use the vanilla sky is kept so the pack can draw its own.
 */
public final class MountainEffects extends DimensionSpecialEffects {
    private static final ResourceLocation MOON = new ResourceLocation("minecraft", "textures/environment/moon_phases.png");

    public MountainEffects() {
        super(Float.NaN, false, SkyType.NONE, false, false);
    }

    @Override
    public SkyType skyType() {
        return ShaderCompat.active() ? SkyType.NORMAL : SkyType.NONE;
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return new Vec3(0.020, 0.026, 0.034);
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
        // high in the south-west, behind thin cloud
        poseStack.mulPose(Axis.YP.rotationDegrees(-30.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(28.0f));
        Matrix4f m = poseStack.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, MOON);
        float fear = ClientState.fearShown / 100.0f;
        RenderSystem.setShaderColor(0.42f - fear * 0.1f, 0.46f - fear * 0.1f, 0.55f - fear * 0.08f, 1.0f);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        float s = 16.0f;
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.vertex(m, -s, 100.0f, -s).uv(0.0f, 0.0f).endVertex();
        b.vertex(m, s, 100.0f, -s).uv(0.25f, 0.0f).endVertex();
        b.vertex(m, s, 100.0f, s).uv(0.25f, 0.5f).endVertex();
        b.vertex(m, -s, 100.0f, s).uv(0.0f, 0.5f).endVertex();
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
        // desaturate and cool the dark; the flashlight is the only warm light
        float lum = colors.x * 0.3f + colors.y * 0.59f + colors.z * 0.11f;
        float warm = block * block;
        float r = Mth.lerp(0.45f, colors.x, lum);
        float g = Mth.lerp(0.45f, colors.y, lum);
        float b = Mth.lerp(0.45f, colors.z, lum);
        r *= Mth.lerp(warm, 0.70f, 1.08f);
        g *= Mth.lerp(warm, 0.76f, 0.96f);
        b *= Mth.lerp(warm, 0.95f, 0.78f);
        float dark = 1.0f - 0.2f * fear;
        // the darkness is deep but never a flat black: a cold floor of light
        r += 0.010f;
        g += 0.013f;
        b += 0.022f;
        // moonlight where the canopy opens: cold and faint
        float moon = sky * sky * sky * Math.max(0.0f, 1.0f - block);
        r += moon * 0.05f;
        g += moon * 0.065f;
        b += moon * 0.11f;
        colors.set(Mth.clamp(r * dark, 0.0f, 1.0f), Mth.clamp(g * dark, 0.0f, 1.0f), Mth.clamp(b * dark, 0.0f, 1.0f));
    }
}
