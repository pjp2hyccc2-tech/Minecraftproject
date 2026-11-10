package com.eternalrealm.client.block;

import com.eternalrealm.block.forge.FacadeMechanismBlock;
import com.eternalrealm.block.forge.WaterWheelBlockEntity;
import com.eternalrealm.core.ModInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * An undershot mill wheel: a square oak axle, two iron-shod rims on spokes and wet paddle boards between them,
 * turning steadily (the same angle for every player, from the game time).
 */
public class WaterWheelRenderer implements BlockEntityRenderer<WaterWheelBlockEntity> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ModInfo.MOD_ID, "textures/entity/water_wheel/wheel.png");
    private static final float TEX = 64.0F;

    public WaterWheelRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(WaterWheelBlockEntity wheel, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Level level = wheel.getLevel();
        if (level == null) {
            return;
        }
        Direction facing = wheel.getBlockState().getValue(FacadeMechanismBlock.FACING);
        double[] n = {facing.getStepX(), 0.0, facing.getStepZ()};
        double[] up = {0.0, 1.0, 0.0};
        double[] right = Mesh.cross(up, n);
        double[] centre = {0.5, 0.5, 0.5};
        double radius = wheel.radius();
        double width = wheel.width();
        double start = 0.5 + 0.15;
        double end = start + width;
        double mid = (start + end) / 2.0;
        int light = LevelRenderer.getLightColor(level, wheel.getBlockPos().relative(facing));
        Mesh mesh = new Mesh(pose.last(), buffers.getBuffer(RenderType.entityCutout(TEXTURE)), light);

        double angle = wheel.angle(level.getGameTime() + partialTick);
        // axle (turning)
        double[] ax = radial(right, up, angle);
        double[] ay = radial(right, up, angle + Math.PI / 2.0);
        mesh.box(Mesh.add(centre, n, (0.45 + end + 0.15) / 2.0), ax, ay, n, 0.19, 0.19, (end + 0.15 - 0.45) / 2.0,
                0.0F, 16.0F / TEX, 1.0F, 24.0F / TEX);

        int paddles = Mth.clamp((int) Math.round(radius * 5.0), 12, 32);
        int spokes = radius < 2.5 ? 6 : 8;
        double rimDepth = Math.min(0.3, radius * 0.1);
        for (double t : new double[]{start + 0.07, end - 0.07}) {
            double[] ring = Mesh.add(centre, n, t);
            // hub
            mesh.box(ring, ax, ay, n, 0.32, 0.32, 0.1, 0.0F, 32.0F / TEX, 16.0F / TEX, 48.0F / TEX);
            // rim: one plank per paddle bay, an iron band on the outside
            double chord = 2.0 * radius * Math.sin(Math.PI / paddles);
            for (int i = 0; i < paddles; i++) {
                double a = angle + (i + 0.5) * Math.PI * 2.0 / paddles;
                double[] er = radial(right, up, a);
                double[] et = radial(right, up, a + Math.PI / 2.0);
                double r = radius - rimDepth / 2.0 - 0.02;
                mesh.box(Mesh.add(ring, er, r), et, er, n, chord / 2.0 + 0.02, rimDepth / 2.0, 0.07,
                        0.0F, 0.0F, 1.0F, 8.0F / TEX);
                mesh.box(Mesh.add(ring, er, radius - 0.01), et, er, n, chord / 2.0 + 0.03, 0.03, 0.075,
                        0.0F, 8.0F / TEX, 1.0F, 12.0F / TEX);
            }
            for (int i = 0; i < spokes; i++) {
                double a = angle + i * Math.PI * 2.0 / spokes;
                double[] er = radial(right, up, a);
                double[] et = radial(right, up, a + Math.PI / 2.0);
                double r0 = 0.3;
                double r1 = radius - rimDepth - 0.02;
                mesh.box(Mesh.add(ring, er, (r0 + r1) / 2.0), et, er, n, 0.07, (r1 - r0) / 2.0, 0.06,
                        0.0F, 24.0F / TEX, 1.0F, 28.0F / TEX);
            }
        }
        // paddles between the rims, standing out past them
        double[] middle = Mesh.add(centre, n, mid);
        for (int i = 0; i < paddles; i++) {
            double a = angle + i * Math.PI * 2.0 / paddles;
            double[] er = radial(right, up, a);
            double[] et = radial(right, up, a + Math.PI / 2.0);
            double r0 = radius - rimDepth - 0.25;
            double r1 = radius + 0.1;
            mesh.box(Mesh.add(middle, er, (r0 + r1) / 2.0), n, er, et, width / 2.0, (r1 - r0) / 2.0, 0.035,
                    0.0F, 0.0F, 1.0F, 8.0F / TEX);
        }
    }

    /** Unit vector in the wheel's plane at {@code angle} (0 = to the right, counter-clockwise). */
    private static double[] radial(double[] right, double[] up, double angle) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        return new double[]{right[0] * c + up[0] * s, right[1] * c + up[1] * s, right[2] * c + up[2] * s};
    }

    @Override
    public boolean shouldRenderOffScreen(WaterWheelBlockEntity wheel) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
