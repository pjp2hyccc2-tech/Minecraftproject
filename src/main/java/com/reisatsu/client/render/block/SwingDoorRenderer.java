package com.reisatsu.client.render.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reisatsu.Reisatsu;
import com.reisatsu.block.Props;
import com.reisatsu.block.SwingDoorBlock;
import com.reisatsu.block.entity.SwingDoorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/** A kitchen swing door leaf (texture swing_door, 16x32 with the round porthole), swinging about its outer hinge. */
public class SwingDoorRenderer implements BlockEntityRenderer<SwingDoorBlockEntity> {
    public static final ResourceLocation TEXTURE = Reisatsu.id("textures/block/swing_door.png");

    public SwingDoorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(SwingDoorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState s = be.getBlockState();
        if (!(s.getBlock() instanceof SwingDoorBlock)) return;
        float angle = Mth.lerp(partialTick, be.prevAngle, be.angle);
        Direction f = s.getValue(SwingDoorBlock.FACING);
        boolean left = s.getValue(Props.SIDE) == Props.Side.LEFT;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-f.toYRot()));
        // hinge on the outer edge of the pair; positive angles swing the leaf to the back (-z)
        pose.translate(left ? -0.5 : 0.5, 0, 0);
        pose.mulPose(Axis.YP.rotationDegrees(left ? angle : -angle));
        float x0 = left ? 0.0f : -1.0f, x1 = left ? 1.0f : 0.0f;
        PanelQuads.panel(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), x0 + 0.02f, x1 - 0.02f, 0.0f, 2.0f - 0.05f, 0.04f, light, overlay, 255);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(SwingDoorBlockEntity be) {
        return true;
    }
}
