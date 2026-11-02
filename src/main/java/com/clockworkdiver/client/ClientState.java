package com.clockworkdiver.client;

import java.util.ArrayDeque;
import java.util.Deque;

/** Everything the HUD needs to know, mirrored from the server. */
public final class ClientState {
    public static String objectiveKey = "";
    public static int objectiveA;
    public static int objectiveB;
    public static int objectiveFlash;

    public static final Deque<String> HINTS = new ArrayDeque<>();
    public static String currentHint;
    public static int hintTicks;
    public static final int HINT_DURATION = 150;

    public static boolean inDive;
    public static String defectType = "";
    public static String partKey = "";
    public static int period = 20;
    public static long anchor;
    public static int lensLevel;
    public static boolean tutorialDive;
    public static long lastBeat = Long.MIN_VALUE;
    public static int abortHold;

    public static float steam;
    public static float maxSteam = 100.0f;
    public static int combo;
    public static int clickQuality = -1;
    public static int clickTicks;
    public static long lastBeatInputTick = Long.MIN_VALUE;

    public static int transitionKind = -1;
    public static int transitionTicks;
    public static String transitionTitle = "";
    public static String transitionSubtitle = "";

    private ClientState() {}

    public static int transitionDuration(int kind) {
        return switch (kind) {
            case 0 -> 60;
            case 1 -> 45;
            case 2 -> 60;
            case 3 -> 55;
            case 4 -> 70;
            default -> 40;
        };
    }

    public static void leaveDive() {
        inDive = false;
        combo = 0;
        steam = 0;
        abortHold = 0;
        lastBeat = Long.MIN_VALUE;
    }

    public static void reset() {
        leaveDive();
        objectiveKey = "";
        HINTS.clear();
        currentHint = null;
        hintTicks = 0;
        transitionKind = -1;
    }
}
