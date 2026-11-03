package com.reisatsu.game;

import com.reisatsu.entity.SpiritKind;

import javax.annotation.Nullable;

/**
 * The week at HIDAMARI (docs/DESIGN.md §1 Flow): seven nights, 十一月三日（火）〜九日（月）. The first night only
 * watches the 霊障; then one spirit a night, in this order: the visitor, the waitress, the child, the woman in the
 * mirror, the chef, and on the last night the manager (closing time). Text: lang keys {@code night.reisatsu.<n>.*}
 * (title card), {@code dialogue.reisatsu.open.<n>.<i>} (the medium's opening lines), {@code dialogue.reisatsu.end.*}.
 */
public final class Week {
    public static final int NIGHTS = 7;
    /** Night 1: how many different phenomena to witness. */
    public static final int OBSERVE_GOAL = 5;
    /** Nights 2–7: how many of its signs you must have witnessed before the night's spirit shows. */
    public static final int MANIFEST_SIGNS = 2;
    /** Ticks the title card holds the screen before the medium speaks. */
    public static final int CARD_TICKS = 110;

    private static final SpiritKind[] ORDER = {null, null, SpiritKind.VISITOR, SpiritKind.WAITRESS, SpiritKind.CHILD,
            SpiritKind.MIRROR_WOMAN, SpiritKind.CHEF, SpiritKind.MANAGER};

    /** The phenomena the first night counts (the report's list, without the 2:00 crash and the board outside). */
    public static final String[] OBSERVE = {"door", "taps", "menus", "call", "chair", "lights", "plate", "music", "pa", "phone", "tv",
            "dryer", "drink", "knock", "bear", "glass", "prints"};

    /** The medium's opening lines of each night (index = night): their durations in ticks. */
    private static final int[][] OPENING = {
            {},
            {60, 110, 80},
            {56, 64, 92, 88},
            {56, 70, 100, 104},
            {56, 92, 92, 96},
            {56, 64, 108, 100},
            {56, 76, 84, 100},
            {56, 72, 100, 96},
    };

    private Week() {}

    /** The spirit of a night (null for the first, and outside 1..7). */
    @Nullable
    public static SpiritKind spirit(int night) {
        return night >= 0 && night < ORDER.length ? ORDER[night] : null;
    }

    /** The night a spirit belongs to. */
    public static int nightOf(SpiritKind k) {
        for (int n = 0; n < ORDER.length; n++) if (ORDER[n] == k) return n;
        return 0;
    }

    /** The signs that announce a spirit (witness keys). */
    public static String[] signs(SpiritKind k) {
        return switch (k) {
            case VISITOR -> new String[]{"crash", "door", "prints", "call"};
            case WAITRESS -> new String[]{"glass", "greet", "tray"};
            case CHILD -> new String[]{"menus", "giggle", "ball", "bear"};
            case MIRROR_WOMAN -> new String[]{"taps", "sob", "knock"};
            case CHEF -> new String[]{"plate", "gas", "chop"};
            case MANAGER -> new String[]{"pa", "lights", "music", "camera", "keys"};
        };
    }

    public static boolean isSign(@Nullable SpiritKind k, String kind) {
        if (k == null) return false;
        for (String s : signs(k)) if (s.equals(kind)) return true;
        return false;
    }

    public static boolean observes(String kind) {
        for (String s : OBSERVE) if (s.equals(kind)) return true;
        return false;
    }

    /** Durations of the medium's opening lines of a night. */
    public static int[] openingTicks(int night) {
        return night >= 1 && night < OPENING.length ? OPENING[night] : new int[0];
    }

    public static int openingLength(int night) {
        int t = 0;
        for (int d : openingTicks(night)) t += d;
        return t;
    }
}
