package com.hitori.game;

import com.hitori.entity.FixedCamera;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * Who sees what (docs/DESIGN.md §3.1, §3.4): the player's own eyes (the doll freezes), the camera the player watches
 * in the camera app (the doll moves in stiff stop-motion steps; the woman is only there), the slit of the fusuma
 * while peeking. A view is a frustum (vertical field of view, aspect) plus a clear line of sight.
 */
public final class Watch {
    /** The eyes: a bit narrower than the screen (what you are looking at, not the edge of your sight). */
    private static final double EYE_TAN_V = Math.tan(Math.toRadians(30.0)), EYE_ASPECT = 1.5, EYE_RANGE = 20.0;
    /** A camera feed: 70° vertical on a 16:9 screen. */
    private static final double CAM_TAN_V = Math.tan(Math.toRadians(35.0)), CAM_ASPECT = 1.6, CAM_RANGE = 18.0;
    /** The slit of the fusuma. */
    private static final double SLIT_TAN_V = Math.tan(Math.toRadians(32.0)), SLIT_ASPECT = 0.45, SLIT_RANGE = 14.0;

    private Watch() {}

    /** Do this player's own eyes see the point? (Not while watching a feed or with eyes closed; through the gap when peeking.) */
    public static boolean eyesOn(ServerPlayer p, Vec3 at) {
        PlayerState ps = GameManager.state(p);
        if (ps.inApp() || ps.eyesClosed || ps.caught() || p.isSpectator()) return false;
        if (ps.hiding()) {
            if (!ps.peeking || !ps.inCloset()) return false;
            return sees(p.level(), Hiding.peekEye(), p.getYRot(), 0.0f, SLIT_TAN_V, SLIT_ASPECT, SLIT_RANGE, at);
        }
        return sees(p.level(), p.getEyePosition(), p.getYRot(), p.getXRot(), EYE_TAN_V, EYE_ASPECT, EYE_RANGE, at);
    }

    /** Does the camera this player watches in the app see the point? */
    public static boolean cameraOn(ServerPlayer p, Vec3 at) {
        PlayerState ps = GameManager.state(p);
        if (!ps.inApp()) return false;
        FixedCamera cam = Cameras.entity(p.serverLevel(), ps.appCamera);
        if (cam == null) return false;
        return cameraSees(cam, at);
    }

    public static boolean cameraSees(FixedCamera cam, Vec3 at) {
        return sees(cam.level(), cam.getEyePosition(), cam.getYRot(), cam.getXRot(), CAM_TAN_V, CAM_ASPECT, CAM_RANGE, at);
    }

    /** Frustum test plus line of sight. */
    public static boolean sees(Level level, Vec3 eye, float yaw, float pitch, double tanV, double aspect, double range, Vec3 at) {
        Vec3 to = at.subtract(eye);
        double d = to.length();
        if (d > range) return false;
        if (d < 0.35) return true;
        Vec3 f = Vec3.directionFromRotation(pitch, yaw);
        Vec3 r = f.cross(new Vec3(0.0, 1.0, 0.0));
        if (r.lengthSqr() < 1e-6) r = new Vec3(1.0, 0.0, 0.0);
        r = r.normalize();
        Vec3 u = r.cross(f).normalize();
        double z = to.dot(f);
        if (z < 0.2) return false;
        double x = Math.abs(to.dot(r)) / z, y = Math.abs(to.dot(u)) / z;
        if (y > tanV || x > tanV * aspect) return false;
        return Sight.clear(level, eye, at);
    }

    /** Is the player lit (a lamp on near them, or the phone's light)? */
    public static boolean lit(ServerPlayer p) {
        if (GameManager.phoneLit(p)) return true;
        return p.level().getBrightness(LightLayer.BLOCK, BlockPos.containing(p.getEyePosition())) >= 8;
    }
}
