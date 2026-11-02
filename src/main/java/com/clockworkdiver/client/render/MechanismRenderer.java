package com.clockworkdiver.client.render;

import com.clockworkdiver.ClockworkDiver;
import com.clockworkdiver.block.MechanismBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Animates the giant clockwork: every gear ticks forward on the beat, stuck ones shudder. */
public class MechanismRenderer implements BlockEntityRenderer<MechanismBlockEntity> {
    private static final ResourceLocation METAL = ClockworkDiver.id("textures/entity/mechanism_metal.png");

    public MechanismRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static int color(int id) {
        return switch (id) {
            case MechanismBlockEntity.COLOR_STEEL -> 0xB8C2CC;
            case MechanismBlockEntity.COLOR_COPPER -> 0xD08A58;
            case MechanismBlockEntity.COLOR_RUST -> 0x9A4A2A;
            case MechanismBlockEntity.COLOR_GOLD -> 0xF2CC6A;
            case MechanismBlockEntity.COLOR_DARK -> 0x5A4A40;
            default -> 0xE0B862;
        };
    }

    @Override
    public void render(MechanismBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        double t = level.getGameTime() + partialTick;
        int lit = LightTexture.pack(Math.max(LightTexture.block(light), 11), LightTexture.sky(light));
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(METAL));
        int color = color(be.color);
        boolean rusted = !be.running && be.kind == MechanismBlockEntity.GEAR && be.color != MechanismBlockEntity.COLOR_RUST;
        if (rusted) color = Mesh3D.shade(color, 0.78f);

        pose.pushPose();
        pose.translate(0.5 + be.offsetX, 0.5 + be.offsetY, 0.5 + be.offsetZ);
        if (be.kind == MechanismBlockEntity.PENDULUM) {
            renderPendulum(be, t, pose, vc, lit, color);
        } else {
            orient(pose, be.axis, be.flip);
            switch (be.kind) {
                case MechanismBlockEntity.ESCAPE_WHEEL -> {
                    pose.mulPose(Axis.YP.rotationDegrees(gearAngle(be, t)));
                    int every = be.missing > 0 ? Math.max(2, be.teeth / be.missing) : 0;
                    Mesh3D.gear(pose, vc, lit, color, be.radius, be.teeth, be.thickness, every, true);
                }
                case MechanismBlockEntity.BALANCE -> {
                    double beats = t / be.period;
                    float swing = be.running ? (float) Math.sin(beats * Math.PI) * be.speed : (float) Math.sin(t * 0.8) * 1.5f;
                    pose.mulPose(Axis.YP.rotationDegrees(swing));
                    Mesh3D.balance(pose, vc, lit, color, be.radius, be.thickness);
                }
                case MechanismBlockEntity.SPRING -> renderSpring(be, t, pose, vc, lit, color);
                case MechanismBlockEntity.CLOCK_FACE -> renderClock(be, t, pose, vc, lit, color);
                default -> {
                    pose.mulPose(Axis.YP.rotationDegrees(gearAngle(be, t)));
                    Mesh3D.gear(pose, vc, lit, color, be.radius, be.teeth, be.thickness, 0, false);
                }
            }
        }
        pose.popPose();
    }

    /** Points local +Y along the mechanism axis; for upright faces local -Z points up. */
    private static void orient(PoseStack pose, int axis, boolean flip) {
        switch (axis) {
            case MechanismBlockEntity.AXIS_X -> {
                pose.mulPose(Axis.YP.rotationDegrees(flip ? 270.0f : 90.0f));
                pose.mulPose(Axis.XP.rotationDegrees(90.0f));
            }
            case MechanismBlockEntity.AXIS_Z -> {
                if (flip) pose.mulPose(Axis.YP.rotationDegrees(180.0f));
                pose.mulPose(Axis.XP.rotationDegrees(90.0f));
            }
            default -> {
                if (flip) pose.mulPose(Axis.XP.rotationDegrees(180.0f));
            }
        }
    }

    /** Escapement motion: a quick jump on every beat, then rest. Stuck mechanisms only shudder. */
    private static float gearAngle(MechanismBlockEntity be, double t) {
        if (!be.running) {
            return be.phase + (float) Math.sin(t * 0.9 + be.phase) * 0.7f;
        }
        double beats = t / be.period;
        if (!be.stepped) return (float) (be.phase + beats * be.speed);
        double whole = Math.floor(beats);
        double frac = beats - whole;
        double ease = frac < 0.2 ? backOut(frac / 0.2) : 1.0;
        return (float) (be.phase + (whole + ease) * be.speed);
    }

    private static double backOut(double x) {
        double c1 = 1.70158, c3 = c1 + 1.0;
        return 1.0 + c3 * Math.pow(x - 1.0, 3) + c1 * Math.pow(x - 1.0, 2);
    }

    private static void renderPendulum(MechanismBlockEntity be, double t, PoseStack pose, VertexConsumer vc, int light, int color) {
        double beats = t / be.period;
        float swing = be.running ? (float) Math.sin(beats * Math.PI) * be.speed : (float) Math.sin(t * 0.7) * 0.8f;
        if (be.axis == MechanismBlockEntity.AXIS_X) pose.mulPose(Axis.XP.rotationDegrees(swing));
        else pose.mulPose(Axis.ZP.rotationDegrees(swing));
        float len = be.radius;
        float w = Math.max(0.03f, len * 0.025f);
        Mesh3D.box(pose, vc, light, Mesh3D.shade(color, 0.9f), -w, -len, -w, w, 0.0f, w);
        Mesh3D.box(pose, vc, light, 0x3A2A1A, -w * 2.5f, -w * 2.5f, -w * 2.5f, w * 2.5f, w * 2.5f, w * 2.5f);
        pose.pushPose();
        pose.translate(0.0, -len, 0.0);
        if (be.axis == MechanismBlockEntity.AXIS_X) pose.mulPose(Axis.ZP.rotationDegrees(-90.0f));
        else pose.mulPose(Axis.XP.rotationDegrees(90.0f));
        Mesh3D.ring(pose, vc, light, color, 0.0f, len * 0.17f, Math.max(0.03f, len * 0.025f), 24);
        Mesh3D.ring(pose, vc, light, Mesh3D.shade(color, 0.7f), len * 0.12f, len * 0.15f, Math.max(0.035f, len * 0.03f), 24);
        pose.popPose();
    }

    private static void renderSpring(MechanismBlockEntity be, double t, PoseStack pose, VertexConsumer vc, int light, int color) {
        float p = Math.max(0.0f, Math.min(1.0f, be.progress));
        float r = be.radius;
        float rot = be.running ? (float) (t * 0.25) : (float) Math.sin(t * 0.6) * (1.0f - p) * 1.5f;
        pose.mulPose(Axis.YP.rotationDegrees(rot));
        float outer = r * (0.95f - 0.5f * p);
        float turns = 3.0f + 6.0f * p;
        float h = be.thickness / 2.0f;
        Mesh3D.spiral(pose, vc, light, 0xA8B4C8, r * 0.12f, outer, turns, h, Math.max(0.05f, r * 0.012f));
        Mesh3D.ring(pose, vc, light, color, r, r * 1.06f, h * 1.1f, 72);
        Mesh3D.gear(pose, vc, light, 0xE0B862, r * 1.08f, 96, 0.3f, 0, false);
        Mesh3D.ring(pose, vc, light, 0xE0B862, 0.0f, r * 0.12f, h * 1.15f, 20);
        Mesh3D.box(pose, vc, light, 0x5A4A40, -r * 0.05f, -h * 1.4f, -r * 0.05f, r * 0.05f, h * 1.4f, r * 0.05f);
    }

    private static void renderClock(MechanismBlockEntity be, double t, PoseStack pose, VertexConsumer vc, int light, int color) {
        double seconds = t / 20.0 + be.phase * 151.0 + 40000.0;
        float sec = (float) (Math.floor(seconds % 60.0) / 60.0 * Math.PI * 2.0);
        float min = (float) ((seconds / 60.0 % 60.0) / 60.0 * Math.PI * 2.0);
        float hour = (float) ((seconds / 3600.0 % 12.0) / 12.0 * Math.PI * 2.0);
        Mesh3D.clockFace(pose, vc, light, color, be.radius, hour, min, sec);
    }

    @Override
    public boolean shouldRenderOffScreen(MechanismBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
