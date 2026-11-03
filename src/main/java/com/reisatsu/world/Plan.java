package com.reisatsu.world;

/**
 * The ground floor plan of HIDAMARI exactly as in docs/DESIGN.md §2 (one character = one block column, x 0..40,
 * z 0..30). {@code #} exterior wall, {@code =} interior wall, letters are rooms, upper case and {@code d / w} are
 * doors, windows and fixtures in the wall line.
 */
public final class Plan {
    private Plan() {}

    public static final int W = 41, D = 31;

    private static final String[] ROWS = {
            "#########################################", // z00
            "#ssssss#oooooo#ffff=kkkkkkkkkkkkkkkkkkkk#", // z01
            "#ssssss#oooooo#ffff=kkkkkkkkkkkkkkkkkkkk#", // z02
            "#ssssss#oooooo#ffff=kkkkkkkkkkkkkkkkkkkk#", // z03
            "#ssssss#oooooo#==F==kkkkkkkkkkkkkkkkkkkk#", // z04
            "#ssssss#oooooo#kkkkkkkkkkkkkkkkkkkkkkkkk#", // z05
            "#ssssss#oooooo#kkkkkkkkkkkkkkkkkkkkkkkkk#", // z06
            "###d###E#wwd###kkkkkkkkkkkkkkkkkkkkkkkkk#", // z07
            "Bccccccccccccc#kkkkkkkkkkkkkkkkkkkkkkkkk#", // z08
            "#cccccccccccccdkkkkkkkkkkkkkkkkkkkkkkkkk#", // z09
            "###d##################PPPPP##SS##########", // z10
            "#ttttt#xxxxxxx#.........................#", // z11
            "#ttttt#xxxxxxx#.........................W", // z12
            "#ttttt#xxxxxxx#.........................W", // z13
            "#ttttt#xxxxxxx#.........................W", // z14
            "#ttttt#xxxxxxx#.........................W", // z15
            "#########MMM###.........................W", // z16
            "#mmmmm#rrrrrrr#.........................W", // z17
            "#mmmmm#rrrrrrr#.........................W", // z18
            "#mmmmm#rrrrrrr#.........................W", // z19
            "#mmmmm#rrrrrrr#.........................W", // z20
            "#mmmmm#rrrrrrr#.........................W", // z21
            "###d######d####.........................W", // z22
            "#aaaaaaaaaaaaaa.........................W", // z23
            "#aaaaaaaaaaaaaa.........................W", // z24
            "#aaaaaaaaaaaaaa.........................W", // z25
            "###AA####aaaaaa.........................W", // z26
            "#vvvvvvv#aaaaaa.........................W", // z27
            "#vvvvvvv#aaaaaa.........................W", // z28
            "#vvvvvvv#aaaaaa.........................W", // z29
            "###AA####WWWWWWWWWWWWWWWWWWWWWWWWWWWWWWW#", // z30
    };

    /** The plan character at (x, z), or ' ' outside the building. */
    public static char at(int x, int z) {
        if (x < 0 || z < 0 || x >= W || z >= D) return ' ';
        return ROWS[z].charAt(x);
    }

    public static boolean inside(int x, int z) {
        return x >= 0 && z >= 0 && x < W && z < D;
    }

    /** Room key of a plan character (room cells only), else null. */
    public static String roomOf(char c) {
        return switch (c) {
            case 's' -> "staff";
            case 'o' -> "office";
            case 'f' -> "fridge";
            case 'k' -> "kitchen";
            case 'c' -> "corridor";
            case 't' -> "storage";
            case 'x' -> "cavity";
            case 'm' -> "mens";
            case 'r' -> "womens";
            case 'a' -> "waiting";
            case 'v' -> "vestibule";
            case '.' -> "dining";
            default -> null;
        };
    }

    public static String roomAt(int x, int z) {
        return roomOf(at(x, z));
    }

    /** Wall-line cells: walls and everything set into them (doors, windows, the mirror, the pass, the breaker). */
    public static boolean isWallLine(char c) {
        return c == '#' || c == '=' || c == 'A' || c == 'W' || c == 'w' || c == 'd' || c == 'B' || c == 'F' || c == 'S' || c == 'P'
                || c == 'M' || c == 'E';
    }
}
