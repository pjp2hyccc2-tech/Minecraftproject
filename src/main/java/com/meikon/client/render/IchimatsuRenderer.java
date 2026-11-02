package com.meikon.client.render;

import com.meikon.Meikon;
import com.meikon.entity.Ichimatsu;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class IchimatsuRenderer extends MobRenderer<Ichimatsu, IchimatsuModel> {
    private static final ResourceLocation TEXTURE = Meikon.id("textures/entity/ichimatsu.png");

    public IchimatsuRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new IchimatsuModel(ctx.bakeLayer(IchimatsuModel.LAYER)), 0.25f);
    }

    @Override
    public ResourceLocation getTextureLocation(Ichimatsu entity) {
        return TEXTURE;
    }
}
