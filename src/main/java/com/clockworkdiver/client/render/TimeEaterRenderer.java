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
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** The Time Eater, with a madly spinning clock face on its shell and glowing eyes. */
public class TimeEaterRenderer extends MobRenderer<TimeEater, TimeEaterModel<TimeEater>> {
    private static final ResourceLocation TEXTURE = ClockworkDiver.id("textures/entity/time_eater.png");
    private static final ResourceLocation EYES = ClockworkDiver.id("textures/entity/time_eater_eyes.png");
    private static final ResourceLocation METAL = ClockworkDiver.id("textures/entity/mechanism_metal.png");
    private static final float SCALE = 1.35f;

    public TimeEaterRenderer(EntityRendererProvider.Context context) {
        super(context, new TimeEaterModel<>(context.bakeLayer(TimeEaterModel.LAYER)), 1.2f);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return RenderType.eyes(EYES);
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(TimeEater entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(TimeEater entity, PoseStack pose, float partialTick) {
        pose.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public void render(TimeEater entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(entity, yaw, partialTick, pose, buffers, light);
        pose.pushPose();
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
        pose.translate(0.0, 1.70, 0.0);
        float age = entity.tickCount + partialTick;
        float speed = entity.isEnraged() ? 0.5f : 0.15f;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(METAL));
        Mesh3D.clockFace(pose, vc, LightTexture.FULL_BRIGHT, entity.isEnraged() ? 0xC04A3A : 0xB08A3A, 0.62f,
                age * speed * 0.08f, age * speed, age * speed * 6.0f);
        pose.popPose();
    }
}
