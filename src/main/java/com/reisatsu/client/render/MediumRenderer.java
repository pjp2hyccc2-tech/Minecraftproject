package com.reisatsu.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reisatsu.Reisatsu;
import com.reisatsu.client.Tex;
import com.reisatsu.entity.Medium;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** 霊媒師 八雲トキ: a living person, drawn plainly (her prayer beads may glint from the glow layer). */
public class MediumRenderer extends MobRenderer<Medium, EntityModel<Medium>> {
    private static final ResourceLocation TEXTURE = Reisatsu.id("textures/entity/medium.png");
    private static final ResourceLocation EYES = Reisatsu.id("textures/entity/medium_eyes.png");
    private final boolean placeholder;

    public MediumRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, pick(ctx), 0.4f);
        placeholder = !Models.has("Medium");
        final RenderType eyes = RenderType.eyes(EYES);
        addLayer(new EyesLayer<Medium, EntityModel<Medium>>(this) {
            @Override
            public RenderType renderType() {
                return eyes;
            }

            @Override
            public void render(PoseStack ps, MultiBufferSource buf, int light, Medium e, float limbSwing, float limbSwingAmount, float partial,
                               float age, float headYaw, float headPitch) {
                if (Tex.has(EYES)) super.render(ps, buf, light, e, limbSwing, limbSwingAmount, partial, age, headYaw, headPitch);
            }
        });
    }

    private static EntityModel<Medium> pick(EntityRendererProvider.Context ctx) {
        EntityModel<Medium> real = Models.create("Medium", ctx);
        return real != null ? real : new HumanoidModel<>(ctx.bakeLayer(Models.PLACEHOLDER));
    }

    @Override
    public ResourceLocation getTextureLocation(Medium entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(Medium entity, PoseStack ps, float partial) {
        if (placeholder) ps.scale(0.86f, 0.86f, 0.86f);
    }
}
