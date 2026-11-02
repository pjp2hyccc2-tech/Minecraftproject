package com.clockworkdiver.client.render;

import com.clockworkdiver.ClockworkDiver;
import com.clockworkdiver.customer.CustomerEntity;
import com.clockworkdiver.customer.CustomerProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Customers use the player model with their own steampunk outfits (and sometimes a top hat). */
public class CustomerRenderer extends HumanoidMobRenderer<CustomerEntity, PlayerModel<CustomerEntity>> {
    private static final ResourceLocation[] SKINS = new ResourceLocation[9];

    static {
        for (int i = 0; i < SKINS.length; i++) SKINS[i] = ClockworkDiver.id("textures/entity/customer/customer_" + i + ".png");
    }

    public CustomerRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        addLayer(new TopHatLayer(this, context.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(CustomerEntity entity) {
        int skin = entity.profile().skin;
        return SKINS[Math.floorMod(skin, SKINS.length)];
    }

    static final class TopHatLayer extends RenderLayer<CustomerEntity, PlayerModel<CustomerEntity>> {
        private static final ResourceLocation TEXTURE = ClockworkDiver.id("textures/entity/customer/top_hat.png");
        private final TopHatModel model;

        TopHatLayer(RenderLayerParent<CustomerEntity, PlayerModel<CustomerEntity>> parent, EntityModelSet models) {
            super(parent);
            this.model = new TopHatModel(models.bakeLayer(TopHatModel.LAYER));
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, CustomerEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            CustomerProfile profile = entity.profile();
            if (profile.hat == 0) return;
            pose.pushPose();
            getParentModel().getHead().translateAndRotate(pose);
            pose.translate(0.0, -0.5, 0.0);
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
            int c = profile.hatColor;
            model.renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, ((c >> 16) & 255) / 255.0f, ((c >> 8) & 255) / 255.0f,
                    (c & 255) / 255.0f, 1.0f);
            pose.popPose();
        }
    }
}
