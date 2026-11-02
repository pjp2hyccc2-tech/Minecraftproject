package com.clockworkdiver.clock;

import java.util.ArrayList;
import java.util.List;

/**
 * Twists that make a repair dive harder or stranger. Stored as a bit mask on a {@link Defect}.
 */
public final class DiveMods {
    /** A time limit: the repair fails when it runs out. */
    public static final int TIMED = 1;
    /** The beat is much faster. */
    public static final int FAST = 1 << 1;
    /** Off-beat: strike between the ticks, when the pendulum is at the end of its swing. */
    public static final int OFFBEAT = 1 << 2;
    /** The chamber's jewel lamps are out; the spectacles outline the vermin. */
    public static final int DARK = 1 << 3;
    /** Many more vermin. */
    public static final int SWARM = 1 << 4;
    /** Tougher, faster vermin wreathed in black mist. */
    public static final int ELITE = 1 << 5;
    /** No steam vents: steam only comes from Just Ticks. */
    public static final int NO_VENTS = 1 << 6;
    /** Precision work: a missed beat undoes repair progress. */
    public static final int PRECISION = 1 << 7;

    public static final String[] NAMES = {"timed", "fast", "offbeat", "dark", "swarm", "elite", "novents", "precision"};

    private DiveMods() {}

    public static boolean has(int mods, int flag) {
        return (mods & flag) != 0;
    }

    /** Bit for a name used by the debug command, or 0. */
    public static int byName(String name) {
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(name)) return 1 << i;
        }
        return 0;
    }

    /** Language keys of the modifiers in the mask, in a stable order. */
    public static List<String> keys(int mods) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < NAMES.length; i++) {
            if ((mods & (1 << i)) != 0) out.add("mod.clockworkdiver." + NAMES[i]);
        }
        return out;
    }
}
