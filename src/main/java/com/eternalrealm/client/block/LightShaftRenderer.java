package com.eternalrealm.client.block;

import com.eternalrealm.block.forge.LightShaftBlock;
import com.eternalrealm.block.forge.LightShaftBlockEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Sunbeams through windows for players without a shader pack: a few overlapping translucent slabs from the window
 * into the room, additive so they read as light, bright at the window and fading towards the floor, breathing
 * slowly. Their strength follows the daylight and the sky light at the window; they vanish at night and while a
 * shader pack (which draws real light shafts) is active.
 */
public class LightShaftRenderer implements BlockEntityRenderer<LightShaftBlockEntity> {
    private static final int LAYERS = 4;

    /** Additive, no texture, no culling, depth-tested but not depth-writing. */
    private static final class Types extends RenderType {
        static final RenderType BEAM = RenderType.create("eternalrealm_sunbeam", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 4096, false, true, CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private Types(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling,
                      boolean sort, Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }
    }

    public LightShaftRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(LightShaftBlockEntity beam, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Level level = beam.getLevel();
        if (level == null || ShaderCompat.shaderPackInUse()) {
            return;
        }
        float strength = daylight(level, partialTick)
                * level.getBrightness(LightLayer.SKY, beam.getBlockPos()) / 15.0F;
        if (strength <= 0.02F) {
            return;
        }
        long seed = beam.getBlockPos().asLong();
        float time = (level.getGameTime() + partialTick) * 0.02F + (seed & 255) * 0.1F;
        float breathe = 0.85F + 0.15F * Mth.sin(time);
        Direction facing = beam.getBlockState().getValue(LightShaftBlock.FACING);
        Vec3 forward = new Vec3(facing.getStepX(), 0.0, facing.getStepZ());
        Vec3 side = new Vec3(-facing.getStepZ(), 0.0, facing.getStepX());
        double pitch = Math.toRadians(beam.pitch());
        Vec3 direction = forward.scale(Math.cos(pitch)).add(0.0, -Math.sin(pitch), 0.0);
        Vec3 start = new Vec3(0.5, 0.5, 0.5).subtract(forward.scale(0.45));
        Vec3 end = start.add(direction.scale(beam.length()));
        VertexConsumer out = buffers.getBuffer(Types.BEAM);
        Matrix4f matrix = pose.last().pose();
        float base = 0.10F * strength * breathe;
        for (int layer = 0; layer < LAYERS; layer++) {
            float f = (layer + 0.5F) / LAYERS - 0.5F;                 // -0.375 .. 0.375 across the width
            float w0 = beam.width() * (0.55F - 0.1F * Math.abs(f));
            float wobble = 0.06F * Mth.sin(time * 1.7F + layer * 2.1F);
            Vec3 shift = side.scale(f * beam.width() + wobble);
            int alphaNear = Math.round(255.0F * base * (layer % 2 == 0 ? 1.0F : 0.8F));
            slab(out, matrix, start.add(shift), end.add(shift).add(side.scale(f * beam.width() * 0.25)), side, w0,
                    beam.height(), alphaNear);
        }
    }

    /** A vertical ribbon (and a horizontal one) along the beam, bright at the start, gone at the end. */
    private static void slab(VertexConsumer out, Matrix4f m, Vec3 a, Vec3 b, Vec3 side, float width, float height,
                             int alpha) {
        int r = 255;
        int g = 236;
        int bl = 190;
        float hh = height / 2.0F;
        float grow = 1.25F; // the beam widens a little as it travels
        // vertical sheet
        quad(out, m, a.add(0, hh, 0), a.add(0, -hh, 0), b.add(0, -hh * grow, 0), b.add(0, hh * grow, 0),
                r, g, bl, alpha, 0);
        // sheet across the width
        Vec3 sa = side.scale(width / 2.0);
        Vec3 sb = side.scale(width / 2.0 * grow);
        quad(out, m, a.add(sa), a.subtract(sa), b.subtract(sb), b.add(sb), r, g, bl, alpha, 0);
    }

    private static void quad(VertexConsumer out, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int r, int g, int b,
                             int alphaStart, int alphaEnd) {
        out.vertex(m, (float) p0.x, (float) p0.y, (float) p0.z).color(r, g, b, alphaStart).endVertex();
        out.vertex(m, (float) p1.x, (float) p1.y, (float) p1.z).color(r, g, b, alphaStart).endVertex();
        out.vertex(m, (float) p2.x, (float) p2.y, (float) p2.z).color(r, g, b, alphaEnd).endVertex();
        out.vertex(m, (float) p3.x, (float) p3.y, (float) p3.z).color(r, g, b, alphaEnd).endVertex();
    }

    /** 1 at noon, 0 at night, smooth at dawn and dusk (the vanilla sky brightness curve). */
    static float daylight(Level level, float partialTick) {
        float angle = level.getTimeOfDay(partialTick);
        float f = 1.0F - (Mth.cos(angle * ((float) Math.PI * 2.0F)) * 2.0F + 0.2F);
        f = Mth.clamp(f, 0.0F, 1.0F);
        return (1.0F - f) * (1.0F - level.getRainLevel(partialTick) * 0.6F);
    }

    @Override
    public boolean shouldRenderOffScreen(LightShaftBlockEntity beam) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
