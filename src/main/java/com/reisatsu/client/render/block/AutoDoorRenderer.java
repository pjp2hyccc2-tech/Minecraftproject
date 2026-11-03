package com.reisatsu.client.render.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reisatsu.Reisatsu;
import com.reisatsu.block.AutoDoorBlock;
import com.reisatsu.block.Props;
import com.reisatsu.block.entity.AutoDoorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The automatic door panel: a 3-high glass leaf in an aluminium frame (texture auto_door_panel, 16x48: frame,
 * glass, the sun-logo decal and the 「自動ドア」 sticker) that slides sideways into the wall when it opens.
 */
public class AutoDoorRenderer implements BlockEntityRenderer<AutoDoorBlockEntity> {
    public static final ResourceLocation TEXTURE = Reisatsu.id("textures/block/auto_door_panel.png");

    public AutoDoorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(AutoDoorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState s = be.getBlockState();
        if (!(s.getBlock() instanceof AutoDoorBlock)) return;
        float p = Mth.lerp(partialTick, be.prevProgress, be.progress);
        float eased = p * p * (3 - 2 * p);
        Direction f = s.getValue(AutoDoorBlock.FACING);
        float dir = s.getValue(Props.SIDE) == Props.Side.LEFT ? -1.0f : 1.0f;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-f.toYRot()));
        pose.translate(dir * eased * 0.94f, 0, 0);
        PanelQuads.panel(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), -0.5f, 0.5f, 0.0f, 3.0f, 0.03f, light, overlay, 255);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(AutoDoorBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
