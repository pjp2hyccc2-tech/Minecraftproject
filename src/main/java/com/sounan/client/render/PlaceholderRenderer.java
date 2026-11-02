package com.sounan.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sounan.entity.SounanMonster;
import com.sounan.entity.Utsurumono;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Stand-in look for a monster until its real model is in (vanilla humanoid, scaled). */
public class PlaceholderRenderer<T extends SounanMonster> extends MobRenderer<T, HumanoidModel<T>> {
    private final ResourceLocation texture;
    private final float scale;

    public PlaceholderRenderer(EntityRendererProvider.Context ctx, String texture, float scale) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.4f * scale);
        this.texture = new ResourceLocation(texture);
        this.scale = scale;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }

    @Override
    protected void scale(T entity, PoseStack pose, float partial) {
        pose.scale(scale, scale, scale);
    }

    @Override
    public boolean shouldRender(T entity, Frustum frustum, double x, double y, double z) {
        if (entity instanceof Utsurumono && !CameraVision.active(Minecraft.getInstance())) return false;
        return super.shouldRender(entity, frustum, x, y, z);
    }
}
