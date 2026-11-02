package com.birdiecraft.match;

public enum MatchState {
    LOBBY,
    INTRO,
    PLAYING,
    BALL_MOVING,
    HOLE_END,
    FINISHED;

    public static MatchState byId(int id) {
        if (id < 0 || id >= values().length) return LOBBY;
        return values()[id];
    }
}
