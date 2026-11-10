package com.eternalrealm.dungeon.decor;

import com.eternalrealm.dungeon.BlockBox;
import com.eternalrealm.dungeon.BlockPoint;

import java.util.List;

/** The spaces of an instance as the decoration sees them. */
final class Spaces {
    private Spaces() {
    }

    /** What a space is for: fighting spaces must stay lit and walkable. */
    enum Kind {
        HALL, ROOM, CORRIDOR, ANTECHAMBER, ARENA, SET_PIECE, SPUR;

        boolean fighting() {
            return this == ROOM || this == CORRIDOR || this == ANTECHAMBER || this == ARENA;
        }
    }

    /**
     * A space to light: {@code inside} is the open volume (feet levels from its minY), {@code ceiling} the y of the
     * lowest ceiling block above it.
     */
    record Space(Kind kind, BlockBox inside, int ceiling) {
    }

    /**
     * A combat room.
     *
     * @param openings holes in its walls (corridor and set-piece openings)
     * @param lanes    strips in front of the openings, kept free
     * @param bossDoor whether its exit door leads to the boss (the antechamber starts behind it)
     * @param exit     the opening of its exit door
     */
    record Room(int index, BlockBox box, List<BlockBox> openings, List<BlockBox> lanes, List<BlockPoint> spawns,
                boolean bossDoor, BlockBox exit) {
    }

    /** A stretch of wall between pilasters or openings: {@code a0..a1} along the wall, up to {@code top}. */
    record Bay(Geometry.Wall wall, int a0, int a1, int top, Kind kind) {
        int width() {
            return a1 - a0 + 1;
        }

        int mid() {
            return (a0 + a1) / 2;
        }
    }

    /**
     * The entrance hall: {@code box} is the whole hall (the layout's hall and the wings built around it), {@code core}
     * the layout's hall, {@code front} the side of the entrance door.
     */
    record Hall(BlockBox box, BlockBox core, Geometry.Side front, BlockBox door, BlockBox lane, BlockPoint entry) {
    }

    /** The boss arena and where the antechamber meets it. */
    record Arena(BlockBox box, BlockBox opening, Geometry.Side entrySide) {
    }

    /** A set-piece room: {@code kind} names its content (vault, library...), {@code door} the opening in its wall. */
    record SetPiece(int index, String kind, BlockBox box, BlockBox door, Geometry.Side doorSide) {
    }
}
