package com.mcbesiege.part;

import com.mcbesiege.physics.Quat;
import com.mcbesiege.physics.V3;
import net.minecraft.core.Direction;

/**
 * Maps the canonical part orientation (facing UP, twist axis X) to a block facing / twist, using exactly the same
 * rotations as the generated blockstate files (x rotation first, then y; twist = canonical model pre-rotated by y=90).
 */
public final class Orient {
    private Orient() {}

    public static int xRot(Direction f) {
        return switch (f) {
            case UP -> 0;
            case DOWN -> 180;
            default -> 90;
        };
    }

    public static int yRot(Direction f) {
        return switch (f) {
            case SOUTH -> 180;
            case WEST -> 270;
            case EAST -> 90;
            default -> 0;
        };
    }

    public static Quat quat(Direction f, boolean twist) {
        Quat q = Quat.fromAxisAngle(V3.Y, -Math.toRadians(yRot(f))).mul(Quat.fromAxisAngle(V3.X, -Math.toRadians(xRot(f))));
        if (twist) q = q.mul(Quat.fromAxisAngle(V3.Y, -Math.PI / 2));
        return q;
    }

    /** Type-aware rotation: player-facing parts (starting block, seat) only turn around Y, like their blockstates. */
    public static Quat quat(PartType t, Direction f, boolean twist) {
        if (t.place == PartType.Place.PLAYER_H) return Quat.fromAxisAngle(V3.Y, -Math.toRadians(yRot(f)));
        return quat(f, twist);
    }

    public static double[] box(PartType t, double[] px, Direction f, boolean twist) {
        Quat q = quat(t, f, twist);
        V3 a = rot(q, px[0], px[1], px[2]);
        V3 b = rot(q, px[3], px[4], px[5]);
        return new double[]{
                round(Math.min(a.x, b.x)), round(Math.min(a.y, b.y)), round(Math.min(a.z, b.z)),
                round(Math.max(a.x, b.x)), round(Math.max(a.y, b.y)), round(Math.max(a.z, b.z))};
    }

    private static V3 rot(Quat q, double px, double py, double pz) {
        V3 r = q.rotate(new V3(px / 16.0 - 0.5, py / 16.0 - 0.5, pz / 16.0 - 0.5));
        return new V3(r.x + 0.5, r.y + 0.5, r.z + 0.5);
    }

    public static V3 dir(Direction d) {
        return new V3(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    /** Canonical pixel point -> cell-local point (0..1 cube, possibly outside for big parts). */
    public static V3 point(double px, double py, double pz, Direction f, boolean twist) {
        V3 c = new V3(px / 16.0 - 0.5, py / 16.0 - 0.5, pz / 16.0 - 0.5);
        V3 r = quat(f, twist).rotate(c);
        return new V3(r.x + 0.5, r.y + 0.5, r.z + 0.5);
    }

    /** Rotates a canonical pixel box into a cell-local (0..1) axis aligned box {x0,y0,z0,x1,y1,z1}. */
    public static double[] box(double[] px, Direction f, boolean twist) {
        V3 a = point(px[0], px[1], px[2], f, twist);
        V3 b = point(px[3], px[4], px[5], f, twist);
        return new double[]{
                round(Math.min(a.x, b.x)), round(Math.min(a.y, b.y)), round(Math.min(a.z, b.z)),
                round(Math.max(a.x, b.x)), round(Math.max(a.y, b.y)), round(Math.max(a.z, b.z))};
    }

    private static double round(double v) {
        return Math.round(v * 1024.0) / 1024.0;
    }
}
