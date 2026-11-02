package com.sounan.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sounan.Sounan;
import com.sounan.client.ShaderCompat;
import com.sounan.entity.Oyamasama;
import com.sounan.entity.Yobigoe;
import com.sounan.registry.ModDimensions;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Lights that carry through the fog further than the fog lets the models show (only without a shader pack, which
 * draws these itself): the rescuer's headlamp waving far off between the trunks, and a pair of dim red points at
 * the height of something far too tall, watching from beyond where you can see. Trunks hide the lights
 * themselves, but not the faint glow a lamp makes in the fog around it, so you can tell where it is.
 */
@Mod.EventBusSubscriber(modid = Sounan.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FarLights {
    private static final int SEGMENTS = 12;

    private FarLights() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !ModDimensions.isMountain(mc.level) || ShaderCompat.active()) return;
        Camera cam = event.getCamera();
        Matrix4f m = event.getPoseStack().last().pose();
        // the lights themselves (trunks hide them), then the glow they make in the fog (seen around the trunks)
        draw(mc, cam, m, event.getPartialTick(), false);
        draw(mc, cam, m, event.getPartialTick(), true);
    }

    private static void draw(Minecraft mc, Camera cam, Matrix4f m, float partial, boolean fogGlow) {
        Vec3 cp = cam.getPosition();
        Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        boolean any = false;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.isInvisible()) continue;
            Vec3 at = e.getPosition(partial);
            double d = at.distanceTo(cp);
            if (d > 96) continue;
            float yaw = Mth.lerp(partial, e.yRotO, e.getYRot()) * Mth.DEG_TO_RAD;
            float fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
            if (e instanceof Yobigoe y) {
                if (y.pose() == Yobigoe.RUSH || flicker(e.tickCount + partial)) continue;
                // headlamp: 1.86 up, a little in front of the face
                Vec3 lamp = at.add(fx * 0.3, 1.86, fz * 0.3).subtract(cp);
                float far = (float) Math.exp(-d / 70.0);
                if (fogGlow) {
                    float a = smooth((float) (d - 12.0) / 10.0f) * far;
                    if (a < 0.01f) continue;
                    if (!any) begin(bb);
                    any = true;
                    float grow = (float) Math.max(1.0, d / 30.0);
                    fan(bb, m, lamp, left, up, 3.6f * grow, 1.0f, 0.9f, 0.72f, 0.15f * a);
                } else {
                    float a = smooth((float) (d - 8.0) / 10.0f) * far; // close up the model's own glow takes over
                    if (a < 0.01f) continue;
                    if (!any) begin(bb);
                    any = true;
                    float grow = (float) Math.max(1.0, d / 22.0); // stays a visible point far away
                    fan(bb, m, lamp, left, up, 0.22f * grow, 1.0f, 0.98f, 0.9f, 0.95f * a);
                    fan(bb, m, lamp, left, up, 1.6f * grow, 1.0f, 0.93f, 0.78f, 0.22f * a);
                }
            } else if (e instanceof Oyamasama o && !fogGlow) {
                float h = o.pose() == Oyamasama.CRAWL ? 1.25f : 3.78f;
                float a = smooth((float) (d - 16.0) / 10.0f) * (float) Math.exp(-d / 80.0)
                        * (0.8f + 0.2f * Mth.sin((e.tickCount + partial) * 0.07f));
                if (a < 0.01f) continue;
                if (!any) begin(bb);
                any = true;
                float grow = (float) Math.max(1.0, d / 30.0);
                for (int side = -1; side <= 1; side += 2) {
                    Vec3 eye = at.add(fx * 0.26 + fz * 0.13 * side, h, fz * 0.26 - fx * 0.13 * side).subtract(cp);
                    fan(bb, m, eye, left, up, 0.07f * grow, 0.75f, 0.12f, 0.07f, 0.9f * a);
                    fan(bb, m, eye, left, up, 0.3f * grow, 0.6f, 0.06f, 0.03f, 0.25f * a);
                }
            }
        }
        if (!any) return;
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        if (fogGlow) RenderSystem.disableDepthTest();
        else RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void begin(BufferBuilder bb) {
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
    }

    /** A camera-facing disc, bright in the middle and fading to nothing at the rim. */
    private static void fan(BufferBuilder bb, Matrix4f m, Vec3 c, Vector3f left, Vector3f up, float r,
                            float red, float green, float blue, float alpha) {
        float cx = (float) c.x, cy = (float) c.y, cz = (float) c.z;
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SEGMENTS, a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            float c0 = Mth.cos(a0) * r, s0 = Mth.sin(a0) * r, c1 = Mth.cos(a1) * r, s1 = Mth.sin(a1) * r;
            bb.vertex(m, cx, cy, cz).color(red, green, blue, alpha).endVertex();
            bb.vertex(m, cx + left.x() * c0 + up.x() * s0, cy + left.y() * c0 + up.y() * s0, cz + left.z() * c0 + up.z() * s0)
                    .color(red, green, blue, 0.0f).endVertex();
            bb.vertex(m, cx + left.x() * c1 + up.x() * s1, cy + left.y() * c1 + up.y() * s1, cz + left.z() * c1 + up.z() * s1)
                    .color(red, green, blue, 0.0f).endVertex();
        }
    }

    private static float smooth(float x) {
        float c = Mth.clamp(x, 0.0f, 1.0f);
        return c * c * (3.0f - 2.0f * c);
    }

    /** The lamp gutters now and then (the same beat as the model's). */
    private static boolean flicker(float t) {
        float m = (t + 7.0f) % 151.0f;
        if (m >= 3.0f) return false;
        float k = 1.0f - m / 3.0f;
        return k > 0.34f && k < 0.67f;
    }
}
