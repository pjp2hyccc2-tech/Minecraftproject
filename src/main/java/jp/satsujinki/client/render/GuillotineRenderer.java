package jp.satsujinki.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.satsujinki.block.entity.GuillotineBlockEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** The tall wooden frame, the lunette and the falling blade. */
public class GuillotineRenderer implements BlockEntityRenderer<GuillotineBlockEntity> {
    private final ModelPart frame;
    private final ModelPart blade;
    private final ModelPart weight;

    public GuillotineRenderer(BlockEntityRendererProvider.Context ctx) {
        ModelPart root = ctx.bakeLayer(ModModelLayers.GUILLOTINE);
        this.frame = root.getChild("frame");
        this.blade = root.getChild("blade");
        this.weight = root.getChild("weight");
    }

    @Override
    public void render(GuillotineBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        Direction f = be.facing();
        int la = LevelRenderer.getLightColor(level, be.getBlockPos().above());
        ps.pushPose();
        ps.translate(0.5D, 0.0D, 0.5D);
        ps.mulPose(Axis.YP.rotationDegrees(-f.toYRot()));
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutout(be.bloody ? Skins.GUILLOTINE_BLOODY : Skins.GUILLOTINE));
        this.frame.render(ps, vc, la, OverlayTexture.NO_OVERLAY);
        float drop = be.bladeDrop(pt);
        ps.translate(0.0D, 34.0D / 16.0D - drop, 1.6D / 16.0D);
        this.blade.render(ps, vc, la, OverlayTexture.NO_OVERLAY);
        this.weight.render(ps, vc, la, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(GuillotineBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 80;
    }
}
