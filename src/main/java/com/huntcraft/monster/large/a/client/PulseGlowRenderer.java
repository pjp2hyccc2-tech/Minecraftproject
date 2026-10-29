package com.huntcraft.monster.large.a.client;

import com.huntcraft.HuntCraft;
import com.huntcraft.client.model.HcModel;
import com.huntcraft.client.render.HcMonsterRenderer;
import com.huntcraft.entity.monster.HuntMonster;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * hcmodel renderer whose emissive layer reacts to the monster's state instead of the default red rage tint:
 * <ul>
 *   <li>FROST: the ice crystals flare with a cold pulsing light while enraged</li>
 *   <li>VOLT: the electric organs slowly "breathe"; while enraged they are supercharged (fast pulse + flicker)</li>
 * </ul>
 * Both dim while the monster is tired.
 */
@OnlyIn(Dist.CLIENT)
public class PulseGlowRenderer<T extends HuntMonster> extends HcMonsterRenderer<T> {
    public enum Style { FROST, VOLT }

    public PulseGlowRenderer(EntityRendererProvider.Context ctx, String name, float shadowRadius, Style style) {
        super(ctx, name, shadowRadius);
        this.layers.removeIf(l -> l instanceof HcMonsterRenderer.GlowLayer);
        ResourceLocation glow = HuntCraft.id("textures/entity/" + name + "_glow.png");
        if (Minecraft.getInstance().getResourceManager().getResource(glow).isPresent()) {
            this.addLayer(new PulseLayer<>(this, glow, style));
        }
    }

    /** Full-bright additive layer with state-dependent intensity. */
    public static class PulseLayer<T extends HuntMonster> extends RenderLayer<T, HcModel<T>> {
        private final ResourceLocation glow;
        private final Style style;

        public PulseLayer(RenderLayerParent<T, HcModel<T>> parent, ResourceLocation glow, Style style) {
            super(parent);
            this.glow = glow;
            this.style = style;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isCarcass()) return;
            VertexConsumer vc = buffers.getBuffer(RenderType.eyes(glow));
            boolean tired = entity.isTired();
            float base = tired ? 0.45F : 1.0F;
            if (style == Style.VOLT && !entity.isEnraged()) base *= 0.86F + 0.14F * Mth.sin(ageInTicks * 0.08F);
            this.getParentModel().renderToBuffer(poseStack, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, base, base, base, 1.0F);
            if (!entity.isEnraged() || tired) return;
            float r, g, b;
            if (style == Style.VOLT) {
                float k = 0.55F + 0.45F * Mth.sin(ageInTicks * 0.55F);
                if ((((int) ageInTicks) * 31 + entity.getId() * 17) % 23 == 0) k = 1.0F;
                r = k;
                g = 0.95F * k;
                b = 0.6F * k;
            } else {
                float k = 0.45F + 0.3F * Mth.sin(ageInTicks * 0.2F);
                r = 0.55F * k;
                g = 0.85F * k;
                b = k;
            }
            this.getParentModel().renderToBuffer(poseStack, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, r, g, b, 1.0F);
        }
    }
}
