package jp.satsujinki.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.satsujinki.entity.GibEntity;
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

/** Tumbling gore chunks; heads, arms and legs use the victim's own skin. */
public class GibRenderer extends EntityRenderer<GibEntity> {
    private final ModelPart chunk;
    private final ModelPart bone;
    private final PlayerModel<LivingEntity> body;

    public GibRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        ModelPart root = ctx.bakeLayer(ModModelLayers.GIB);
        this.chunk = root.getChild("chunk");
        this.bone = root.getChild("bone");
        this.body = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false);
        this.shadowRadius = 0.15F;
    }

    @Override
    public ResourceLocation getTextureLocation(GibEntity e) {
        return Skins.GIB_MEAT;
    }

    @Override
    public void render(GibEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.tickCount + pt;
        float life = e.getLife();
        float shrink = t > life - 30.0F ? Math.max(0.0F, (life - t) / 30.0F) : 1.0F;
        int kind = e.getKind();
        ps.pushPose();
        ps.translate(0.0D, kind >= GibEntity.KIND_HEAD ? 0.2D : 0.12D, 0.0D);
        if (e.isResting()) {
            ps.mulPose(Axis.YP.rotationDegrees(e.spinY * 9.0F));
            if (kind == GibEntity.KIND_ARM || kind == GibEntity.KIND_LEG || kind == GibEntity.KIND_BONE) ps.mulPose(Axis.ZP.rotationDegrees(90.0F));
            else ps.mulPose(Axis.ZP.rotationDegrees(e.spinZ * 2.0F));
        } else {
            ps.mulPose(Axis.XP.rotationDegrees(t * e.spinX));
            ps.mulPose(Axis.YP.rotationDegrees(t * e.spinY));
            ps.mulPose(Axis.ZP.rotationDegrees(t * e.spinZ));
        }
        ps.scale(shrink, shrink, shrink);
        int ov = OverlayTexture.NO_OVERLAY;
        switch (kind) {
            case GibEntity.KIND_ORGAN -> {
                ps.scale(1.25F, 0.8F, 1.05F);
                this.chunk.render(ps, buf.getBuffer(RenderType.entityCutout(Skins.GIB_ORGAN)), light, ov);
            }
            case GibEntity.KIND_BONE -> this.bone.render(ps, buf.getBuffer(RenderType.entityCutout(Skins.GIB_BONE)), light, ov);
            case GibEntity.KIND_HEAD, GibEntity.KIND_ARM, GibEntity.KIND_LEG -> renderLimb(e, kind, ps, buf, light);
            default -> this.chunk.render(ps, buf.getBuffer(RenderType.entityCutout(Skins.GIB_MEAT)), light, ov);
        }
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    private void renderLimb(GibEntity e, int kind, PoseStack ps, MultiBufferSource buf, int light) {
        int ov = OverlayTexture.NO_OVERLAY;
        if (e.getSkin() < 0) {
            this.chunk.render(ps, buf.getBuffer(RenderType.entityCutout(Skins.GIB_MEAT)), light, ov);
            return;
        }
        VertexConsumer skin = buf.getBuffer(RenderType.entityCutoutNoCull(Skins.skin(e.getSkin())));
        VertexConsumer blood = buf.getBuffer(RenderType.entityCutoutNoCull(Skins.BLOOD[1]));
        ps.scale(-1.0F, -1.0F, 1.0F);
        ModelPart main;
        ModelPart layer;
        switch (kind) {
            case GibEntity.KIND_HEAD -> {
                main = this.body.head;
                layer = this.body.hat;
                main.resetPose();
                main.setPos(0.0F, 4.0F, 0.0F);
            }
            case GibEntity.KIND_ARM -> {
                main = this.body.rightArm;
                layer = this.body.rightSleeve;
                main.resetPose();
                main.setPos(1.0F, -4.0F, 0.0F);
            }
            default -> {
                main = this.body.rightLeg;
                layer = this.body.rightPants;
                main.resetPose();
                main.setPos(0.0F, -6.0F, 0.0F);
            }
        }
        main.visible = true;
        layer.visible = true;
        layer.copyFrom(main);
        main.render(ps, skin, light, ov);
        layer.render(ps, skin, light, ov);
        main.render(ps, blood, light, ov);
    }
}
