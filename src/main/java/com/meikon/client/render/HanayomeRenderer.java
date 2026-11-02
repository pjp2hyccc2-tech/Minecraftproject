package com.meikon.client.render;

import com.meikon.Meikon;
import com.meikon.entity.Hanayome;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public class HanayomeRenderer extends MobRenderer<Hanayome, HanayomeModel<Hanayome>> {
    public static final ResourceLocation TEXTURE = Meikon.id("textures/entity/hanayome.png");
    public static final RenderType EYES = RenderType.eyes(Meikon.id("textures/entity/hanayome_eyes.png"));

    public HanayomeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HanayomeModel<>(ctx.bakeLayer(HanayomeModel.LAYER)), 0.0f);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }

            @Override
            public void render(PoseStack ps, MultiBufferSource buf, int light, Hanayome e, float limbSwing, float limbSwingAmount,
                               float partial, float age, float headYaw, float headPitch) {
                Hanayome.State s = e.clientState();
                if (s == Hanayome.State.CHASE || s == Hanayome.State.CAPTURE || s == Hanayome.State.SEARCH || s == Hanayome.State.CHECK_CLOSET) {
                    super.render(ps, buf, light, e, limbSwing, limbSwingAmount, partial, age, headYaw, headPitch);
                }
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(Hanayome entity) {
        return TEXTURE;
    }

    @Override
    public void render(Hanayome e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        model.state = e.clientState();
        model.phantom = false;
        e.clientDuck += ((lowCeiling(e) ? 1.0f : 0.0f) - e.clientDuck) * 0.2f;
        model.duck = e.clientDuck;
        super.render(e, yaw, partial, ps, buf, light);
    }

    static boolean lowCeiling(Entity e) {
        BlockPos p = BlockPos.containing(e.getX(), e.getY() + 2.15, e.getZ());
        return !e.level().getBlockState(p).getCollisionShape(e.level(), p).isEmpty();
    }
}
