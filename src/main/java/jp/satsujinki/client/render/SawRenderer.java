package jp.satsujinki.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.satsujinki.block.SawBlock;
import jp.satsujinki.block.entity.SawBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** The spinning circular blade sticking out of the saw table. */
public class SawRenderer implements BlockEntityRenderer<SawBlockEntity> {
    private static final float R = 0.66F;

    public SawRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(SawBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        BlockState st = be.getBlockState();
        Direction f = st.hasProperty(SawBlock.FACING) ? st.getValue(SawBlock.FACING) : Direction.NORTH;
        float ang = Mth.lerp(pt, be.prevAngle, be.angle);
        int la = LevelRenderer.getLightColor(level, be.getBlockPos().above());
        ps.pushPose();
        ps.translate(0.5D, 0.82D, 0.5D);
        if (f.getAxis() == Direction.Axis.X) ps.mulPose(Axis.YP.rotationDegrees(90.0F));
        ps.mulPose(Axis.XP.rotationDegrees(-ang));
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(be.getKills() > 0 ? Skins.SAW_BLADE_BLOODY : Skins.SAW_BLADE));
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        for (float x : new float[]{-0.01F, 0.01F}) {
            v(vc, m, n, x, -R, -R, 0.0F, 1.0F, la);
            v(vc, m, n, x, R, -R, 0.0F, 0.0F, la);
            v(vc, m, n, x, R, R, 1.0F, 0.0F, la);
            v(vc, m, n, x, -R, R, 1.0F, 1.0F, la);
        }
        ps.popPose();
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, int light) {
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 1.0F, 0.0F, 0.0F).endVertex();
    }

    @Override
    public int getViewDistance() {
        return 80;
    }
}
