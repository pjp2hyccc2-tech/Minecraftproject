package com.eternalrealm.dungeon;

import java.util.Objects;

/**
 * A sealed opening in the wall shared by a space and the corridor leading on. The build plan fills {@code opening}
 * with {@link BlockRole#DOOR}; on {@link DungeonRunEvent.OpenDoor} the platform fills it with air.
 */
public record Door(BlockBox opening) {
    public Door {
        Objects.requireNonNull(opening, "opening");
        if (opening.sizeX() != 1 && opening.sizeZ() != 1) {
            throw new IllegalArgumentException("a door is one block thick: " + opening);
        }
    }
}
