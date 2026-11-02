package com.birdiecraft.client;

import com.birdiecraft.match.MatchState;
import com.birdiecraft.match.MatchView;
import com.birdiecraft.network.S2CMenuDataPacket;

import java.util.ArrayList;
import java.util.List;

/** Everything the client knows about golf right now. */
public final class ClientGolfState {
    public static MatchView match;
    public static MatchState previousState;
    public static final List<BallFlight> flights = new ArrayList<>();
    public static HoleMapTexture holeMap;
    public static S2CMenuDataPacket menu;
    public static boolean scorecardToggled;
    /** Last finished practice flight (for the result read-out). */
    public static BallFlight lastPractice;
    public static int lastPracticeAge;
    private static boolean wasMyTurn;

    private ClientGolfState() {
    }

    public static boolean inMatch() {
        return match != null;
    }

    public static void reset() {
        match = null;
        previousState = null;
        flights.clear();
        if (holeMap != null) holeMap.close();
        holeMap = null;
        menu = null;
        scorecardToggled = false;
        lastPractice = null;
        wasMyTurn = false;
    }

    public static boolean isFlying(int participantId) {
        for (BallFlight f : flights) {
            if (!f.practice && f.participantId == participantId && !f.finished()) return true;
        }
        return false;
    }

    public static BallFlight myActiveFlight() {
        for (BallFlight f : flights) {
            if (f.mine && !f.expired()) return f;
        }
        return null;
    }

    public static boolean practiceFlightActive() {
        for (BallFlight f : flights) {
            if (f.practice && !f.finished()) return true;
        }
        return false;
    }

    public static void tick() {
        for (int i = flights.size() - 1; i >= 0; i--) {
            BallFlight f = flights.get(i);
            f.tick();
            if (f.practice && f.finished() && lastPractice != f) {
                lastPractice = f;
                lastPracticeAge = 0;
            }
            if (f.expired()) flights.remove(i);
        }
        if (lastPractice != null && ++lastPracticeAge > 160) lastPractice = null;

        boolean myTurn = match != null && match.isMyTurn() && !isFlying(match.myId);
        if (myTurn && !wasMyTurn) SwingController.onTurnStart();
        wasMyTurn = myTurn;
    }
}
