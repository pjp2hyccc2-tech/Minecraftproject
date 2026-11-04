package jp.satsujinki.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.satsujinki.SatsujinkiConfig;
import jp.satsujinki.entity.ContestantEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.List;

public class ContestantRenderer extends HumanoidMobRenderer<ContestantEntity, ContestantModel> {
    public ContestantRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ContestantModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        this.addLayer(new BloodLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(ContestantEntity e) {
        return Skins.skin(e.getSkin());
    }

    @Override
    public void render(ContestantEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        if (e.isDeadOrDying()) return;
        super.render(e, yaw, pt, ps, buf, light);
        if (SatsujinkiConfig.speechBubbles()) renderSpeech(e, pt, ps, buf);
    }

    @Override
    protected void setupRotations(ContestantEntity e, PoseStack ps, float bob, float yRot, float pt) {
        super.setupRotations(e, ps, bob, yRot, pt);
        float t = e.tickCount + pt;
        int st = e.getState();
        if (st == ContestantEntity.ST_GRABBED) {
            ps.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 0.9F) * 14.0F));
            ps.mulPose(Axis.XP.rotationDegrees(Mth.cos(t * 0.7F) * 8.0F));
        } else if (st == ContestantEntity.ST_THROWN) {
            ps.mulPose(Axis.XP.rotationDegrees(t * 25.0F % 360.0F));
        } else {
            float fear = e.getFear();
            if (fear > 0.6F) {
                float s = (fear - 0.6F) * 2.5F;
                ps.translate(Mth.sin(t * 3.1F) * 0.012F * s, 0.0F, Mth.cos(t * 2.7F) * 0.012F * s);
            }
        }
    }

    private void renderSpeech(ContestantEntity e, float pt, PoseStack ps, MultiBufferSource buf) {
        long now = e.level().getGameTime();
        int until = e.getSpeechUntil();
        if (now >= until) return;
        Component text = e.getSpeech();
        if (text.getString().isEmpty()) return;
        if (this.entityRenderDispatcher.distanceToSqr(e) > 24.0D * 24.0D) return;
        float age = (now - e.getSpeechStart()) + pt;
        float pop = Mth.clamp(age / 4.0F, 0.0F, 1.0F);
        float fade = Mth.clamp((until - now - pt) / 8.0F, 0.0F, 1.0F);
        if (fade < 0.05F) return;
        int st = e.getState();
        boolean scream = st == ContestantEntity.ST_PANIC || st == ContestantEntity.ST_GRABBED || st == ContestantEntity.ST_THROWN || st == ContestantEntity.ST_LOCKED;
        Font font = this.getFont();
        List<FormattedCharSequence> lines = font.split(text, 150);
        ps.pushPose();
        ps.translate(0.0D, e.getBbHeight() + 0.85D + lines.size() * 0.24D, 0.0D);
        ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
        float s = 0.025F * (0.55F + 0.45F * pop) * (scream ? 1.15F : 1.0F);
        ps.scale(-s, -s, s);
        if (scream) ps.translate(Mth.sin(age * 2.3F) * 1.3F, Mth.cos(age * 2.9F) * 1.3F, 0.0F);
        Matrix4f m = ps.last().pose();
        int a = Math.max(16, (int) (fade * 255.0F));
        int rgb = scream ? 0xFF4A4A : (st == ContestantEntity.ST_COWER ? 0xB8D0FF : (st == ContestantEntity.ST_FIGHT ? 0xFFB040 : 0xFFFFFF));
        int bg = ((int) (fade * 0.6F * 255.0F) << 24) | (scream ? 0x2A0000 : 0x000000);
        int y = 0;
        for (FormattedCharSequence line : lines) {
            float x = -font.width(line) / 2.0F;
            font.drawInBatch(line, x, y, (a << 24) | rgb, false, m, buf, Font.DisplayMode.NORMAL, bg, LightTexture.FULL_BRIGHT);
            y += 10;
        }
        ps.popPose();
    }

    /** Bloodier skin overlay the more the contestant is hurt. */
    static final class BloodLayer extends RenderLayer<ContestantEntity, ContestantModel> {
        BloodLayer(RenderLayerParent<ContestantEntity, ContestantModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, ContestantEntity e, float limbSwing, float limbSwingAmount,
                           float pt, float age, float netHeadYaw, float headPitch) {
            float hp = e.getHealth() / Math.max(1.0F, e.getMaxHealth());
            int lvl = hp < 0.35F ? 2 : hp < 0.7F ? 1 : hp < 0.98F ? 0 : -1;
            if (lvl < 0) return;
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(Skins.BLOOD[lvl]));
            this.getParentModel().renderToBuffer(ps, vc, light, LivingEntityRenderer.getOverlayCoords(e, 0.0F), 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
