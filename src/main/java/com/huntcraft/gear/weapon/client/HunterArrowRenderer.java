package com.huntcraft.gear.weapon.client;

import com.huntcraft.HuntCraft;
import com.huntcraft.gear.weapon.HunterArrow;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Renders hunter arrows with the vanilla arrow geometry and our own texture. */
@OnlyIn(Dist.CLIENT)
public class HunterArrowRenderer extends ArrowRenderer<HunterArrow> {
    private static final ResourceLocation TEXTURE = HuntCraft.id("textures/item/weapon/hunter_arrow_entity.png");

    public HunterArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(HunterArrow arrow) {
        return TEXTURE;
    }
}
