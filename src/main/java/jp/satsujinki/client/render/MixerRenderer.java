package jp.satsujinki.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.satsujinki.block.entity.MixerBlockEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Spinning blades + the swirling blood soup that rises with every victim. */
public class MixerRenderer implements BlockEntityRenderer<MixerBlockEntity> {
    private final ModelPart blades;

    public MixerRenderer(BlockEntityRendererProvider.Context ctx) {
        this.blades = ctx.bakeLayer(ModModelLayers.MIXER_BLADES);
    }

    @Override
    public void render(MixerBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        int lightAbove = LevelRenderer.getLightColor(level, be.getBlockPos().above());
        float ang = Mth.lerp(pt, be.prevAngle, be.angle);
        ps.pushPose();
        ps.translate(0.5D, 1.0D + 1.0D / 16.0D, 0.5D);
        if (be.spin > 0.05F) {
            ps.translate(Mth.sin(ang * 0.37F) * 0.012F * be.spin, 0.0D, Mth.cos(ang * 0.41F) * 0.012F * be.spin);
        }
        ps.mulPose(Axis.YP.rotationDegrees(ang));
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutout(be.getKills() > 0 ? Skins.MIXER_BLADES_BLOODY : Skins.MIXER_BLADES));
        this.blades.render(ps, vc, lightAbove, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        if (be.blood > 0.01F) renderSoup(be, level, pt, ps, buf, lightAbove);
    }

    private void renderSoup(MixerBlockEntity be, Level level, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float time = (level.getGameTime() % 100000L) + pt;
        float h = be.blood * 2.75F;
        float wob = 0.02F + be.spin * 0.06F;
        float y0 = 1.02F;
        float yt = y0 + h;
        float x0 = -0.97F, x1 = 1.97F, z0 = -0.97F, z1 = 1.97F;
        int frame = (int) (time / 2.5F) % 4;
        float v0 = frame * 0.25F, v1 = v0 + 0.25F;
        float c0 = Mth.sin(time * 0.35F) * wob, c1 = Mth.sin(time * 0.35F + 1.6F) * wob;
        float c2 = Mth.sin(time * 0.35F + 3.1F) * wob, c3 = Mth.sin(time * 0.35F + 4.7F) * wob;
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(Skins.MIXER_SOUP));
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        int r = 255, g = 255, b = 255, a = 225;
        // surface
        v(vc, m, n, x0, yt + c0, z0, 0.0F, v0, r, g, b, a, light, 0.0F, 1.0F, 0.0F);
        v(vc, m, n, x0, yt + c1, z1, 0.0F, v1, r, g, b, a, light, 0.0F, 1.0F, 0.0F);
        v(vc, m, n, x1, yt + c2, z1, 1.0F, v1, r, g, b, a, light, 0.0F, 1.0F, 0.0F);
        v(vc, m, n, x1, yt + c3, z0, 1.0F, v0, r, g, b, a, light, 0.0F, 1.0F, 0.0F);
        // sides (seen through the glass), darker
        int d = 170;
        float vs = Math.min(0.25F, h * 0.09F);
        v(vc, m, n, x0, y0, z0, 0.0F, v0 + vs, d, d, d, a, light, 0.0F, 0.0F, -1.0F);
        v(vc, m, n, x0, yt + c0, z0, 0.0F, v0, d, d, d, a, light, 0.0F, 0.0F, -1.0F);
        v(vc, m, n, x1, yt + c3, z0, 1.0F, v0, d, d, d, a, light, 0.0F, 0.0F, -1.0F);
        v(vc, m, n, x1, y0, z0, 1.0F, v0 + vs, d, d, d, a, light, 0.0F, 0.0F, -1.0F);

        v(vc, m, n, x1, y0, z1, 0.0F, v0 + vs, d, d, d, a, light, 0.0F, 0.0F, 1.0F);
        v(vc, m, n, x1, yt + c2, z1, 0.0F, v0, d, d, d, a, light, 0.0F, 0.0F, 1.0F);
        v(vc, m, n, x0, yt + c1, z1, 1.0F, v0, d, d, d, a, light, 0.0F, 0.0F, 1.0F);
        v(vc, m, n, x0, y0, z1, 1.0F, v0 + vs, d, d, d, a, light, 0.0F, 0.0F, 1.0F);

        v(vc, m, n, x0, y0, z1, 0.0F, v0 + vs, d, d, d, a, light, -1.0F, 0.0F, 0.0F);
        v(vc, m, n, x0, yt + c1, z1, 0.0F, v0, d, d, d, a, light, -1.0F, 0.0F, 0.0F);
        v(vc, m, n, x0, yt + c0, z0, 1.0F, v0, d, d, d, a, light, -1.0F, 0.0F, 0.0F);
        v(vc, m, n, x0, y0, z0, 1.0F, v0 + vs, d, d, d, a, light, -1.0F, 0.0F, 0.0F);

        v(vc, m, n, x1, y0, z0, 0.0F, v0 + vs, d, d, d, a, light, 1.0F, 0.0F, 0.0F);
        v(vc, m, n, x1, yt + c3, z0, 0.0F, v0, d, d, d, a, light, 1.0F, 0.0F, 0.0F);
        v(vc, m, n, x1, yt + c2, z1, 1.0F, v0, d, d, d, a, light, 1.0F, 0.0F, 0.0F);
        v(vc, m, n, x1, y0, z1, 1.0F, v0 + vs, d, d, d, a, light, 1.0F, 0.0F, 0.0F);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                          int r, int g, int b, int a, int light, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(MixerBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
