package com.birdiecraft.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;

/**
 * Chase camera that follows a flying ball, using a client-only marker entity as the camera.
 */
public final class BallCam {
    private static Marker camera;
    private static BallFlight target;
    private static CameraType previousType;
    private static boolean active;
    private static int holdTicks;
    private static double cx, cy, cz;

    private BallCam() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void follow(BallFlight flight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        target = flight;
        holdTicks = 0;
        if (!active) {
            camera = new Marker(EntityType.MARKER, mc.level);
            Vec3 eye = mc.player.getEyePosition();
            cx = eye.x;
            cy = eye.y;
            cz = eye.z;
            camera.setPos(cx, cy, cz);
            camera.xo = cx;
            camera.yo = cy;
            camera.zo = cz;
            camera.setYRot(mc.player.getYRot());
            camera.setXRot(mc.player.getXRot());
            camera.yRotO = camera.getYRot();
            camera.xRotO = camera.getXRot();
            previousType = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.setCameraEntity(camera);
            active = true;
        }
    }

    public static void stop() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        active = false;
        target = null;
        if (mc.player != null) mc.setCameraEntity(mc.player);
        if (previousType != null) mc.options.setCameraType(previousType);
        camera = null;
    }

    public static void tick() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || camera == null || target == null) {
            stop();
            return;
        }
        if (mc.getCameraEntity() != camera) {
            // someone else (spectator mode, another mod) took the camera
            active = false;
            target = null;
            return;
        }
        if (!target.started()) {
            camera.xo = camera.getX();
            camera.yo = camera.getY();
            camera.zo = camera.getZ();
            return;
        }
        if (target.finished()) {
            holdTicks++;
            if (holdTicks > 30) {
                stop();
                return;
            }
        }
        Vec3 ball = target.pos(1f);
        Vec3 dir = target.horizontalDirection();
        boolean rolling = target.hasLanded();
        double back = rolling ? 4.0 : 6.0;
        double up = rolling ? 1.6 : 2.4;
        double tx = ball.x - dir.x * back;
        double ty = ball.y + up;
        double tz = ball.z - dir.z * back;
        double k = target.age() < 6 ? 0.35 : 0.2;
        cx += (tx - cx) * k;
        cy += (ty - cy) * k;
        cz += (tz - cz) * k;
        camera.xo = camera.getX();
        camera.yo = camera.getY();
        camera.zo = camera.getZ();
        camera.setPos(cx, cy, cz);
        double dx = ball.x - cx;
        double dy = ball.y - cy;
        double dz = ball.z - cz;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        camera.yRotO = camera.getYRot();
        camera.xRotO = camera.getXRot();
        // avoid spinning the long way round
        float prev = camera.getYRot();
        float delta = (float) Math.IEEEremainder(yaw - prev, 360.0);
        camera.setYRot(prev + delta);
        camera.setXRot(pitch);
    }
}
