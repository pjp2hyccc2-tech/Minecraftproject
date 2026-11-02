package com.birdiecraft.client;

import com.birdiecraft.BirdieCraft;
import com.birdiecraft.block.FlagColor;
import com.birdiecraft.block.GreenBlock;
import com.birdiecraft.block.SlopeDir;
import com.birdiecraft.config.GolfConfig;
import com.birdiecraft.golf.engine.GolfUnits;
import com.birdiecraft.golf.engine.Lie;
import com.birdiecraft.golf.engine.ShotCalculator;
import com.birdiecraft.golf.engine.Terrain;
import com.birdiecraft.match.MatchState;
import com.birdiecraft.match.MatchView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** In-world golf visuals: balls, trails, shadows, landing ring, aim line, putting grid, pin beam. */
public final class WorldRenderer {
    private static final ResourceLocation BALL = BirdieCraft.id("textures/misc/golf_ball.png");
    private static final ResourceLocation RING = BirdieCraft.id("textures/misc/landing_ring.png");
    private static final ResourceLocation SHADOW = BirdieCraft.id("textures/misc/ball_shadow.png");
    private static final float BALL_SIZE = 0.085f;

    private WorldRenderer() {
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack ps = event.getPoseStack();
        float partial = event.getPartialTick();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);

        MatchView m = ClientGolfState.match;
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();

        // --- translucent colour geometry (beam, grid, aim line, trails)
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        if (m != null && m.state != MatchState.LOBBY && GolfConfig.SHOW_PIN_BEACON.get()) {
            int rgb = FlagColor.byId(m.flagColor).rgb;
            drawBeam(ps, quads, m.cup.getX() + 0.5, m.cup.getY() + 1.0, m.cup.getZ() + 0.5, rgb, cam, mc.level.getGameTime() + partial);
        }
        boolean aiming = SwingController.holdingClub() && (SwingController.canSwing() || SwingController.isActive());
        if (aiming) {
            Vec3 o = SwingController.origin();
            if (SwingController.club.isPutter()) {
                drawPuttGrid(ps, quads, mc.level, o, mc.level.getGameTime() + partial);
                double range = GolfUnits.toBlocks(ShotCalculator.PUTT_RANGES_YARDS[SwingController.puttRange]);
                drawAimLine(ps, quads, o, mc.player.getYRot(), range, 0xFFFFFF);
            } else if (GolfConfig.SHOW_LANDING_PREVIEW.get() && LandingPreview.full() != null) {
                Vec3 land = LandingPreview.full();
                drawAimLine(ps, quads, o, mc.player.getYRot(), GolfUnits.horizontalDistance(o.x, o.z, land.x, land.z), 0xFFFFFF);
            }
        }
        for (BallFlight f : ClientGolfState.flights) {
            if (!f.started()) continue;
            drawTrail(ps, quads, f, partial, cam);
        }
        buffers.endBatch(RenderType.debugQuads());

        // --- textured: rings and shadows
        VertexConsumer rings = buffers.getBuffer(RenderType.entityTranslucent(RING));
        if (aiming && !SwingController.club.isPutter() && GolfConfig.SHOW_LANDING_PREVIEW.get()) {
            float pulse = 0.85f + 0.15f * (float) Math.sin((mc.level.getGameTime() + partial) * 0.25);
            Vec3 land = LandingPreview.full();
            if (land != null) {
                float size = (float) Math.max(0.9, cam.distanceTo(land) * 0.022);
                flat(ps, rings, land.x, land.y + 0.03, land.z, size * pulse, 1f, 1f, 1f, 0.95f);
            }
            Vec3 pw = LandingPreview.atCurrentPower();
            if (pw != null) {
                float size = (float) Math.max(0.55, cam.distanceTo(pw) * 0.015);
                flat(ps, rings, pw.x, pw.y + 0.035, pw.z, size, 1f, 0.85f, 0.25f, 0.95f);
            }
        }
        if (m != null) {
            // coloured ring under every resting ball so players can spot them
            for (MatchView.PView p : m.players) {
                if (p.holed || ClientGolfState.isFlying(p.id)) continue;
                float[] c = rgb(p.color);
                flat(ps, rings, p.bx, p.by + 0.02, p.bz, 0.32f, c[0], c[1], c[2], 0.9f);
            }
        }
        buffers.endBatch(RenderType.entityTranslucent(RING));

        VertexConsumer shadows = buffers.getBuffer(RenderType.entityTranslucent(SHADOW));
        for (BallFlight f : ClientGolfState.flights) {
            if (!f.started()) continue;
            Vec3 p = f.pos(partial);
            double gy = groundBelow(mc.level, p);
            if (gy > -1000 && p.y - gy > 0.15) {
                float s = (float) Math.max(0.06, 0.16 - (p.y - gy) * 0.004);
                flat(ps, shadows, p.x, gy + 0.02, p.z, s, 1f, 1f, 1f, 0.7f);
            }
        }
        buffers.endBatch(RenderType.entityTranslucent(SHADOW));

        // --- balls
        VertexConsumer balls = buffers.getBuffer(RenderType.entityCutoutNoCull(BALL));
        if (m != null) {
            for (MatchView.PView p : m.players) {
                if (p.holed || ClientGolfState.isFlying(p.id)) continue;
                billboard(ps, balls, p.bx, p.by + BALL_SIZE, p.bz, BALL_SIZE, left, up);
            }
        }
        for (BallFlight f : ClientGolfState.flights) {
            if (f.finished() && f.result != com.birdiecraft.golf.engine.Trajectory.Result.REST && f.result != com.birdiecraft.golf.engine.Trajectory.Result.TIMEOUT) continue;
            if (f.finished() && !f.practice) continue;
            Vec3 p = f.pos(partial);
            billboard(ps, balls, p.x, p.y + BALL_SIZE, p.z, BALL_SIZE, left, up);
        }
        buffers.endBatch(RenderType.entityCutoutNoCull(BALL));
        ps.popPose();
    }

    // ------------------------------------------------------------------ primitives

    private static float[] rgb(int c) {
        return new float[]{((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f};
    }

    private static void billboard(PoseStack ps, VertexConsumer vc, double x, double y, double z, float s, Vector3f left, Vector3f up) {
        Matrix4f mat = ps.last().pose();
        Matrix3f nrm = ps.last().normal();
        float lx = left.x() * s, ly = left.y() * s, lz = left.z() * s;
        float ux = up.x() * s, uy = up.y() * s, uz = up.z() * s;
        int light = LightTexture.FULL_BRIGHT;
        vtx(vc, mat, nrm, x + lx - ux, y + ly - uy, z + lz - uz, 0, 1, 1, 1, 1, 1, light);
        vtx(vc, mat, nrm, x - lx - ux, y - ly - uy, z - lz - uz, 1, 1, 1, 1, 1, 1, light);
        vtx(vc, mat, nrm, x - lx + ux, y - ly + uy, z - lz + uz, 1, 0, 1, 1, 1, 1, light);
        vtx(vc, mat, nrm, x + lx + ux, y + ly + uy, z + lz + uz, 0, 0, 1, 1, 1, 1, light);
    }

    /** Horizontal textured square (both faces). */
    private static void flat(PoseStack ps, VertexConsumer vc, double x, double y, double z, float s,
                             float r, float g, float b, float a) {
        Matrix4f mat = ps.last().pose();
        Matrix3f nrm = ps.last().normal();
        int light = LightTexture.FULL_BRIGHT;
        vtx(vc, mat, nrm, x - s, y, z - s, 0, 0, r, g, b, a, light);
        vtx(vc, mat, nrm, x - s, y, z + s, 0, 1, r, g, b, a, light);
        vtx(vc, mat, nrm, x + s, y, z + s, 1, 1, r, g, b, a, light);
        vtx(vc, mat, nrm, x + s, y, z - s, 1, 0, r, g, b, a, light);
    }

    private static void vtx(VertexConsumer vc, Matrix4f mat, Matrix3f nrm, double x, double y, double z, float u, float v,
                            float r, float g, float b, float a, int light) {
        vc.vertex(mat, (float) x, (float) y, (float) z).color(r, g, b, a).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nrm, 0f, 1f, 0f).endVertex();
    }

    private static void quad(VertexConsumer vc, Matrix4f mat, double x1, double y1, double z1, double x2, double y2, double z2,
                             double x3, double y3, double z3, double x4, double y4, double z4, int argb) {
        int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        vc.vertex(mat, (float) x1, (float) y1, (float) z1).color(r, g, b, a).endVertex();
        vc.vertex(mat, (float) x2, (float) y2, (float) z2).color(r, g, b, a).endVertex();
        vc.vertex(mat, (float) x3, (float) y3, (float) z3).color(r, g, b, a).endVertex();
        vc.vertex(mat, (float) x4, (float) y4, (float) z4).color(r, g, b, a).endVertex();
    }

    private static void drawBeam(PoseStack ps, VertexConsumer vc, double x, double y, double z, int rgb, Vec3 cam, double time) {
        double dist = Math.sqrt((x - cam.x) * (x - cam.x) + (z - cam.z) * (z - cam.z));
        if (dist < 6) return;
        Matrix4f mat = ps.last().pose();
        double w = Math.min(0.6, 0.06 + dist * 0.004);
        double h = 3.0 + Math.min(18.0, dist * 0.12);
        // face the camera horizontally
        double dx = cam.x - x;
        double dz = cam.z - z;
        double len = Math.max(1.0e-4, Math.sqrt(dx * dx + dz * dz));
        double px = -dz / len * w;
        double pz = dx / len * w;
        int alpha = (int) (110 + 40 * Math.sin(time * 0.15));
        int col = (alpha << 24) | (rgb & 0xFFFFFF);
        int top = (rgb & 0xFFFFFF);
        quadGradient(vc, mat, x - px, y, z - pz, x + px, y, z + pz, x + px, y + h, z + pz, x - px, y + h, z - pz, col, top);
    }

    private static void quadGradient(VertexConsumer vc, Matrix4f mat, double x1, double y1, double z1, double x2, double y2, double z2,
                                     double x3, double y3, double z3, double x4, double y4, double z4, int bottom, int top) {
        int ab = (bottom >>> 24) & 0xFF, rb = (bottom >> 16) & 0xFF, gb = (bottom >> 8) & 0xFF, bb = bottom & 0xFF;
        int at = (top >>> 24) & 0xFF, rt = (top >> 16) & 0xFF, gt = (top >> 8) & 0xFF, bt = top & 0xFF;
        vc.vertex(mat, (float) x1, (float) y1, (float) z1).color(rb, gb, bb, ab).endVertex();
        vc.vertex(mat, (float) x2, (float) y2, (float) z2).color(rb, gb, bb, ab).endVertex();
        vc.vertex(mat, (float) x3, (float) y3, (float) z3).color(rt, gt, bt, at).endVertex();
        vc.vertex(mat, (float) x4, (float) y4, (float) z4).color(rt, gt, bt, at).endVertex();
    }

    private static void drawTrail(PoseStack ps, VertexConsumer vc, BallFlight f, float partial, Vec3 cam) {
        Matrix4f mat = ps.last().pose();
        int end = Math.min(f.age(), f.length() - 1);
        int start = Math.max(0, end - 36);
        if (end - start < 1) return;
        int rgb = f.color & 0xFFFFFF;
        for (int i = start; i < end; i++) {
            if (f.hasLanded() && i > 0 && Math.abs(f.ys[i] - f.ys[i - 1]) < 0.002 && i > end - 2) break;
            double x1 = f.xs[i], y1 = f.ys[i] + BALL_SIZE, z1 = f.zs[i];
            double x2 = f.xs[i + 1], y2 = f.ys[i + 1] + BALL_SIZE, z2 = f.zs[i + 1];
            // ribbon facing the camera
            double sx = x2 - x1, sy = y2 - y1, sz = z2 - z1;
            double vx = (x1 + x2) * 0.5 - cam.x, vy = (y1 + y2) * 0.5 - cam.y, vz = (z1 + z2) * 0.5 - cam.z;
            double nx = sy * vz - sz * vy, ny = sz * vx - sx * vz, nz = sx * vy - sy * vx;
            double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl < 1.0e-6) continue;
            float k = (float) (i - start) / (end - start);
            double w = 0.012 + 0.03 * k;
            nx = nx / nl * w;
            ny = ny / nl * w;
            nz = nz / nl * w;
            int a = (int) (200 * k * k);
            int col = (a << 24) | rgb;
            quad(vc, mat, x1 - nx, y1 - ny, z1 - nz, x1 + nx, y1 + ny, z1 + nz, x2 + nx, y2 + ny, z2 + nz, x2 - nx, y2 - ny, z2 - nz, col);
        }
    }

    private static void drawAimLine(PoseStack ps, VertexConsumer vc, Vec3 o, float yaw, double length, int rgb) {
        Matrix4f mat = ps.last().pose();
        double dx = -Math.sin(Math.toRadians(yaw));
        double dz = Math.cos(Math.toRadians(yaw));
        double px = -dz * 0.035;
        double pz = dx * 0.035;
        double y = o.y + 0.03;
        for (double d = 0.6; d < length; d += 0.9) {
            double e = Math.min(length, d + 0.45);
            int a = (int) (170 * (1.0 - d / Math.max(1.0, length) * 0.6));
            quad(vc, mat, o.x + dx * d - px, y, o.z + dz * d - pz, o.x + dx * d + px, y, o.z + dz * d + pz,
                    o.x + dx * e + px, y, o.z + dz * e + pz, o.x + dx * e - px, y, o.z + dz * e - pz, (a << 24) | rgb);
        }
    }

    /** Grid on the green with flowing dots that show the slope direction and strength. */
    private static void drawPuttGrid(PoseStack ps, VertexConsumer vc, Level level, Vec3 o, double time) {
        Matrix4f mat = ps.last().pose();
        int bx = (int) Math.floor(o.x);
        int bz = (int) Math.floor(o.z);
        int baseY = (int) Math.floor(o.y - 0.02);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int r = 10;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) continue;
                int gx = bx + dx;
                int gz = bz + dz;
                int gy = -999;
                BlockState st = null;
                for (int dy = 2; dy >= -3; dy--) {
                    p.set(gx, baseY + dy, gz);
                    BlockState s = level.getBlockState(p);
                    if (s.getBlock() instanceof GreenBlock || s.is(com.birdiecraft.registry.ModBlocks.GOLF_CUP.get())) {
                        gy = baseY + dy;
                        st = s;
                        break;
                    }
                }
                if (st == null) continue;
                double y = gy + 1.012;
                float fade = 1f - (float) Math.sqrt(dx * dx + dz * dz) / (r + 1);
                int lineA = (int) (150 * fade);
                int line = (lineA << 24) | 0xFFFFFF;
                double t = 0.045;
                // west and north edges of the cell
                quad(vc, mat, gx, y, gz, gx, y, gz + 1, gx + t, y, gz + 1, gx + t, y, gz, line);
                quad(vc, mat, gx, y, gz, gx + 1, y, gz, gx + 1, y, gz + t, gx, y, gz + t, line);
                if (!(st.getBlock() instanceof GreenBlock)) continue;
                SlopeDir dir = st.getValue(GreenBlock.SLOPE);
                if (dir == SlopeDir.NONE) continue;
                int steep = st.getValue(GreenBlock.STEEP);
                double sx = Terrain.SLOPE_DX[dir.index];
                double sz = Terrain.SLOPE_DZ[dir.index];
                double speed = 0.2 + 0.25 * steep;
                int color = steep == 1 ? 0x7FD6FF : (steep == 2 ? 0xFFE066 : 0xFF6A4A);
                for (int k = 0; k < 2; k++) {
                    double phase = (time * 0.02 * speed + k * 0.5 + ((gx * 31 + gz * 17) & 7) * 0.125) % 1.0;
                    double cx = gx + 0.5 + sx * (phase - 0.5) * 0.8;
                    double cz = gz + 0.5 + sz * (phase - 0.5) * 0.8;
                    double s = 0.08 + 0.03 * steep;
                    int a = (int) (220 * fade * Math.sin(phase * Math.PI));
                    // small arrow-ish diamond pointing downhill
                    double fx = sx * s * 1.6, fz = sz * s * 1.6;
                    double qx = -sz * s, qz = sx * s;
                    quad(vc, mat, cx - fx, y + 0.002, cz - fz, cx + qx, y + 0.002, cz + qz, cx + fx, y + 0.002, cz + fz,
                            cx - qx, y + 0.002, cz - qz, (a << 24) | color);
                }
            }
        }
    }

    private static double groundBelow(Level level, Vec3 p) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int x = (int) Math.floor(p.x);
        int z = (int) Math.floor(p.z);
        int y0 = (int) Math.floor(p.y);
        for (int y = y0; y > y0 - 48; y--) {
            m.set(x, y, z);
            BlockState s = level.getBlockState(m);
            if (!s.getFluidState().isEmpty()) return y + 0.9;
            if (!s.getCollisionShape(level, m).isEmpty()) {
                return y + s.getCollisionShape(level, m).max(net.minecraft.core.Direction.Axis.Y);
            }
        }
        return -10000;
    }

    @SuppressWarnings("unused")
    private static boolean onGreen() {
        return SwingController.lie == Lie.GREEN;
    }
}
