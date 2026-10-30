package com.mcbesiege.client;

import com.mcbesiege.block.MachinePartBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Build mode: draws the braces / springs / ropes that start at this part. */
public class PartBER implements BlockEntityRenderer<MachinePartBlockEntity> {
    public PartBER(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(MachinePartBlockEntity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        ListTag cons = be.getConnectors();
        if (cons.isEmpty() || be.getLevel() == null) return;
        BlockPos pos = be.getBlockPos();
        for (int i = 0; i < cons.size(); i++) {
            CompoundTag c = cons.getCompound(i);
            Direction fa = Direction.from3DDataValue(c.getInt("fa"));
            Direction fb = Direction.from3DDataValue(c.getInt("fb"));
            double ax = 0.5 + fa.getStepX() * 0.5, ay = 0.5 + fa.getStepY() * 0.5, az = 0.5 + fa.getStepZ() * 0.5;
            double bx = c.getInt("dx") + 0.5 + fb.getStepX() * 0.5, by = c.getInt("dy") + 0.5 + fb.getStepY() * 0.5, bz = c.getInt("dz") + 0.5 + fb.getStepZ() * 0.5;
            int l = LevelRenderer.getLightColor(be.getLevel(), pos.offset(c.getInt("dx") / 2, c.getInt("dy") / 2 + 1, c.getInt("dz") / 2));
            MachineRenderer.drawBeam(Minecraft.getInstance().getBlockRenderer(), ps, buffers, MachineRenderer.visual(c.getString("kind")), ax, ay, az, bx, by, bz, l);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(MachinePartBlockEntity be) {
        return !be.getConnectors().isEmpty();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
