package com.reisatsu.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reisatsu.Reisatsu;
import com.reisatsu.ReisatsuConfig;
import com.reisatsu.client.ClientState;
import com.reisatsu.client.Finder;
import com.reisatsu.client.Tex;
import com.reisatsu.entity.Spirit;
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
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

/**
 * A spirit (docs/DESIGN.md §5): translucent ({@code entityTranslucent}) with the naked-eye alpha of its kind —
 * invisible ones draw nothing — always fully visible through the finder, and at high fear a brief flicker to more.
 * The glow layer ({@code <id>_eyes.png}, {@code RenderType.eyes}) fades with it. No shadow: they cast none.
 */
public class SpiritRenderer<T extends Spirit> extends MobRenderer<T, EntityModel<T>> {
    private final ResourceLocation texture;
    private final ResourceLocation eyesTexture;
    private final RenderType eyes;
    private final float scale;
    private final boolean placeholder;

    public SpiritRenderer(EntityRendererProvider.Context ctx, String id, String modelName, float scale) {
        super(ctx, pick(ctx, modelName), 0.0f);
        this.texture = Reisatsu.id("textures/entity/" + id + ".png");
        this.eyesTexture = Reisatsu.id("textures/entity/" + id + "_eyes.png");
        this.eyes = RenderType.eyes(eyesTexture);
        this.placeholder = !Models.has(modelName);
        this.scale = placeholder ? scale : 1.0f;
        addLayer(new EyesLayer<T, EntityModel<T>>(this) {
            @Override
            public RenderType renderType() {
                return eyes;
            }

            @Override
            public void render(PoseStack ps, MultiBufferSource buf, int light, T e, float limbSwing, float limbSwingAmount, float partial,
                               float age, float headYaw, float headPitch) {
                if (Tex.has(eyesTexture)) super.render(ps, buf, light, e, limbSwing, limbSwingAmount, partial, age, headYaw, headPitch);
            }
        });
    }

    private static <T extends Spirit> EntityModel<T> pick(EntityRendererProvider.Context ctx, String modelName) {
        EntityModel<T> real = Models.create(modelName, ctx);
        if (real != null) return real;
        return new HumanoidModel<>(ctx.bakeLayer(Models.PLACEHOLDER));
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }

    @Nullable
    @Override
    protected RenderType getRenderType(T entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(texture);
    }

    /** How much of the spirit this player sees right now. */
    public static float alpha(Spirit e, float partial) {
        Minecraft mc = Minecraft.getInstance();
        float fade = e.fade();
        if (e.flag(Spirit.F_EXORCISM)) return 0.92f * fade;
        if (Finder.active(mc)) return fade;
        float a = e.eyeAlpha();
        float fear = ClientState.fearShown;
        if (a < 0.9f && fear > 0.6f && !ReisatsuConfig.flashReduction()) {
            // now and then, at the edge of panic, it is there a little more for a moment
            long slot = (e.getId() * 7919L + ClientState.clientTicks / 6) * 2654435761L;
            float r = ((slot >>> 16) & 0xFFFF) / 65535.0f;
            if (r < (fear - 0.6f) * 0.22f) a = Math.max(a, 0.4f + 0.25f * ((slot >>> 40) & 0xFF) / 255.0f);
        }
        return Mth.clamp(a, 0.0f, 1.0f) * fade;
    }

    @Override
    public boolean shouldRender(T entity, Frustum frustum, double x, double y, double z) {
        if (alpha(entity, 1.0f) < 0.01f) return false;
        return super.shouldRender(entity, frustum, x, y, z);
    }

    @Override
    public void render(T entity, float yaw, float partial, PoseStack ps, MultiBufferSource buffer, int light) {
        float a = alpha(entity, partial);
        if (a < 0.01f) return;
        ps.pushPose();
        float g = entity.glitch(partial);
        if (g > 0.55f && !ReisatsuConfig.flashReduction() && placeholder) {
            // the stutter: a jolt now and then (the real models move their joints wrong instead)
            long k = (entity.tickCount / 2) * 31L + entity.getId();
            if ((k * 2654435761L >>> 28) % 5 == 0) ps.translate((((k >>> 3) & 7) - 3.5) * 0.012 * g, 0.0, (((k >>> 6) & 7) - 3.5) * 0.012 * g);
        }
        super.render(entity, yaw, partial, ps, new AlphaBuffers(buffer, a, eyes), light);
        ps.popPose();
    }

    @Override
    protected void scale(T entity, PoseStack ps, float partial) {
        float s = scale;
        if (entity.flag(Spirit.F_BOUND)) s *= 0.25f + 0.75f * entity.fade();
        if (s != 1.0f) ps.scale(s, s, s);
    }
}
