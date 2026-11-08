package com.eternalrealm.client;

import com.eternalrealm.combat.forge.SkillArrow;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Skill arrows look like vanilla arrows (the vanilla texture is referenced, not copied). */
public final class SkillArrowRenderer extends ArrowRenderer<SkillArrow> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/projectiles/arrow.png");

    public SkillArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SkillArrow arrow) {
        return TEXTURE;
    }
}
