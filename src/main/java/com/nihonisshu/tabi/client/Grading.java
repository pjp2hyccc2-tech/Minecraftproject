package com.nihonisshu.tabi.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nihonisshu.tabi.registry.TabiWorldgen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LightLayer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Built-in "scene shader": a full-screen colour grade that leans cool (fresh mornings, sea breeze, rain, moonlit
 * nights) or warm (golden evening, lamp-lit rooms) depending on the scene. Key O cycles 自動 → 暖 → 涼 → オフ.
 */
public final class Grading {
    private Grading() {}

    public static ShaderInstance shader;
    private static RenderTarget copy;
    private static int mode; // 0 auto, 1 warm, 2 cool, 3 off
    private static float warmth, saturation = 1f, vignette = 0.2f, contrast = 0.15f;
    private static float tWarmth, tSaturation = 1f, tVignette = 0.2f, tContrast = 0.15f;
    private static boolean failed;

    private static final String[] NAMES = {"自動（場面にあわせて）", "暖かい色", "涼しい色", "オフ"};

    public static void cycle(Minecraft mc) {
        mode = (mode + 1) % 4;
        mc.gui.setOverlayMessage(Component.literal("色調モード：" + NAMES[mode]), false);
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        boolean japan = mc.level.dimension() == TabiWorldgen.JAPAN;
        float w = 0, sat = 1.05f, vig = 0.22f, con = 0.15f;
        if (mode == 1) {
            w = 0.75f;
            sat = 1.08f;
        } else if (mode == 2) {
            w = -0.7f;
            sat = 0.95f;
        } else if (mode == 0 && japan) {
            long t = Math.floorMod(mc.level.getDayTime(), 24000L);
            float hour = ((t / 1000f) + 6f) % 24f;
            // time of day curve
            if (hour >= 4.5f && hour < 6.5f) w = 0.35f;              // dawn: pink-gold
            else if (hour >= 6.5f && hour < 10f) w = -0.30f;         // fresh morning
            else if (hour >= 10f && hour < 15f) w = 0.05f;           // summer noon
            else if (hour >= 15f && hour < 16.5f) w = 0.35f;
            else if (hour >= 16.5f && hour < 19f) w = 0.85f;         // golden evening
            else w = -0.55f;                                        // blue night
            BlockPos p = BlockPos.containing(mc.player.getEyePosition());
            int sky = mc.level.getBrightness(LightLayer.SKY, p);
            int block = mc.level.getBrightness(LightLayer.BLOCK, p);
            if (sky < 6 && block >= 8) w = Math.max(w, 0.0f) * 0.4f + 0.55f;    // indoors under lamps
            String biome = mc.level.getBiome(p).unwrapKey().map(k -> k.location().getPath()).orElse("");
            if (biome.equals("seto_sea") && sky >= 6) w -= 0.2f;                  // sea breeze
            float rain = mc.level.getRainLevel(1f);
            w = w * (1 - rain) + (-0.55f) * rain;
            sat = 1.07f - 0.3f * rain;
            if (hour >= 19f || hour < 4.5f) {
                vig = 0.32f;
                if (biome.equals("kobe")) sat += 0.12f;                            // city lights pop at night
            }
        } else {
            w = 0;
            sat = 1f;
            vig = 0f;
            con = 0f;
        }
        tWarmth = w;
        tSaturation = sat;
        tVignette = vig;
        tContrast = con;
        float k = 0.03f;
        warmth += (tWarmth - warmth) * k;
        saturation += (tSaturation - saturation) * k;
        vignette += (tVignette - vignette) * k;
        contrast += (tContrast - contrast) * k;
    }

    public static void apply() {
        if (failed || shader == null || mode == 3) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (Math.abs(warmth) < 0.01f && Math.abs(saturation - 1) < 0.01f && vignette < 0.01f && contrast < 0.01f) return;
        try {
            RenderTarget main = mc.getMainRenderTarget();
            if (copy == null) copy = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            else if (copy.width != main.width || copy.height != main.height) copy.resize(main.width, main.height, Minecraft.ON_OSX);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, copy.width, copy.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            main.bindWrite(true);

            shader.safeGetUniform("Warmth").set(warmth);
            shader.safeGetUniform("Saturation").set(saturation);
            shader.safeGetUniform("Vignette").set(vignette);
            shader.safeGetUniform("Contrast").set(contrast);
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0, copy.getColorTextureId());
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            BufferBuilder bb = Tesselator.getInstance().getBuilder();
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            bb.vertex(-1, -1, 0).uv(0, 0).endVertex();
            bb.vertex(1, -1, 0).uv(1, 0).endVertex();
            bb.vertex(1, 1, 0).uv(1, 1).endVertex();
            bb.vertex(-1, 1, 0).uv(0, 1).endVertex();
            BufferUploader.drawWithShader(bb.end());
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        } catch (Throwable t) {
            failed = true;
            com.nihonisshu.tabi.TabiMod.LOGGER.error("[tabi] colour grading disabled after error", t);
        }
    }
}
