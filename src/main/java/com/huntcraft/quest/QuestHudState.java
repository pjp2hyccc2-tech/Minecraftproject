package com.huntcraft.quest;

import java.util.ArrayList;
import java.util.List;

/**
 * Client copy of the quest HUD state (written by the client network handler, read by HUD/screens and by
 * {@link com.huntcraft.core.HuntHooks#questRank} on the client side). Plain data, safe to load on both sides.
 */
public final class QuestHudState {
    public record Marker(double x, double z, String id, boolean enraged, boolean dead, boolean capturable) {
    }

    public record Mate(double x, double z, String name) {
    }

    public static boolean active;
    public static String questId = "";
    public static int state;
    public static int remainingTicks;
    public static long syncedAt;
    public static int faints;
    public static int maxFaints = 3;
    public static int centerX, centerZ, half = QuestRegions.HALF;
    public static int campX, campY, campZ;
    public static int[] progress = new int[0];
    public static int rank;
    public static boolean leader;
    public static String reason = "";
    public static final List<Marker> MONSTERS = new ArrayList<>();
    public static final List<Mate> PARTY = new ArrayList<>();

    private QuestHudState() {
    }

    public static void reset() {
        active = false;
        questId = "";
        progress = new int[0];
        MONSTERS.clear();
        PARTY.clear();
    }
}
