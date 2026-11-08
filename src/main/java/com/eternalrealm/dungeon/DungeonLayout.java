package com.eternalrealm.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A generated instance, in block coordinates relative to the instance origin: an entrance hall, the combat rooms in
 * order, the boss arena, and one corridor between each consecutive pair (hall -> room 0 -> ... -> arena).
 *
 * <p>Build it by applying {@link #buildPlan()} in order (spread the fills over several ticks if needed; floors are at
 * {@code y = 0}). {@link #entranceDoor()} stays sealed until the run starts; {@code doors().get(i)} seals the exit of
 * room {@code i} until the room is cleared. Everything lies inside {@link #BOUNDS}.</p>
 *
 * @param corridors {@code corridors.get(i)} joins chain element {@code i} and {@code i + 1}, where element 0 is the
 *                  hall, element {@code 1 + r} is room {@code r} and the last element is the arena
 * @param bossSpawn feet position of the boss
 * @param entry     feet position where players arrive
 * @param exit      feet position for the exit portal once the boss is defeated
 */
public record DungeonLayout(String id, DungeonTheme theme, long seed, BlockBox entranceHall, List<RoomLayout> rooms,
                            List<BlockBox> corridors, Door entranceDoor, List<Door> doors, BlockBox bossArena,
                            BlockPoint bossSpawn, BlockPoint entry, BlockPoint exit, List<BlockFill> buildPlan) {
    /** Horizontal size of the area an instance may use (x and z from 0 to 255). */
    public static final int FOOTPRINT = 256;
    /** Vertical size of the area an instance may use (y from 0 to 23). */
    public static final int MAX_HEIGHT = 24;
    public static final BlockBox BOUNDS = new BlockBox(0, 0, 0, FOOTPRINT - 1, MAX_HEIGHT - 1, FOOTPRINT - 1);
    /** Smallest boss arena floor (inside the walls). */
    public static final int MIN_ARENA_INTERIOR = 25;

    public DungeonLayout {
        Check.id("id", id);
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(entranceHall, "entranceHall");
        rooms = List.copyOf(rooms);
        corridors = List.copyOf(corridors);
        Objects.requireNonNull(entranceDoor, "entranceDoor");
        doors = List.copyOf(doors);
        Objects.requireNonNull(bossArena, "bossArena");
        Objects.requireNonNull(bossSpawn, "bossSpawn");
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(exit, "exit");
        buildPlan = List.copyOf(buildPlan);
        if (doors.size() != rooms.size() || corridors.size() != rooms.size() + 1) {
            throw new IllegalArgumentException("need one door per room and one corridor per link");
        }
        for (int i = 0; i < rooms.size(); i++) {
            if (rooms.get(i).index() != i) {
                throw new IllegalArgumentException("rooms must be in order");
            }
        }
        BlockBox arenaFloor = bossArena.interior();
        if (arenaFloor.sizeX() < MIN_ARENA_INTERIOR || arenaFloor.sizeZ() < MIN_ARENA_INTERIOR) {
            throw new IllegalArgumentException("boss arena too small: " + bossArena);
        }
        for (BlockFill fill : buildPlan) {
            requireInside(fill.box());
        }
        for (BlockBox box : List.of(entranceHall, bossArena, entranceDoor.opening())) {
            requireInside(box);
        }
        for (BlockBox box : corridors) {
            requireInside(box);
        }
        for (RoomLayout room : rooms) {
            requireInside(room.bounds());
        }
        if (!entranceHall.interior().contains(entry)) {
            throw new IllegalArgumentException("entry must be inside the entrance hall");
        }
        if (!arenaFloor.contains(bossSpawn) || !arenaFloor.contains(exit)) {
            throw new IllegalArgumentException("boss spawn and exit must be inside the arena");
        }
    }

    private static void requireInside(BlockBox box) {
        if (!BOUNDS.contains(box)) {
            throw new IllegalArgumentException("outside the " + FOOTPRINT + "x" + FOOTPRINT + "x" + MAX_HEIGHT
                    + " footprint: " + box);
        }
    }

    public RoomLayout room(int index) {
        return rooms.get(index);
    }

    /** The door that opens when room {@code roomIndex} is cleared. */
    public Door door(int roomIndex) {
        return doors.get(roomIndex);
    }

    /** Hall, rooms and arena in walking order. */
    public List<BlockBox> chain() {
        List<BlockBox> chain = new ArrayList<>();
        chain.add(entranceHall);
        for (RoomLayout room : rooms) {
            chain.add(room.bounds());
        }
        chain.add(bossArena);
        return List.copyOf(chain);
    }

    /** Every box the build touches: clear these (and the entities inside) before the slot is reused. */
    public List<BlockBox> occupiedBoxes() {
        List<BlockBox> boxes = new ArrayList<>(chain());
        boxes.addAll(corridors);
        return List.copyOf(boxes);
    }

    /** Number of blocks the build plan sets (a block written by several fills counts several times). */
    public long buildVolume() {
        long volume = 0;
        for (BlockFill fill : buildPlan) {
            volume += fill.box().volume();
        }
        return volume;
    }
}
