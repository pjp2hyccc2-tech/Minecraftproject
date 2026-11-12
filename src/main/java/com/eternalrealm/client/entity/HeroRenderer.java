package com.eternalrealm.client.entity;

import com.eternalrealm.client.entity.layer.RigGlowLayer;
import com.eternalrealm.client.entity.model.HeroModel;
import com.eternalrealm.client.entity.model.RigTables;
import com.eternalrealm.npc.forge.TravellerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Renderer of the four legendary travellers: their hero texture ({@code textures/entity/hero/<id>.png}, painted at
 * two texels per unit), the model scale, and the emissive layer, which breathes softly and flares while a move plays
 * (the witch's stars, the knight's embers, the swordsman's runes, the sister's halo).
 */
public class HeroRenderer extends MobRenderer<TravellerEntity, HeroModel> {
    private static final float SHADOW = 0.5F;

    private final RigTables tables = ERRigs.HERO;

    public HeroRenderer(EntityRendererProvider.Context context) {
        super(context, new HeroModel(context.bakeLayer(ERModelLayers.hero()), ERRigs.HERO), SHADOW);
        addLayer(new RigGlowLayer<>(this, this::glowTexture, HeroRenderer::glowColor));
    }

    private int index(TravellerEntity hero) {
        return this.tables.indexOf(hero.heroAppearance());
    }

    @Override
    public ResourceLocation getTextureLocation(TravellerEntity hero) {
        return EntityTextures.body("hero", this.tables.variants()[index(hero)]);
    }

    private ResourceLocation glowTexture(TravellerEntity hero) {
        int index = index(hero);
        return this.tables.glows()[index] ? EntityTextures.glow("hero", this.tables.variants()[index]) : null;
    }

    private static float[] glowColor(TravellerEntity hero, float ageInTicks) {
        float pulse = 0.82F + 0.18F * Mth.sin(ageInTicks * 0.12F);
        float flare = Math.max(pulse, hero.actionWeight(ageInTicks));
        float down = 1.0F - 0.5F * hero.downWeight(ageInTicks);
        float v = flare * down;
        return new float[]{v, v, v};
    }

    @Override
    protected void scale(TravellerEntity hero, PoseStack poseStack, float partialTick) {
        float scale = this.tables.scales()[index(hero)];
        poseStack.scale(scale, scale, scale);
    }

    @Override
    public void render(TravellerEntity hero, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        this.shadowRadius = SHADOW * this.tables.scales()[index(hero)];
        super.render(hero, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    protected float getFlipDegrees(TravellerEntity hero) {
        return 0.0F;
    }
}
