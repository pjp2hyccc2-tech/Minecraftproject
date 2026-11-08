package com.eternalrealm.client.entity.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;

/**
 * Emissive overlay (eyes, runes, cores, flames): the parent model drawn again with the {@code _glow} texture in the
 * full-bright additive "eyes" render type, tinted per frame (boss states pulse or turn red).
 */
public class RigGlowLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    /** Color of the glow for an entity at a time; {r, g, b} in 0..1. */
    @FunctionalInterface
    public interface GlowColor<T> {
        float[] color(T entity, float ageInTicks);
    }

    private final Function<T, ResourceLocation> texture;
    private final GlowColor<T> color;

    /** @param texture glow texture of an entity, or {@code null} if it has none */
    public RigGlowLayer(RenderLayerParent<T, M> parent, Function<T, ResourceLocation> texture, GlowColor<T> color) {
        super(parent);
        this.texture = texture;
        this.color = color;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        ResourceLocation glow = this.texture.apply(entity);
        if (glow == null) {
            return;
        }
        float[] rgb = this.color.color(entity, ageInTicks);
        VertexConsumer consumer = buffers.getBuffer(RenderType.eyes(glow));
        getParentModel().renderToBuffer(poseStack, consumer, 15728640, OverlayTexture.NO_OVERLAY, rgb[0], rgb[1], rgb[2], 1.0F);
    }
}
