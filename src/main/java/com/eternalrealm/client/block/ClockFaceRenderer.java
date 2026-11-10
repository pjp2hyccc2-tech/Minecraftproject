package com.eternalrealm.client.block;

import com.eternalrealm.block.forge.ClockFaceBlockEntity;
import com.eternalrealm.block.forge.FacadeMechanismBlock;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.town.TowerClock;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Tower clock dials: an ivory enamel face with Roman numerals inside a gilded bead bezel, and ornate gold hands that
 * show the world's time (the minute hand goes round once per in-game hour) and cast a soft shadow on the dial. Like
 * real tower clocks the face is lit from behind at night.
 */
public class ClockFaceRenderer implements BlockEntityRenderer<ClockFaceBlockEntity> {
    private static final ResourceLocation DIAL = new ResourceLocation(ModInfo.MOD_ID, "textures/entity/clock/dial.png");
    private static final ResourceLocation PARTS = new ResourceLocation(ModInfo.MOD_ID, "textures/entity/clock/parts.png");
    private static final float TEX = 128.0F;
    /** Inner radius of the bezel as a fraction of the dial radius. */
    private static final double FACE = 0.9;

    public ClockFaceRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ClockFaceBlockEntity clock, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Level level = clock.getLevel();
        if (level == null) {
            return;
        }
        Direction facing = clock.getBlockState().getValue(FacadeMechanismBlock.FACING);
        double[] n = {facing.getStepX(), 0.0, facing.getStepZ()};
        double[] up = {0.0, 1.0, 0.0};
        double[] right = Mesh.cross(up, n);
        double[] centre = {0.5 + n[0] * 0.5, 0.5, 0.5 + n[2] * 0.5};
        double radius = clock.size();
        double face = radius * FACE;

        int light = LevelRenderer.getLightColor(level, clock.getBlockPos().relative(facing));
        float night = 1.0F - LightShaftRenderer.daylight(level, partialTick);
        int glow = Mth.floor(4.0F + 8.0F * night);                     // lamps behind the dial after dusk
        int dialLight = LightTexture.pack(Math.max(LightTexture.block(light), glow), LightTexture.sky(light));

        // the enamel dial: one square quad, the round face cut out of the texture
        Mesh dial = new Mesh(pose.last(), buffers.getBuffer(RenderType.entityCutout(DIAL)), dialLight);
        double[] tl = at(centre, right, up, n, -face, face, 0.025);
        double[] bl = at(centre, right, up, n, -face, -face, 0.025);
        double[] br = at(centre, right, up, n, face, -face, 0.025);
        double[] tr = at(centre, right, up, n, face, face, 0.025);
        dial.quad(tl, bl, br, tr, 0.0F, 0.0F, 1.0F, 1.0F);

        Mesh parts = new Mesh(pose.last(), buffers.getBuffer(RenderType.entityCutout(PARTS)), light);
        bezel(parts, centre, right, up, n, radius, face);

        double time = level.getDayTime();                // 0.36 degrees per tick on the minute hand: smooth enough
        double hour = Math.toRadians(TowerClock.hourAngle(time));
        double minute = Math.toRadians(TowerClock.minuteAngle(time));
        // shadows first (dark copies a little down and to the right), then the hands
        parts.light(dialLight).color(0.12F, 0.1F, 0.08F, 1.0F);
        hand(parts, centre, right, up, n, hour, radius * 0.56, radius, 0.035, 0.03, true);
        hand(parts, centre, right, up, n, minute, radius * 0.82, radius, 0.037, 0.03, false);
        parts.light(light).color(1.0F, 1.0F, 1.0F, 1.0F);
        hand(parts, centre, right, up, n, hour, radius * 0.56, radius, 0.055, 0.0, true);
        hand(parts, centre, right, up, n, minute, radius * 0.82, radius, 0.065, 0.0, false);
        // the gold boss over the arbor
        double cap = radius * 0.075;
        parts.quad(at(centre, right, up, n, -cap, cap, 0.075), at(centre, right, up, n, -cap, -cap, 0.075),
                at(centre, right, up, n, cap, -cap, 0.075), at(centre, right, up, n, cap, cap, 0.075),
                32.0F / TEX, 0.0F, 48.0F / TEX, 16.0F / TEX);
    }

    /** A gilded bead bezel: a front ring, the outer rim down to the wall and the inner lip down to the dial. */
    private static void bezel(Mesh mesh, double[] c, double[] right, double[] up, double[] n, double radius,
                              double face) {
        int segments = Mth.clamp((int) Math.round(radius * 16.0), 24, 96);
        float u0 = 48.0F / TEX;
        float u1 = 64.0F / TEX;
        double front = 0.09;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            double c0 = Math.cos(a0);
            double s0 = Math.sin(a0);
            double c1 = Math.cos(a1);
            double s1 = Math.sin(a1);
            double[] in0 = at(c, right, up, n, face * c0, face * s0, front);
            double[] out0 = at(c, right, up, n, radius * c0, radius * s0, front);
            double[] out1 = at(c, right, up, n, radius * c1, radius * s1, front);
            double[] in1 = at(c, right, up, n, face * c1, face * s1, front);
            mesh.quad(in0, out0, out1, in1, u0, 0.0F, u1, 8.0F / TEX);                 // front ring (beads)
            double[] wall0 = at(c, right, up, n, radius * c0, radius * s0, 0.0);
            double[] wall1 = at(c, right, up, n, radius * c1, radius * s1, 0.0);
            mesh.quad(out0, wall0, wall1, out1, u0, 8.0F / TEX, u1, 12.0F / TEX);       // outer rim
            double[] lip0 = at(c, right, up, n, face * c0, face * s0, 0.02);
            double[] lip1 = at(c, right, up, n, face * c1, face * s1, 0.02);
            mesh.quad(in1, lip1, lip0, in0, u0, 12.0F / TEX, u1, 16.0F / TEX);         // inner lip
        }
    }

    /**
     * A hand at {@code angle} (clockwise from twelve). Its sprite stands upright in the parts texture with the pivot
     * near its foot; {@code shadowOffset} shifts a copy down-right for the shadow.
     */
    private static void hand(Mesh mesh, double[] c, double[] right, double[] up, double[] n, double angle,
                             double length, double radius, double lift, double shadowOffset, boolean hourHand) {
        float u0 = (hourHand ? 0.0F : 16.0F) / TEX;
        float u1 = (hourHand ? 16.0F : 32.0F) / TEX;
        float spriteLength = hourHand ? 64.0F : 96.0F;   // sprite height in pixels
        float pivot = hourHand ? 52.0F : 84.0F;          // pivot row (tip at row 0)
        double scale = length / pivot;
        double tail = (spriteLength - pivot) * scale;
        double half = 8.0 * scale;
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        // along the hand (12 o'clock = up, turning clockwise) and across it
        double dx = sin;
        double dy = cos;
        double wx = cos;
        double wy = -sin;
        double ox = shadowOffset * radius * 0.6;
        double oy = -shadowOffset * radius * 0.9;
        double[] tipLeft = at(c, right, up, n, ox + dx * length - wx * half, oy + dy * length - wy * half, lift);
        double[] tailLeft = at(c, right, up, n, ox - dx * tail - wx * half, oy - dy * tail - wy * half, lift);
        double[] tailRight = at(c, right, up, n, ox - dx * tail + wx * half, oy - dy * tail + wy * half, lift);
        double[] tipRight = at(c, right, up, n, ox + dx * length + wx * half, oy + dy * length + wy * half, lift);
        mesh.quad(tipLeft, tailLeft, tailRight, tipRight, u0, 0.0F, u1, spriteLength / TEX);
    }

    /** A point on the clock: {@code x} to the right, {@code y} up, {@code z} out of the wall. */
    private static double[] at(double[] c, double[] right, double[] up, double[] n, double x, double y, double z) {
        return new double[]{c[0] + right[0] * x + up[0] * y + n[0] * z, c[1] + right[1] * x + up[1] * y + n[1] * z,
                c[2] + right[2] * x + up[2] * y + n[2] * z};
    }

    @Override
    public boolean shouldRenderOffScreen(ClockFaceBlockEntity clock) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }
}
