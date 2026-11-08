package com.eternalrealm.dungeon;

import java.util.List;

/** The waves of one combat room, fought in order; clearing the last one opens the room's exit door. */
public record RoomPlan(List<Wave> waves) {
    public static final int MAX_WAVES = 8;

    public RoomPlan {
        waves = List.copyOf(waves);
        Check.range("waves", waves.size(), 1, MAX_WAVES);
    }

    public static RoomPlan of(Wave... waves) {
        return new RoomPlan(List.of(waves));
    }
}
