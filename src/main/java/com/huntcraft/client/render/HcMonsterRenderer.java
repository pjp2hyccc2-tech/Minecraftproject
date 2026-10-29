package com.huntcraft.client.render;

import com.huntcraft.HuntCraft;
import com.huntcraft.client.model.HcModel;
import com.huntcraft.client.model.HcModelLoader;
import com.huntcraft.client.model.HcModels;
import com.huntcraft.entity.monster.HuntMonster;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Renderer for any hcmodel monster. Texture: textures/entity/NAME.png, optional emissive
 * textures/entity/NAME_glow.png (eyes, lava cracks, electric organs...).
 */
@OnlyIn(Dist.CLIENT)
public class HcMonsterRenderer<T extends HuntMonster> extends MobRenderer<T, HcModel<T>> {
    protected final ResourceLocation texture;
    protected final float baseShadow;
    protected final HcModelLoader.HcModelData data;

    public HcMonsterRenderer(EntityRendererProvider.Context ctx, String modelName, float shadowRadius) {
        this(ctx, modelName, modelName, shadowRadius);
    }

    public HcMonsterRenderer(EntityRendererProvider.Context ctx, String modelName, String textureName, float shadowRadius) {
        super(ctx, new HcModel<>(ctx.bakeLayer(HcModels.layer(modelName)), HcModelLoader.get(modelName)), shadowRadius);
        this.data = HcModelLoader.get(modelName);
        this.texture = HuntCraft.id("textures/entity/" + textureName + ".png");
        this.baseShadow = shadowRadius;
        ResourceLocation glow = HuntCraft.id("textures/entity/" + textureName + "_glow.png");
        if (Minecraft.getInstance().getResourceManager().getResource(glow).isPresent()) {
            this.addLayer(new GlowLayer<>(this, glow));
        }
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }

    @Override
    protected void scale(T entity, PoseStack poseStack, float partialTick) {
        float s = entity.getMonsterScale() * data.renderScale;
        poseStack.scale(s, s, s);
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        this.shadowRadius = baseShadow * entity.getMonsterScale();
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    @Override
    protected float getFlipDegrees(T entity) {
        return 0.0F;
    }

    /** Full-bright emissive layer; pulses red while the monster is enraged. */
    public static class GlowLayer<T extends HuntMonster> extends RenderLayer<T, HcModel<T>> {
        private final ResourceLocation glow;

        public GlowLayer(RenderLayerParent<T, HcModel<T>> parent, ResourceLocation glow) {
            super(parent);
            this.glow = glow;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isCarcass()) return;
            VertexConsumer vc = buffers.getBuffer(RenderType.eyes(glow));
            float r = 1, g = 1, b = 1;
            if (entity.isEnraged()) {
                float pulse = 0.75F + 0.25F * (float) Math.sin(ageInTicks * 0.3F);
                g = 0.55F * pulse;
                b = 0.45F * pulse;
            } else if (entity.isTired()) {
                r = g = b = 0.45F;
            }
            this.getParentModel().renderToBuffer(poseStack, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, r, g, b, 1.0F);
        }
    }
}
