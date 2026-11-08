package com.eternalrealm.client.entity;

import com.eternalrealm.client.entity.layer.RigGlowLayer;
import com.eternalrealm.client.entity.model.EnemyModel;
import com.eternalrealm.client.entity.model.RigTables;
import com.eternalrealm.entity.ERMonster;
import com.eternalrealm.entity.EnemyRig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Renderer of an enemy rig: variant texture, model scale, emissive layer, translucency for ghosts. */
public class EnemyRenderer<T extends ERMonster> extends MobRenderer<T, EnemyModel<T>> {
    private static final float[] WHITE = {1.0F, 1.0F, 1.0F};

    private final EnemyRig rig;
    private final RigTables tables;
    private final float baseShadow;

    public EnemyRenderer(EntityRendererProvider.Context context, EnemyRig rig, float walkSpeed, float shadow) {
        super(context, new EnemyModel<>(context.bakeLayer(ERModelLayers.enemy(rig)), ERRigs.of(rig), walkSpeed), shadow);
        this.rig = rig;
        this.tables = ERRigs.of(rig);
        this.baseShadow = shadow;
        addLayer(new RigGlowLayer<>(this, this::glowTexture, (entity, age) -> WHITE));
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return EntityTextures.body(this.rig.id(), entity.getVariant());
    }

    private ResourceLocation glowTexture(T entity) {
        String variant = entity.getVariant();
        return this.tables.glows()[this.tables.indexOf(variant)] ? EntityTextures.glow(this.rig.id(), variant) : null;
    }

    @Override
    protected void scale(T entity, PoseStack poseStack, float partialTick) {
        float scale = entity.variantScale();
        poseStack.scale(scale, scale, scale);
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight) {
        this.shadowRadius = this.baseShadow * entity.variantScale();
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    protected float getFlipDegrees(T entity) {
        return 0.0F;
    }

    @Override
    protected RenderType getRenderType(T entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        if (bodyVisible && !translucent && entity.variant().translucent()) {
            return RenderType.entityTranslucent(getTextureLocation(entity));
        }
        return super.getRenderType(entity, bodyVisible, translucent, glowing);
    }
}
