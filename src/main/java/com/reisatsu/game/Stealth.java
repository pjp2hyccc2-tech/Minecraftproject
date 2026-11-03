package com.reisatsu.game;

import com.reisatsu.ReisatsuConfig;
import com.reisatsu.entity.Spirit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * How visible you are (docs/DESIGN.md §3.3). Range: 6 blocks in the dark, 14 when lit (block light ≥ 8 at your eyes,
 * or your flashlight is on and points within 60° of the spirit), ×0.6 while sneaking, ×1.15 per 霊障 level. The rate
 * grows with closeness and with how central you are in its view: dead centre at half range ≈ 1.2 s to notice.
 */
public final class Stealth {
    private Stealth() {}

    public static boolean lit(ServerPlayer p) {
        BlockPos eye = BlockPos.containing(p.getEyePosition());
        return p.level().getBrightness(LightLayer.BLOCK, eye) >= 8;
    }

    /** Is the player's flashlight on and pointing within {@code degrees} of this point? */
    public static boolean beamOn(ServerPlayer p, Vec3 at, double degrees) {
        if (!GameManager.flashlightOn(p)) return false;
        Vec3 to = at.subtract(p.getEyePosition());
        double d = to.length();
        if (d < 1e-3) return true;
        return to.scale(1.0 / d).dot(p.getViewVector(1.0f)) >= Math.cos(Math.toRadians(degrees));
    }

    public static float range(ServerPlayer p, Spirit s) {
        if (s.fixedRange() > 0.0f) return s.fixedRange();
        boolean lit = lit(p) || beamOn(p, s.sightOrigin(), 60.0);
        float r = lit ? 14.0f : 6.0f;
        if (p.isShiftKeyDown()) r *= 0.6f;
        r *= (float) Math.pow(1.15, GameManager.reishou(p));
        return r * s.rangeMul();
    }

    /** Awareness per tick this spirit gains from seeing the player now (0 when it cannot see them). */
    public static float sightRate(Spirit s, ServerPlayer p) {
        PlayerState ps = GameManager.state(p);
        if (ps.hiding() && !s.sawHide(p)) return 0.0f;
        float half = s.coneHalfAngle();
        if (half <= 0.0f) return 0.0f;
        Vec3 eye = s.sightOrigin();
        Vec3 head = p.getEyePosition();
        Vec3 to = head.subtract(eye);
        double d = to.length();
        float range = range(p, s);
        if (d > range || d < 1e-3) return 0.0f;
        double cos = Mth.clamp(to.scale(1.0 / d).dot(s.sightDir()), -1.0, 1.0);
        double angle = Math.toDegrees(Math.acos(cos));
        if (angle > half) return 0.0f;
        if (!Sight.clear(p.level(), eye, head) && !Sight.clear(p.level(), eye, p.position().add(0.0, p.getBbHeight() * 0.45, 0.0))) return 0.0f;
        double closeness = 1.0 - d / range;
        double centrality = 1.0 - angle / half;
        double perSec = (closeness / 0.5) * (0.35 + 0.65 * centrality) / 1.2;
        perSec *= ReisatsuConfig.awarenessRate() * (1.0 + 0.15 * GameManager.reishou(p));
        if (beamOn(p, eye, 25.0)) perSec *= 1.6;
        return (float) (perSec / 20.0);
    }

    /**
     * Awareness per tick from an eye that is not a spirit's own (a security camera): cone, fixed range, line of sight,
     * no light needed. Hidden players are not seen.
     */
    public static float rateFrom(Vec3 eye, Vec3 dir, float half, float range, ServerPlayer p) {
        if (GameManager.state(p).hiding()) return 0.0f;
        Vec3 head = p.getEyePosition();
        Vec3 to = head.subtract(eye);
        double d = to.length();
        if (d > range || d < 1e-3) return 0.0f;
        double angle = Math.toDegrees(Math.acos(Mth.clamp(to.scale(1.0 / d).dot(dir), -1.0, 1.0)));
        if (angle > half) return 0.0f;
        if (!Sight.clear(p.level(), eye, head)) return 0.0f;
        double perSec = ((1.0 - d / range) / 0.5) * (0.35 + 0.65 * (1.0 - angle / half)) / 1.2;
        perSec *= ReisatsuConfig.awarenessRate() * (1.0 + 0.15 * GameManager.reishou(p));
        return (float) (perSec / 20.0);
    }

    /** Could this spirit see the player right now, cone and range included (used for "it saw you go in")? */
    public static boolean sees(Spirit s, ServerPlayer p) {
        float half = s.coneHalfAngle();
        if (half <= 0) return false;
        Vec3 eye = s.sightOrigin();
        Vec3 to = p.getEyePosition().subtract(eye);
        double d = to.length();
        if (d > range(p, s) || d < 1e-3) return false;
        double angle = Math.toDegrees(Math.acos(Mth.clamp(to.scale(1.0 / d).dot(s.sightDir()), -1.0, 1.0)));
        return angle <= half && Sight.clear(p.level(), eye, p.getEyePosition());
    }

    /** Line of sight between two entities' eyes (no cone): the flash alerting spirits that can see you. */
    public static boolean lineOfSight(Entity a, Entity b) {
        return Sight.clear(a.level(), a.getEyePosition(), b.getEyePosition());
    }
}
