package com.clockworkdiver.client.render;

import com.clockworkdiver.ClockworkDiver;
import com.clockworkdiver.entity.CogGremlin;
import com.clockworkdiver.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class CogGremlinRenderer extends MobRenderer<CogGremlin, CogGremlinModel<CogGremlin>> {
    private static final ResourceLocation TEXTURE = ClockworkDiver.id("textures/entity/cog_gremlin.png");

    public CogGremlinRenderer(EntityRendererProvider.Context context) {
        super(context, new CogGremlinModel<>(context.bakeLayer(CogGremlinModel.LAYER)), 0.4f);
    }

    @Override
    public ResourceLocation getTextureLocation(CogGremlin entity) {
        return TEXTURE;
    }

    @Override
    public void render(CogGremlin entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(entity, yaw, partialTick, pose, buffers, light);
        if (!entity.hasTooth()) return;
        // The stolen gear tooth floats above its head.
        pose.pushPose();
        float age = entity.tickCount + partialTick;
        pose.translate(0.0, entity.getBbHeight() + 0.45 + Mth.sin(age * 0.15f) * 0.05, 0.0);
        pose.mulPose(Axis.YP.rotationDegrees(age * 4.0f));
        pose.scale(0.5f, 0.5f, 0.5f);
        Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(ModItems.GEAR_TOOTH.get()), ItemDisplayContext.GROUND, light,
                OverlayTexture.NO_OVERLAY, pose, buffers, entity.level(), entity.getId());
        pose.popPose();
    }
}
