package com.meikon.client.render;

import com.meikon.entity.Hanayome;
import com.meikon.entity.Maboroshi;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** The phantom bride: the same figure, a little translucent, eyes glowing, perfectly still. */
public class MaboroshiRenderer extends MobRenderer<Maboroshi, HanayomeModel<Maboroshi>> {
    public MaboroshiRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HanayomeModel<>(ctx.bakeLayer(HanayomeModel.LAYER)), 0.0f);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return HanayomeRenderer.EYES;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(Maboroshi entity) {
        return HanayomeRenderer.TEXTURE;
    }

    @Override
    protected RenderType getRenderType(Maboroshi e, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(HanayomeRenderer.TEXTURE);
    }

    @Override
    public void render(Maboroshi e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        model.state = Hanayome.State.IDLE;
        model.phantom = true;
        model.duck = 0.0f;
        super.render(e, yaw, partial, ps, buf, light);
    }
}
