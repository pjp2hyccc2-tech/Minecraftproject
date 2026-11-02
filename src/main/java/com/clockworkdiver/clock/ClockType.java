package com.clockworkdiver.clock;

import java.util.List;

/**
 * The kinds of clocks customers bring in. Each has its own beat (ticks per beat inside the clock), a
 * defect count range, a base reward, the reputation needed before such customers show up, the defect
 * kinds it can suffer from and anchor points (normalised -1..1 coordinates on the clock drawing) where
 * faulty parts can hide.
 */
public enum ClockType {
    POCKET_WATCH("pocket_watch", 20, 1, 2, 10, 0, 0,
            List.of(DefectType.RUST, DefectType.TEETH, DefectType.SCREW),
            new float[][]{{-0.42f, 0.28f}, {0.38f, -0.30f}, {0.05f, 0.52f}, {-0.25f, -0.45f}, {0.50f, 0.22f}, {-0.55f, -0.05f}}),
    CUCKOO("cuckoo", 18, 2, 2, 16, 0, 1,
            List.of(DefectType.RUST, DefectType.TEETH, DefectType.SPRING, DefectType.DUST),
            new float[][]{{-0.35f, -0.10f}, {0.35f, -0.05f}, {0.00f, 0.38f}, {-0.30f, 0.45f}, {0.32f, 0.42f}, {0.00f, -0.42f}}),
    ASTRONOMICAL("astronomical", 16, 2, 3, 24, 3, 2,
            List.of(DefectType.TEETH, DefectType.SCREW, DefectType.SPRING, DefectType.TUNING, DefectType.NEST),
            new float[][]{{-0.45f, -0.35f}, {0.45f, -0.35f}, {0.00f, 0.55f}, {-0.50f, 0.30f}, {0.52f, 0.28f}, {0.00f, -0.05f}}),
    BIRDCAGE("birdcage", 16, 2, 3, 28, 5, 2,
            List.of(DefectType.RUST, DefectType.SCREW, DefectType.SPRING, DefectType.DUST, DefectType.NEST),
            new float[][]{{-0.30f, -0.20f}, {0.30f, -0.25f}, {0.00f, 0.10f}, {-0.35f, 0.50f}, {0.36f, 0.48f}, {0.00f, -0.55f}}),
    STEAM_TOWER("steam_tower", 14, 3, 3, 34, 7, 3,
            List.of(DefectType.RUST, DefectType.TEETH, DefectType.SCREW, DefectType.SPRING, DefectType.DUST, DefectType.NEST),
            new float[][]{{-0.20f, -0.55f}, {0.22f, -0.50f}, {0.00f, -0.15f}, {-0.25f, 0.25f}, {0.25f, 0.30f}, {0.00f, 0.62f}}),
    ORRERY("orrery", 12, 3, 4, 45, 10, 4,
            List.of(DefectType.RUST, DefectType.TEETH, DefectType.SCREW, DefectType.SPRING, DefectType.TUNING, DefectType.NEST),
            new float[][]{{-0.55f, 0.00f}, {0.55f, 0.00f}, {0.00f, 0.55f}, {0.00f, -0.55f}, {-0.36f, -0.36f}, {0.38f, 0.36f}, {0.00f, 0.00f}}),
    MUSIC_BOX("music_box", 18, 2, 3, 30, 14, 2,
            List.of(DefectType.SPRING, DefectType.DUST, DefectType.TEETH, DefectType.TUNING),
            new float[][]{{-0.50f, 0.30f}, {0.50f, 0.30f}, {0.00f, -0.45f}, {-0.30f, -0.20f}, {0.32f, -0.15f}, {0.00f, 0.55f}}),
    GRANDFATHER("grandfather", 20, 2, 3, 32, 14, 3,
            List.of(DefectType.SCREW, DefectType.SPRING, DefectType.TUNING, DefectType.RUST, DefectType.TEETH),
            new float[][]{{0.00f, -0.55f}, {-0.35f, -0.15f}, {0.35f, -0.20f}, {0.00f, 0.20f}, {-0.30f, 0.50f}, {0.30f, 0.50f}}),
    /** The town's Great Clock: story only, never brought in by ordinary customers. */
    GREAT_CLOCK("great_clock", 14, 3, 4, 60, 100000, 5,
            List.of(DefectType.RUST, DefectType.TEETH, DefectType.SCREW, DefectType.SPRING, DefectType.DUST, DefectType.TUNING),
            new float[][]{{-0.50f, -0.35f}, {0.50f, -0.35f}, {0.00f, 0.60f}, {-0.55f, 0.30f}, {0.55f, 0.30f}, {0.00f, -0.62f}, {0.00f, 0.00f}});

    public final String id;
    /** Game ticks per beat while inside this clock. */
    public final int period;
    public final int minDefects;
    public final int maxDefects;
    public final int baseReward;
    public final int minReputation;
    public final int difficulty;
    public final List<DefectType> defectPool;
    public final float[][] anchors;

    ClockType(String id, int period, int minDefects, int maxDefects, int baseReward, int minReputation, int difficulty,
              List<DefectType> defectPool, float[][] anchors) {
        this.id = id;
        this.period = period;
        this.minDefects = minDefects;
        this.maxDefects = maxDefects;
        this.baseReward = baseReward;
        this.minReputation = minReputation;
        this.difficulty = difficulty;
        this.defectPool = defectPool;
        this.anchors = anchors;
    }

    /** Tall clocks are drawn a little smaller on the bench. */
    public boolean tall() {
        return this == CUCKOO || this == BIRDCAGE || this == STEAM_TOWER || this == GRANDFATHER;
    }

    public String nameKey() {
        return "clock.clockworkdiver." + id;
    }

    public String descKey() {
        return "clock.clockworkdiver." + id + ".desc";
    }

    public static ClockType byId(String id) {
        for (ClockType t : values()) {
            if (t.id.equals(id)) return t;
        }
        return POCKET_WATCH;
    }
}
