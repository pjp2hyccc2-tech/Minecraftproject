package com.hitori.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.hitori.Hitori;
import com.hitori.HitoriConfig;
import com.hitori.client.Tex;
import com.hitori.entity.FixedCamera;
import com.hitori.entity.Woman;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * The woman (docs/DESIGN.md §5): drawn only when the view is a camera — never to your own eyes. A little translucent,
 * now and then a frame missing; her eyes layer glints on the IR feed.
 */
public class WomanRenderer extends MobRenderer<Woman, EntityModel<Woman>> {
    private static final ResourceLocation TEXTURE = Hitori.id("textures/entity/woman.png");
    private static final ResourceLocation EYES = Hitori.id("textures/entity/woman_eyes.png");
    private final RenderType eyes = RenderType.eyes(EYES);

    public WomanRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, pick(ctx), 0.0f);
        addLayer(new EyesLayer<Woman, EntityModel<Woman>>(this) {
            @Override
            public RenderType renderType() {
                return eyes;
            }

            @Override
            public void render(PoseStack ps, MultiBufferSource buf, int light, Woman e, float limbSwing, float limbSwingAmount, float partial,
                               float age, float headYaw, float headPitch) {
                if (Tex.has(EYES)) super.render(ps, buf, light, e, limbSwing, limbSwingAmount, partial, age, headYaw, headPitch);
            }
        });
    }

    private static EntityModel<Woman> pick(EntityRendererProvider.Context ctx) {
        EntityModel<Woman> real = Models.create("WomanModel", ctx);
        if (real != null) return real;
        return new HumanoidModel<>(ctx.bakeLayer(Models.PLACEHOLDER));
    }

    @Override
    public ResourceLocation getTextureLocation(Woman entity) {
        return TEXTURE;
    }

    @Nullable
    @Override
    protected RenderType getRenderType(Woman entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(TEXTURE);
    }

    /** Only through a camera (the app, or the endings' last feeds); GONE plays its 4-tick vanish, then nothing. */
    public static boolean visible(Woman w) {
        return Minecraft.getInstance().getCameraEntity() instanceof FixedCamera && (w.pose() != Woman.GONE || w.poseTime(0.0f) < 4.0f);
    }

    @Override
    public boolean shouldRender(Woman entity, Frustum frustum, double x, double y, double z) {
        return visible(entity) && super.shouldRender(entity, frustum, x, y, z);
    }

    @Override
    public void render(Woman e, float yaw, float partial, PoseStack ps, MultiBufferSource buffer, int light) {
        if (!visible(e)) return;
        float a = 0.88f;
        if (!HitoriConfig.flashReduction()) {
            long slot = (e.getId() * 7919L + e.tickCount / 3) * 2654435761L;
            if (((slot >>> 20) & 31) == 0) return; // a frame missing
            if (((slot >>> 26) & 7) == 0) a = 0.55f;
        }
        super.render(e, yaw, partial, ps, new AlphaBuffers(buffer, a, 1.0f, eyes), light);
    }

    @Override
    protected float getFlipDegrees(Woman entity) {
        return 0.0f;
    }
}
