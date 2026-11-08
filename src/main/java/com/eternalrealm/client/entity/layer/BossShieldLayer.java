package com.eternalrealm.client.entity.layer;

import com.eternalrealm.client.entity.EntityTextures;
import com.eternalrealm.client.entity.model.BossModel;
import com.eternalrealm.entity.ERBoss;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Translucent energy shell while the boss is shielded: an inflated copy of the boss model, animated in sync, drawn
 * with the scrolling additive "energy swirl" render type (like a charged creeper) in the boss's theme color.
 */
public class BossShieldLayer extends RenderLayer<ERBoss, BossModel> {
    private final BossModel shell;

    public BossShieldLayer(RenderLayerParent<ERBoss, BossModel> parent, BossModel shell) {
        super(parent);
        this.shell = shell;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, ERBoss boss, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!boss.isShielded() || boss.isInvisible()) {
            return;
        }
        float time = boss.tickCount + partialTick;
        this.shell.prepareMobModel(boss, limbSwing, limbSwingAmount, partialTick);
        getParentModel().copyPropertiesTo(this.shell);
        this.shell.setupAnim(boss, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        VertexConsumer consumer = buffers.getBuffer(RenderType.energySwirl(EntityTextures.BOSS_SHIELD,
                time * 0.012F % 1.0F, time * 0.008F % 1.0F));
        float[] rgb = BossVisuals.theme(boss.bossId());
        float pulse = 0.55F + 0.15F * (float) Math.sin(time * 0.25F);
        this.shell.renderToBuffer(poseStack, consumer, 15728640, OverlayTexture.NO_OVERLAY,
                rgb[0] * pulse, rgb[1] * pulse, rgb[2] * pulse, 1.0F);
    }
}
