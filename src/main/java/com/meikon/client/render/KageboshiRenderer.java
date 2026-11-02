package com.meikon.client.render;

import com.meikon.Meikon;
import com.meikon.entity.Kageboshi;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** A translucent black silhouette with two pale eyes, arms reaching. */
public class KageboshiRenderer extends HumanoidMobRenderer<Kageboshi, HumanoidModel<Kageboshi>> {
    private static final ResourceLocation TEXTURE = Meikon.id("textures/entity/kageboshi.png");
    private static final RenderType EYES = RenderType.eyes(Meikon.id("textures/entity/kageboshi_eyes.png"));

    public KageboshiRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ShadowModel(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.0f);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(Kageboshi entity) {
        return TEXTURE;
    }

    @Override
    protected RenderType getRenderType(Kageboshi e, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(TEXTURE);
    }

    @Override
    protected void scale(Kageboshi e, PoseStack ps, float partial) {
        float breathe = 1.0f + Mth.sin((e.tickCount + partial) * 0.15f) * 0.02f;
        ps.scale(0.92f * breathe, 1.14f, 0.92f * breathe);
    }

    private static final class ShadowModel extends HumanoidModel<Kageboshi> {
        ShadowModel(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(Kageboshi e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
            super.setupAnim(e, limbSwing, limbSwingAmount, age, headYaw, headPitch);
            rightArm.xRot = -1.3f + Mth.sin(age * 0.11f) * 0.08f;
            leftArm.xRot = -1.3f + Mth.cos(age * 0.12f) * 0.08f;
            rightArm.zRot = 0.06f;
            leftArm.zRot = -0.06f;
            head.zRot = Mth.sin(age * 0.05f) * 0.15f;
        }
    }
}
