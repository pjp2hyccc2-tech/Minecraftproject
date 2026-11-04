package jp.satsujinki.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.satsujinki.entity.CorpseEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Draws a dead contestant lying on its back. Flat = pressed pancake, headless = guillotine, halves = saw.
 * The skins paint the normally hidden faces (neck, waist, hips) as raw flesh, so cut surfaces look right.
 */
public class CorpseRenderer extends EntityRenderer<CorpseEntity> {
    private final PlayerModel<LivingEntity> model;
    private final ModelPart[] parts;

    public CorpseRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false);
        this.parts = new ModelPart[]{this.model.head, this.model.hat, this.model.body, this.model.jacket, this.model.rightArm, this.model.leftArm,
                this.model.rightSleeve, this.model.leftSleeve, this.model.rightLeg, this.model.leftLeg, this.model.rightPants, this.model.leftPants};
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(CorpseEntity e) {
        return Skins.skin(e.getSkin());
    }

    @Override
    public void render(CorpseEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        int style = e.getStyle();
        float age = e.tickCount + pt;
        float life = e.getLife();
        float sink = age > life - 40.0F ? Math.min(1.0F, (age - (life - 40.0F)) / 40.0F) : 0.0F;
        float tilt = ((e.getId() * 2654435761L) >>> 16 & 0xFF) / 255.0F - 0.5F;
        pose(style, tilt);

        ps.pushPose();
        ps.translate(0.0D, -sink * 0.35D, 0.0D);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - e.getYRot()));
        if (!e.onGround() && (style == CorpseEntity.STYLE_TOP_HALF || style == CorpseEntity.STYLE_BOTTOM_HALF || style == CorpseEntity.STYLE_LYING)) {
            ps.translate(0.0D, 0.4D, 0.0D);
            ps.mulPose(Axis.XP.rotationDegrees(age * 23.0F));
            ps.translate(0.0D, -0.4D, 0.0D);
        }
        float lift = 0.16F;
        float center = -0.9F;
        switch (style) {
            case CorpseEntity.STYLE_FLAT -> {
                ps.translate(0.0D, 0.012D, 0.0D);
                ps.scale(1.45F, 0.1F, 1.2F);
            }
            case CorpseEntity.STYLE_IMPALED -> {
                lift = 0.2F;
                ps.mulPose(Axis.ZP.rotationDegrees(tilt * 24.0F));
            }
            case CorpseEntity.STYLE_TOP_HALF -> center = -1.28F;
            case CorpseEntity.STYLE_BOTTOM_HALF -> center = -0.38F;
            default -> {
            }
        }
        ps.translate(0.0D, lift, center);
        ps.mulPose(Axis.XP.rotationDegrees(90.0F));
        ps.scale(-1.0F, -1.0F, 1.0F);
        ps.translate(0.0D, -1.501D, 0.0D);

        VertexConsumer skin = buf.getBuffer(RenderType.entityCutoutNoCull(Skins.skin(e.getSkin())));
        this.model.renderToBuffer(ps, skin, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        VertexConsumer blood = buf.getBuffer(RenderType.entityCutoutNoCull(Skins.BLOOD[2]));
        this.model.renderToBuffer(ps, blood, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    private void pose(int style, float tilt) {
        for (ModelPart p : this.parts) p.resetPose();
        this.model.setAllVisible(true);
        this.model.young = false;
        this.model.crouching = false;
        this.model.riding = false;
        this.model.attackTime = 0.0F;
        PlayerModel<LivingEntity> m = this.model;
        switch (style) {
            case CorpseEntity.STYLE_FLAT -> {
                m.rightArm.zRot = 1.35F;
                m.leftArm.zRot = -1.35F;
                m.rightLeg.zRot = 0.45F;
                m.leftLeg.zRot = -0.45F;
                m.head.yRot = tilt;
            }
            case CorpseEntity.STYLE_HEADLESS -> {
                m.head.visible = false;
                m.hat.visible = false;
                m.rightArm.zRot = 0.5F + tilt * 0.4F;
                m.leftArm.zRot = -0.7F;
                m.rightLeg.zRot = 0.12F;
                m.leftLeg.zRot = -0.2F;
            }
            case CorpseEntity.STYLE_TOP_HALF -> {
                m.rightLeg.visible = false;
                m.leftLeg.visible = false;
                m.rightPants.visible = false;
                m.leftPants.visible = false;
                m.rightArm.xRot = -2.6F;
                m.rightArm.zRot = 0.3F;
                m.leftArm.zRot = -1.1F;
                m.head.xRot = -0.6F;
                m.head.yRot = tilt;
            }
            case CorpseEntity.STYLE_BOTTOM_HALF -> {
                m.head.visible = false;
                m.hat.visible = false;
                m.body.visible = false;
                m.jacket.visible = false;
                m.rightArm.visible = false;
                m.leftArm.visible = false;
                m.rightSleeve.visible = false;
                m.leftSleeve.visible = false;
                m.rightLeg.zRot = 0.25F;
                m.leftLeg.zRot = -0.1F;
                m.leftLeg.xRot = -0.4F;
            }
            case CorpseEntity.STYLE_IMPALED -> {
                m.rightArm.zRot = 0.95F;
                m.leftArm.zRot = -0.85F;
                m.head.xRot = -0.5F;
                m.rightLeg.zRot = 0.2F;
                m.leftLeg.zRot = -0.25F;
            }
            default -> {
                m.rightArm.zRot = 0.4F + tilt * 0.4F;
                m.leftArm.zRot = -0.45F;
                m.leftArm.xRot = -0.3F;
                m.head.yRot = tilt * 1.2F;
                m.rightLeg.zRot = 0.1F;
                m.leftLeg.zRot = -0.18F;
            }
        }
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
    }
}
