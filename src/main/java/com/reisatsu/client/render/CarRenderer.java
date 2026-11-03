package com.reisatsu.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reisatsu.Reisatsu;
import com.reisatsu.client.Tex;
import com.reisatsu.entity.MediumCar;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** The medium's kei car: the spirits worker's model (or a box), wet paint, the dome light in the glow layer. */
public class CarRenderer extends EntityRenderer<MediumCar> {
    private static final ResourceLocation TEXTURE = Reisatsu.id("textures/entity/medium_car.png");
    private static final ResourceLocation EYES = Reisatsu.id("textures/entity/medium_car_eyes.png");
    private final EntityModel<MediumCar> model;

    public CarRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.9f;
        EntityModel<MediumCar> real = Models.create("MediumCar", ctx);
        this.model = real != null ? real : new Box(ctx.bakeLayer(Models.PLACEHOLDER_CAR));
    }

    @Override
    public ResourceLocation getTextureLocation(MediumCar entity) {
        return TEXTURE;
    }

    @Override
    public void render(MediumCar car, float yaw, float partial, PoseStack ps, MultiBufferSource buffer, int light) {
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180.0f - yaw));
        ps.scale(-1.0f, -1.0f, 1.0f);
        ps.translate(0.0f, -1.501f, 0.0f);
        model.setupAnim(car, 0.0f, 0.0f, car.tickCount + partial, 0.0f, 0.0f);
        VertexConsumer v = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        model.renderToBuffer(ps, v, light, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        if (Tex.has(EYES)) {
            model.renderToBuffer(ps, buffer.getBuffer(RenderType.eyes(EYES)), 0xF000F0, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        }
        ps.popPose();
        super.render(car, yaw, partial, ps, buffer, light);
    }

    /** A stand-in box car. */
    static class Box extends EntityModel<MediumCar> {
        private final ModelPart root;

        Box(ModelPart root) {
            this.root = root;
        }

        @Override
        public void setupAnim(MediumCar e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
        }

        @Override
        public void renderToBuffer(PoseStack ps, VertexConsumer v, int light, int overlay, float r, float g, float b, float a) {
            root.render(ps, v, light, overlay, r, g, b, a);
        }
    }
}
