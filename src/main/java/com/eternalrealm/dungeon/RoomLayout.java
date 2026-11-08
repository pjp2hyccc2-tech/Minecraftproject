package com.eternalrealm.dungeon;

import java.util.List;
import java.util.Objects;

/**
 * Where combat room {@code index} was built.
 *
 * @param bounds      outer box including walls, floor ({@code minY}) and ceiling ({@code maxY})
 * @param spawnPoints feet positions for monsters (on the floor, away from walls, pillars and openings); spawn waves
 *                    round-robin over them
 */
public record RoomLayout(int index, BlockBox bounds, List<BlockPoint> spawnPoints) {
    public RoomLayout {
        Check.range("index", index, 0, DungeonDefinition.MAX_ROOMS - 1);
        Objects.requireNonNull(bounds, "bounds");
        spawnPoints = List.copyOf(spawnPoints);
        if (spawnPoints.isEmpty()) {
            throw new IllegalArgumentException("a room needs spawn points");
        }
        for (BlockPoint point : spawnPoints) {
            if (!bounds.interior().contains(point)) {
                throw new IllegalArgumentException("spawn point outside the room: " + point);
            }
        }
    }
}
