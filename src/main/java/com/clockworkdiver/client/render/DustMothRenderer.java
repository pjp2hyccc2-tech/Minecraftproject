package com.clockworkdiver.client.render;

import com.clockworkdiver.ClockworkDiver;
import com.clockworkdiver.entity.DustMoth;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class DustMothRenderer extends MobRenderer<DustMoth, DustMothModel<DustMoth>> {
    private static final ResourceLocation TEXTURE = ClockworkDiver.id("textures/entity/dust_moth.png");

    public DustMothRenderer(EntityRendererProvider.Context context) {
        super(context, new DustMothModel<>(context.bakeLayer(DustMothModel.LAYER)), 0.35f);
    }

    @Override
    public ResourceLocation getTextureLocation(DustMoth entity) {
        return TEXTURE;
    }
}
