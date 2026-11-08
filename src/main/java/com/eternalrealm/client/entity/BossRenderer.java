package com.eternalrealm.client.entity;

import com.eternalrealm.client.entity.layer.BossShieldLayer;
import com.eternalrealm.client.entity.layer.BossVisuals;
import com.eternalrealm.client.entity.layer.RigGlowLayer;
import com.eternalrealm.client.entity.model.BossModel;
import com.eternalrealm.client.entity.model.RigTables;
import com.eternalrealm.entity.BossRig;
import com.eternalrealm.entity.ERBoss;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Renderer of a boss: look texture (phase aware), model scale, emissive state layer and shield shell. */
public class BossRenderer extends MobRenderer<ERBoss, BossModel> {
    private final BossRig rig;
    private final RigTables tables;

    public BossRenderer(EntityRendererProvider.Context context, BossRig rig, float walkSpeed) {
        super(context, new BossModel(context.bakeLayer(ERModelLayers.boss(rig)), ERRigs.of(rig), walkSpeed), 1.0F);
        this.rig = rig;
        this.tables = ERRigs.of(rig);
        addLayer(new RigGlowLayer<>(this, this::glowTexture, BossVisuals::glowColor));
        addLayer(new BossShieldLayer(this,
                new BossModel(context.bakeLayer(ERModelLayers.bossShield(rig)), ERRigs.of(rig), walkSpeed)));
    }

    @Override
    public ResourceLocation getTextureLocation(ERBoss boss) {
        return EntityTextures.body(this.rig.id(), boss.lookKey());
    }

    private ResourceLocation glowTexture(ERBoss boss) {
        String look = boss.lookKey();
        return this.tables.glows()[this.tables.indexOf(look)] ? EntityTextures.glow(this.rig.id(), look) : null;
    }

    @Override
    protected void scale(ERBoss boss, PoseStack poseStack, float partialTick) {
        float scale = boss.modelScale();
        poseStack.scale(scale, scale, scale);
    }

    @Override
    public void render(ERBoss boss, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight) {
        this.shadowRadius = boss.getBbWidth() * 0.6F;
        super.render(boss, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    protected float getFlipDegrees(ERBoss boss) {
        return 0.0F;
    }
}
