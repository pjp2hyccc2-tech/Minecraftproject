package com.hitori.world;

/**
 * The floor plan of one unit of コーポ白樺, exactly as room 203 in docs/DESIGN.md §2 (one character = one block column;
 * column 0 is the unit's west wall, row 0 the corridor wall at z 0). All eight units share it (203 at x 22..33).
 * {@code #} wall, {@code D} front door, {@code d} door, {@code f} fusuma, {@code W} sliding glass doors; room cells:
 * {@code g} genkan, {@code h} hall, {@code t} toilet, {@code b} bath, {@code w} washroom, {@code k} DK, {@code j} / {@code V}
 * tatami room (V: the TV), {@code y} western room, {@code O} 押し入れ, {@code c} the western closet.
 * <p>
 * One change to the drawing: both closets need a side wall at their north end, so the two cells north of them
 * (columns 1 and 10 of row 9) are wall piers.
 */
public final class Plan {
    private Plan() {}

    public static final int W = 12, D = 14;

    private static final String[] ROWS = {
            "##D#########", // z00
            "#ggghh#t#bb#", // z01
            "#ggghhdt#bb#", // z02
            "####hh###d##", // z03
            "#kkkhh#wwww#", // z04
            "#kkkhhdwwww#", // z05
            "#kkkhh######", // z06
            "#kkkhhdyyyy#", // z07
            "##ff#f#yyyy#", // z08
            "##jjjV#yyy##", // z09
            "#Ojjjj#yyyc#", // z10
            "#Ojjjj#yyyc#", // z11
            "#Ojjjj#yyyc#", // z12
            "##WWW###WW##", // z13
    };

    /** The plan character at unit column / row, or ' ' outside. */
    public static char at(int col, int row) {
        if (col < 0 || row < 0 || col >= W || row >= D) return ' ';
        return ROWS[row].charAt(col);
    }

    /** Room key of a plan character (room cells only), else null. */
    public static String roomOf(char c) {
        return switch (c) {
            case 'g' -> "genkan";
            case 'h' -> "hall";
            case 't' -> "toilet";
            case 'b' -> "bath";
            case 'w' -> "washroom";
            case 'k' -> "dk";
            case 'j', 'V' -> "tatami";
            case 'y' -> "western";
            case 'O' -> "closet";
            case 'c' -> "western_closet";
            default -> null;
        };
    }

    public static boolean isRoom(char c) {
        return roomOf(c) != null;
    }

    /** The unit index 0..3 (west to east) whose plan holds world column x (shared walls belong to the western unit), or -1. */
    public static int unitOf(int x) {
        if (x < Site.BX1 || x > Site.BX2) return -1;
        for (int i = Site.UNIT_X0.length - 1; i >= 0; i--) if (x >= Site.UNIT_X0[i]) return x == Site.UNIT_X0[i] && i > 0 ? i - 1 : i;
        return -1;
    }

    /** The plan character of a world column (inside the building), or ' '. Party walls read '#'. */
    public static char atWorld(int x, int z) {
        int u = unitOf(x);
        if (u < 0 || z < Site.BZ1 || z > Site.BZ2) return ' ';
        return at(x - Site.UNIT_X0[u], z);
    }

    /** The room key at a world column (inside the building), or null. */
    public static String roomAtWorld(int x, int z) {
        return roomOf(atWorld(x, z));
    }
}
