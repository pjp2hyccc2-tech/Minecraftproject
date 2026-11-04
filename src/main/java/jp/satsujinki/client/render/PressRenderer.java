package jp.satsujinki.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import jp.satsujinki.block.entity.PressBlockEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** The hydraulic rod and the 3x3 crushing plate. */
public class PressRenderer implements BlockEntityRenderer<PressBlockEntity> {
    private final ModelPart plate;
    private final ModelPart rod;

    public PressRenderer(BlockEntityRendererProvider.Context ctx) {
        this.plate = ctx.bakeLayer(ModModelLayers.PRESS_PLATE).getChild("plate");
        this.rod = ctx.bakeLayer(ModModelLayers.PRESS_ROD).getChild("rod");
    }

    @Override
    public void render(PressBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        float off = be.plateOffset(pt);
        BlockPos p = be.getBlockPos();
        int lp = LevelRenderer.getLightColor(level, BlockPos.containing(p.getX() + 0.5D, p.getY() - off - 0.6D, p.getZ() + 0.5D));
        if (off > 0.02F) {
            ps.pushPose();
            ps.translate(0.5D, -off, 0.5D);
            ps.scale(1.0F, off, 1.0F);
            this.rod.render(ps, buf.getBuffer(RenderType.entityCutout(Skins.PRESS_ROD)), lp, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        ps.pushPose();
        ps.translate(0.5D, -off - PressBlockEntity.PLATE_THICK, 0.5D);
        this.plate.render(ps, buf.getBuffer(RenderType.entityCutout(be.bloody ? Skins.PRESS_PLATE_BLOODY : Skins.PRESS_PLATE)), lp, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(PressBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
