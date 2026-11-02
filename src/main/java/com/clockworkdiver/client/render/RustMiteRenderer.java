package com.clockworkdiver.client.render;

import com.clockworkdiver.ClockworkDiver;
import com.clockworkdiver.entity.RustMite;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RustMiteRenderer extends MobRenderer<RustMite, RustMiteModel<RustMite>> {
    private static final ResourceLocation TEXTURE = ClockworkDiver.id("textures/entity/rust_mite.png");

    public RustMiteRenderer(EntityRendererProvider.Context context) {
        super(context, new RustMiteModel<>(context.bakeLayer(RustMiteModel.LAYER)), 0.45f);
    }

    @Override
    public ResourceLocation getTextureLocation(RustMite entity) {
        return TEXTURE;
    }
}
