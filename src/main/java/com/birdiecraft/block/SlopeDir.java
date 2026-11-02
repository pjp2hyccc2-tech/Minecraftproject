package com.birdiecraft.block;

import net.minecraft.util.StringRepresentable;

/** Downhill direction of a green block. N = -Z, E = +X. */
public enum SlopeDir implements StringRepresentable {
    NONE("none", -1),
    N("n", 0),
    NE("ne", 1),
    E("e", 2),
    SE("se", 3),
    S("s", 4),
    SW("sw", 5),
    W("w", 6),
    NW("nw", 7);

    public static final SlopeDir[] VALUES = values();

    private final String serialized;
    /** Index used by the physics terrain (0..7) or -1 for flat. */
    public final int index;

    SlopeDir(String serialized, int index) {
        this.serialized = serialized;
        this.index = index;
    }

    @Override
    public String getSerializedName() {
        return serialized;
    }

    public SlopeDir next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static SlopeDir fromIndex(int index) {
        if (index < 0 || index > 7) return NONE;
        return VALUES[index + 1];
    }

    /** Quantises a downhill vector (x, z) into one of 8 directions. */
    public static SlopeDir fromVector(double x, double z) {
        if (Math.abs(x) < 1.0e-9 && Math.abs(z) < 1.0e-9) return NONE;
        // angle measured clockwise from north (-Z)
        double ang = Math.toDegrees(Math.atan2(x, -z));
        if (ang < 0) ang += 360.0;
        int idx = (int) Math.round(ang / 45.0) % 8;
        return fromIndex(idx);
    }
}
