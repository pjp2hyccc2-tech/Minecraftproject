package com.hitori.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.hitori.Hitori;
import com.hitori.client.Tex;
import com.hitori.entity.FixedCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 定点カメラ (docs/DESIGN.md §5): the little action cam on its tripod, looking along its yaw / pitch, the red REC LED
 * and the rear screen on its eyes layer (drawn every frame: the model blinks the LED). Not drawn while you are looking
 * through it. Knocked over, the model tips onto its side by itself (FixedCamera is Animated: FALLEN).
 */
public class CameraRenderer extends EntityRenderer<FixedCamera> {
    private static final ResourceLocation TEXTURE = Hitori.id("textures/entity/camera.png");
    private static final ResourceLocation EYES = Hitori.id("textures/entity/camera_eyes.png");
    private final EntityModel<FixedCamera> model;

    public CameraRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        EntityModel<FixedCamera> real = Models.create("CameraModel", ctx);
        this.model = real != null ? real : new Placeholder(ctx.bakeLayer(Models.PLACEHOLDER_CAMERA));
        this.shadowRadius = 0.12f;
    }

    @Override
    public ResourceLocation getTextureLocation(FixedCamera entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(FixedCamera entity, Frustum frustum, double x, double y, double z) {
        if (Minecraft.getInstance().getCameraEntity() == entity || entity.clientOnly) return false;
        return super.shouldRender(entity, frustum, x, y, z);
    }

    @Override
    public void render(FixedCamera e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        if (Minecraft.getInstance().getCameraEntity() == e) return;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180.0f - e.getYRot()));
        ps.scale(-1.0f, -1.0f, 1.0f);
        ps.translate(0.0, -1.501, 0.0);
        // the model tips over by itself (FALLEN) and blinks its REC LED inside the eyes layer
        model.setupAnim(e, 0.0f, 0.0f, e.tickCount + partial, 0.0f, e.getXRot());
        model.renderToBuffer(ps, buf.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        if (Tex.has(EYES)) {
            model.renderToBuffer(ps, buf.getBuffer(RenderType.eyes(EYES)), 0xF000F0, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        }
        ps.popPose();
        super.render(e, yaw, partial, ps, buf, light);
    }

    /** The stand-in model: a box with a lens on a stick; the head pitch tilts the body. */
    static final class Placeholder extends EntityModel<FixedCamera> {
        private final ModelPart body, lens, leg;

        Placeholder(ModelPart root) {
            this.body = root.getChild("body");
            this.lens = root.getChild("lens");
            this.leg = root.getChild("leg");
        }

        @Override
        public void setupAnim(FixedCamera entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            body.xRot = headPitch * ((float) Math.PI / 180.0f);
            lens.xRot = body.xRot;
        }

        @Override
        public void renderToBuffer(PoseStack ps, com.mojang.blaze3d.vertex.VertexConsumer v, int light, int overlay, float r, float g, float b, float a) {
            body.render(ps, v, light, overlay, r, g, b, a);
            lens.render(ps, v, light, overlay, r, g, b, a);
            leg.render(ps, v, light, overlay, r, g, b, a);
        }
    }
}
