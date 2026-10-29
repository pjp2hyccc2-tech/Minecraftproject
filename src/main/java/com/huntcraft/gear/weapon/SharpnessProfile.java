package com.huntcraft.gear.weapon;

/**
 * Sharpness gauge helpers. A profile is 7 segment lengths (RED, ORANGE, YELLOW, GREEN, BLUE, WHITE, PURPLE) in
 * sharpness points. The stack stores the remaining points in NBT ({@link #TAG}); every hit consumes points from the
 * top of the gauge. Handicraft and upgrade levels extend the gauge towards the maximum profile.
 */
public final class SharpnessProfile {
    public static final String TAG = "HCSharp";
    public static final int COLORS = 7;

    private SharpnessProfile() {
    }

    /** Fills `extra` points into the base profile, colour by colour, without exceeding the maximum profile. */
    public static int[] effective(int[] base, int[] max, int extra) {
        int[] out = base.clone();
        for (int i = 0; i < COLORS && extra > 0; i++) {
            int room = max[i] - out[i];
            if (room <= 0) continue;
            int add = Math.min(room, extra);
            out[i] += add;
            extra -= add;
        }
        return out;
    }

    public static int total(int[] seg) {
        int t = 0;
        for (int v : seg) t += v;
        return t;
    }

    /** Sharpness colour index (0 = red .. 6 = purple) for the remaining points. */
    public static int colorIndex(int[] seg, int points) {
        if (points <= 0) return 0;
        int acc = 0;
        int last = 0;
        for (int i = 0; i < COLORS; i++) {
            if (seg[i] <= 0) continue;
            acc += seg[i];
            last = i;
            if (points <= acc) return i;
        }
        return last;
    }

    /** Index of the highest colour present in the profile. */
    public static int topColor(int[] seg) {
        int top = 0;
        for (int i = 0; i < COLORS; i++) if (seg[i] > 0) top = i;
        return top;
    }
}
