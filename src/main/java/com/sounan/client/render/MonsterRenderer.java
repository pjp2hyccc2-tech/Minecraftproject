package com.sounan.client.render;

import com.sounan.Sounan;
import com.sounan.entity.SounanMonster;
import com.sounan.entity.Utsurumono;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * A monster: its model and skin, plus the emissive layer (eyeshine, the yobigoe's headlamp, wet glints) that stays
 * visible in total darkness and flares in the flashlight. The models hide themselves (eyes included) while the
 * entity is invisible; the utsurumono is only ever drawn through the phone camera.
 */
public class MonsterRenderer<T extends SounanMonster, M extends EntityModel<T>> extends MobRenderer<T, M> {
    private final ResourceLocation texture;

    public MonsterRenderer(EntityRendererProvider.Context ctx, M model, String id, float shadow) {
        super(ctx, model, shadow);
        this.texture = Sounan.id("textures/entity/" + id + ".png");
        final RenderType eyes = RenderType.eyes(Sounan.id("textures/entity/" + id + "_eyes.png"));
        addLayer(new EyesLayer<T, M>(this) {
            @Override
            public RenderType renderType() {
                return eyes;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }

    @Override
    public boolean shouldRender(T entity, Frustum frustum, double x, double y, double z) {
        if (entity instanceof Utsurumono && !CameraVision.active(Minecraft.getInstance())) return false;
        return super.shouldRender(entity, frustum, x, y, z);
    }
}
