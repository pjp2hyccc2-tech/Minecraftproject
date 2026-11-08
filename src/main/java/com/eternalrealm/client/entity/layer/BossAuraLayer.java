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
import net.minecraft.util.Mth;

/**
 * The power of a boss's later forms and its end: an inflated, animated copy of the model drawn with the additive
 * "energy swirl" render type and the flowing aura texture.
 * <ul>
 *     <li>From the second form on, an aura in the form's colour that grows stronger with every form.</li>
 *     <li>A white flash right after a transformation ({@link ERBoss#transformFlash}).</li>
 *     <li>During the defeat sequence the body glows white, brighter and brighter while it dissolves.</li>
 * </ul>
 */
public class BossAuraLayer extends RenderLayer<ERBoss, BossModel> {
    private final BossModel shell;

    public BossAuraLayer(RenderLayerParent<ERBoss, BossModel> parent, BossModel shell) {
        super(parent);
        this.shell = shell;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, ERBoss boss, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (boss.isInvisible()) {
            return;
        }
        int form = Math.min(3, boss.getPhase());
        boolean defeated = boss.isDefeatPlaying();
        float aura = defeated || form == 0 ? 0.0F : 0.16F + 0.12F * form;
        float white = Math.max(boss.transformFlash(partialTick) * 0.9F,
                defeated ? 0.15F + 0.25F * boss.defeatProgress(partialTick) + 0.6F * boss.dissolve(partialTick) : 0.0F);
        if (aura <= 0.0F && white <= 0.01F) {
            return;
        }
        float time = boss.tickCount + partialTick;
        this.shell.prepareMobModel(boss, limbSwing, limbSwingAmount, partialTick);
        getParentModel().copyPropertiesTo(this.shell);
        this.shell.setupAnim(boss, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (aura > 0.0F) {
            float[] rgb = BossVisuals.formColor(boss.bossId(), form);
            float pulse = aura * (0.8F + 0.2F * Mth.sin(time * (0.12F + 0.04F * form)));
            VertexConsumer consumer = buffers.getBuffer(RenderType.energySwirl(EntityTextures.BOSS_AURA,
                    time * 0.004F % 1.0F, -time * (0.012F + 0.004F * form) % 1.0F));
            this.shell.renderToBuffer(poseStack, consumer, 15728640, OverlayTexture.NO_OVERLAY, rgb[0] * pulse,
                    rgb[1] * pulse, rgb[2] * pulse, 1.0F);
        }
        if (white > 0.01F) {
            float level = Math.min(1.0F, white);
            VertexConsumer consumer = buffers.getBuffer(RenderType.energySwirl(EntityTextures.BOSS_AURA,
                    time * 0.02F % 1.0F, time * 0.03F % 1.0F));
            this.shell.renderToBuffer(poseStack, consumer, 15728640, OverlayTexture.NO_OVERLAY, level, level,
                    level * 0.95F, 1.0F);
        }
    }
}
