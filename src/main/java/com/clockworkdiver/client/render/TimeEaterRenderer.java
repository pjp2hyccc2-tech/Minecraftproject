package com.clockworkdiver.client.render;

import com.clockworkdiver.ClockworkDiver;
import com.clockworkdiver.entity.TimeEater;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** The Time Eater and its kin, with a madly spinning clock face on the shell and glowing eyes. */
public class TimeEaterRenderer extends MobRenderer<TimeEater, TimeEaterModel<TimeEater>> {
    private static final String[] NAMES = {"time_eater", "time_eater_shadow", "time_eater_shade", "time_eater_queen"};
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[NAMES.length];
    private static final ResourceLocation[] EYES = new ResourceLocation[NAMES.length];
    private static final ResourceLocation METAL = ClockworkDiver.id("textures/entity/mechanism_metal.png");
    private static final float SCALE = 1.35f;
    private static final int[] FACE = {0xB08A3A, 0x6A6A78, 0x7A4AA0, 0xE0B040};
    private static final int[] FACE_ENRAGED = {0xC04A3A, 0xA03030, 0xB040C0, 0xE04AC0};

    static {
        for (int i = 0; i < NAMES.length; i++) {
            TEXTURES[i] = ClockworkDiver.id("textures/entity/" + NAMES[i] + ".png");
            EYES[i] = ClockworkDiver.id("textures/entity/" + NAMES[i] + "_eyes.png");
        }
    }

    public TimeEaterRenderer(EntityRendererProvider.Context context) {
        super(context, new TimeEaterModel<>(context.bakeLayer(TimeEaterModel.LAYER)), 1.2f);
        addLayer(new EyesLayer(this));
    }

    private static int variant(TimeEater entity) {
        return Mth.clamp(entity.variant(), 0, NAMES.length - 1);
    }

    @Override
    public ResourceLocation getTextureLocation(TimeEater entity) {
        return TEXTURES[variant(entity)];
    }

    @Override
    protected void scale(TimeEater entity, PoseStack pose, float partialTick) {
        float s = SCALE * TimeEater.scaleOf(entity.variant());
        pose.scale(s, s, s);
    }

    @Override
    public void render(TimeEater entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        shadowRadius = 1.2f * TimeEater.scaleOf(entity.variant());
        super.render(entity, yaw, partialTick, pose, buffers, light);
        int v = variant(entity);
        float scale = TimeEater.scaleOf(v);
        pose.pushPose();
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
        pose.translate(0.0, 1.70 * scale, 0.0);
        float age = entity.tickCount + partialTick;
        float speed = entity.isEnraged() ? 0.5f : 0.15f;
        if (v == TimeEater.QUEEN) speed *= -1.0f;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(METAL));
        Mesh3D.clockFace(pose, vc, LightTexture.FULL_BRIGHT, entity.isEnraged() ? FACE_ENRAGED[v] : FACE[v], 0.62f * scale,
                age * speed * 0.08f, age * speed, age * speed * 6.0f);
        if (v == TimeEater.QUEEN) {
            // A crown of tiny clock faces orbits the Queen.
            for (int i = 0; i < 6; i++) {
                pose.pushPose();
                float a = age * 0.04f + i * Mth.TWO_PI / 6.0f;
                pose.translate(Math.cos(a) * 1.05, 0.55 + Math.sin(age * 0.1f + i) * 0.08, Math.sin(a) * 1.05);
                Mesh3D.clockFace(pose, vc, LightTexture.FULL_BRIGHT, 0xF0D060, 0.18f, age * 0.3f, age * 2.0f, age * 8.0f);
                pose.popPose();
            }
        }
        pose.popPose();
    }

    /** Glowing eyes, coloured per variant. */
    static final class EyesLayer extends RenderLayer<TimeEater, TimeEaterModel<TimeEater>> {
        EyesLayer(RenderLayerParent<TimeEater, TimeEaterModel<TimeEater>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, TimeEater entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            VertexConsumer vc = buffers.getBuffer(RenderType.eyes(EYES[variant(entity)]));
            getParentModel().renderToBuffer(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        }
    }
}
