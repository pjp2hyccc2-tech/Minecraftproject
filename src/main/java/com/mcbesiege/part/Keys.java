package com.mcbesiege.part;

/** GLFW key codes (duplicated here so common code never touches LWJGL, which dedicated servers lack). */
public final class Keys {
    private Keys() {}

    public static final int NONE = -1;
    public static final int SPACE = 32;
    public static final int A = 65, B = 66, C = 67, D = 68, E = 69, F = 70, G = 71, H = 72, I = 73, J = 74, K = 75, L = 76, M = 77,
            N = 78, O = 79, P = 80, Q = 81, R = 82, S = 83, T = 84, U = 85, V = 86, W = 87, X = 88, Y = 89, Z = 90;
    public static final int ESCAPE = 256, ENTER = 257, TAB = 258, BACKSPACE = 259;
    public static final int RIGHT = 262, LEFT = 263, DOWN = 264, UP = 265;
    public static final int KP_0 = 320, KP_1 = 321, KP_2 = 322, KP_3 = 323, KP_4 = 324, KP_5 = 325, KP_6 = 326, KP_7 = 327, KP_8 = 328, KP_9 = 329;
    public static final int LEFT_SHIFT = 340, LEFT_CONTROL = 341, LEFT_ALT = 342, RIGHT_SHIFT = 344, RIGHT_CONTROL = 345, RIGHT_ALT = 346;

    /** Keys that the control screen keeps for itself. */
    public static boolean isReserved(int key) {
        return key == ESCAPE;
    }

    /** Short display label for a key code. */
    public static String name(int key) {
        if (key == NONE) return "-";
        if (key >= A && key <= Z) return String.valueOf((char) key);
        if (key >= 48 && key <= 57) return String.valueOf((char) key);
        if (key >= KP_0 && key <= KP_9) return "Num" + (key - KP_0);
        if (key >= 290 && key <= 314) return "F" + (key - 289);
        return switch (key) {
            case SPACE -> "Space";
            case UP -> "↑";
            case DOWN -> "↓";
            case LEFT -> "←";
            case RIGHT -> "→";
            case ENTER -> "Enter";
            case TAB -> "Tab";
            case BACKSPACE -> "BS";
            case LEFT_SHIFT -> "LShift";
            case RIGHT_SHIFT -> "RShift";
            case LEFT_CONTROL -> "LCtrl";
            case RIGHT_CONTROL -> "RCtrl";
            case LEFT_ALT -> "LAlt";
            case RIGHT_ALT -> "RAlt";
            case 39 -> "'";
            case 44 -> ",";
            case 45 -> "-";
            case 46 -> ".";
            case 47 -> "/";
            case 59 -> ";";
            case 61 -> "=";
            case 91 -> "[";
            case 92 -> "\\";
            case 93 -> "]";
            case 96 -> "`";
            case 260 -> "Ins";
            case 261 -> "Del";
            case 266 -> "PgUp";
            case 267 -> "PgDn";
            case 268 -> "Home";
            case 269 -> "End";
            case 330 -> "Num.";
            case 331 -> "Num/";
            case 332 -> "Num*";
            case 333 -> "Num-";
            case 334 -> "Num+";
            case 335 -> "NumEnter";
            default -> "#" + key;
        };
    }

    public static String names(int[] keys) {
        if (keys == null || keys.length == 0) return "-";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) sb.append('/');
            sb.append(name(keys[i]));
        }
        return sb.toString();
    }
}
