package com.birdiecraft.golf.engine;

/** Name of a hole score relative to par. */
public enum ScoreTerm {
    HOLE_IN_ONE(0xFFD700),
    CONDOR(0xFF55FF),
    ALBATROSS(0xFF55FF),
    EAGLE(0xFFAA00),
    BIRDIE(0xFF5555),
    PAR(0xFFFFFF),
    BOGEY(0x55AAFF),
    DOUBLE_BOGEY(0x5577FF),
    TRIPLE_BOGEY(0x7755FF),
    OVER(0xAAAAAA);

    public static final ScoreTerm[] VALUES = values();

    public final int color;

    ScoreTerm(int color) {
        this.color = color;
    }

    public static ScoreTerm of(int strokes, int par) {
        if (strokes == 1) return HOLE_IN_ONE;
        int d = strokes - par;
        if (d <= -4) return CONDOR;
        if (d == -3) return ALBATROSS;
        if (d == -2) return EAGLE;
        if (d == -1) return BIRDIE;
        if (d == 0) return PAR;
        if (d == 1) return BOGEY;
        if (d == 2) return DOUBLE_BOGEY;
        if (d == 3) return TRIPLE_BOGEY;
        return OVER;
    }

    public boolean isUnderPar() {
        return ordinal() <= BIRDIE.ordinal();
    }

    public String translationKey() {
        return "score.birdiecraft." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Colour for a score relative to par on a scorecard. */
    public static int colorForDiff(int diff) {
        if (diff <= -2) return EAGLE.color;
        if (diff == -1) return BIRDIE.color;
        if (diff == 0) return PAR.color;
        if (diff == 1) return BOGEY.color;
        return DOUBLE_BOGEY.color;
    }
}
