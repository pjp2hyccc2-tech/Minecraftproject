package com.eternalrealm.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Builds {@link DungeonLayout}s. Deterministic: the same definition and seed always give the same layout (it uses
 * {@link Random}, whose algorithm is fixed by the Java specification), so an instance can be rebuilt from its seed.
 *
 * <p>Method: the footprint is divided into a 7x7 grid of 36-block cells. A random self-avoiding walk picks one cell
 * per space (hall, rooms, arena); each space is placed inside its own cell with a margin, so spaces never overlap,
 * and always covers the middle band of its cell, so neighbouring spaces can always be joined by a straight corridor
 * that meets both walls away from their corners.</p>
 */
public final class DungeonLayoutGenerator {
    static final int CELL = 36;
    static final int GRID = 7;
    static final int GRID_OFFSET = (DungeonLayout.FOOTPRINT - CELL * GRID) / 2;
    static final int CELL_MARGIN = 2;
    static final int CENTER_BAND = 4;
    static final int ROOM_MIN = 15;
    static final int ROOM_MAX = 25;
    static final int ROOM_HEIGHT = 9;
    static final int HALL_SIZE = 11;
    static final int HALL_HEIGHT = 7;
    static final int ARENA_SIZE = 31;
    static final int ARENA_HEIGHT = 14;
    static final int CORRIDOR_HALF_WIDTH = 2;
    static final int CORRIDOR_HEIGHT = 6;
    static final int OPENING_TOP = CORRIDOR_HEIGHT - 2;
    static final int LANE_DEPTH = 4;
    static final int MAX_SPAWN_POINTS = 8;
    static final int RAID_ARENA_SIZE = 53;
    static final int RAID_ARENA_HEIGHT = 18;
    static final int RAID_HALL_SIZE = 15;
    static final int RAID_CORRIDOR_HALF_WIDTH = 3;
    static final int RAID_CORRIDOR_LENGTH = 16;

    private DungeonLayoutGenerator() {
    }

    /** The layout of a party dungeon; {@code seed} chooses the path, the room sizes and the decoration. */
    public static DungeonLayout generate(DungeonDefinition definition, long seed) {
        Objects.requireNonNull(definition, "definition");
        Random random = new Random(seed * 0x9E3779B97F4A7C15L ^ definition.id().hashCode());
        int spaceCount = definition.roomCount() + 2;
        List<int[]> path = randomPath(random, spaceCount);

        List<BlockBox> spaces = new ArrayList<>();
        for (int i = 0; i < spaceCount; i++) {
            int[] cell = path.get(i);
            if (i == 0) {
                spaces.add(place(random, cell, HALL_SIZE, HALL_SIZE, HALL_HEIGHT));
            } else if (i == spaceCount - 1) {
                spaces.add(place(random, cell, ARENA_SIZE, ARENA_SIZE, ARENA_HEIGHT));
            } else {
                spaces.add(place(random, cell, oddSize(random), oddSize(random), ROOM_HEIGHT));
            }
        }

        List<Link> links = new ArrayList<>();
        for (int i = 0; i + 1 < spaceCount; i++) {
            int[] from = path.get(i);
            int[] to = path.get(i + 1);
            int dirX = to[0] - from[0];
            int dirZ = to[1] - from[1];
            int center = dirX != 0 ? cellCenter(from[1]) : cellCenter(from[0]);
            int lateral = center - 2 + random.nextInt(5);
            links.add(connect(spaces.get(i), spaces.get(i + 1), dirX, dirZ, lateral, CORRIDOR_HALF_WIDTH));
        }

        Builder builder = new Builder();
        for (int i = 0; i < spaceCount; i++) {
            boolean arena = i == spaceCount - 1;
            int pillarInset = i == 0 ? 0 : arena ? 3 : 4;
            builder.space(spaces.get(i), pillarInset, !arena, arena, lanesOf(i, links));
        }
        for (Link link : links) {
            builder.corridor(link);
        }
        Door entranceDoor = new Door(links.get(0).fromOpening());
        builder.opening(entranceDoor.opening(), BlockRole.DOOR);
        builder.opening(links.get(0).toOpening(), BlockRole.AIR);
        List<Door> doors = new ArrayList<>();
        List<RoomLayout> rooms = new ArrayList<>();
        for (int r = 0; r < definition.roomCount(); r++) {
            Link exitLink = links.get(r + 1);
            Door door = new Door(exitLink.fromOpening());
            doors.add(door);
            builder.opening(door.opening(), BlockRole.DOOR);
            builder.opening(exitLink.toOpening(), BlockRole.AIR);
            BlockBox bounds = spaces.get(r + 1);
            rooms.add(new RoomLayout(r, bounds, spawnPoints(random, bounds, lanesOf(r + 1, links),
                    builder.pillarsIn(bounds))));
        }

        BlockBox hall = spaces.get(0);
        BlockBox arena = spaces.get(spaceCount - 1);
        Link last = links.get(links.size() - 1);
        BlockPoint bossSpawn = arena.center(1);
        int reach = ARENA_SIZE / 2 - 4;
        BlockPoint exit = bossSpawn.offset(last.dirX() * reach, 0, last.dirZ() * reach);
        return new DungeonLayout(definition.id(), definition.theme(), seed, hall, rooms,
                links.stream().map(Link::corridor).toList(), entranceDoor, doors, arena, bossSpawn, hall.center(1),
                exit, builder.plan());
    }

    /**
     * The final boss's raid instance: a {@value #RAID_ARENA_SIZE}x{@value #RAID_ARENA_SIZE} arena (51x51 floor for
     * 20 players), a wide corridor and an entrance hall to gather in. Fixed geometry, no combat rooms.
     */
    public static DungeonLayout raidArena(RaidDefinition raid) {
        Objects.requireNonNull(raid, "raid");
        int center = DungeonLayout.FOOTPRINT / 2;
        int arenaHalf = RAID_ARENA_SIZE / 2;
        BlockBox arena = new BlockBox(center - arenaHalf, 0, center - arenaHalf, center + arenaHalf,
                RAID_ARENA_HEIGHT - 1, center + arenaHalf);
        int hallHalf = RAID_HALL_SIZE / 2;
        int hallMinZ = arena.maxZ() + 1 + RAID_CORRIDOR_LENGTH;
        BlockBox hall = new BlockBox(center - hallHalf, 0, hallMinZ, center + hallHalf, HALL_HEIGHT - 1,
                hallMinZ + RAID_HALL_SIZE - 1);
        Link link = connect(hall, arena, 0, -1, center, RAID_CORRIDOR_HALF_WIDTH);

        Builder builder = new Builder();
        builder.space(hall, 0, true, false, List.of(link.fromLane()));
        builder.space(arena, 4, false, true, List.of(link.toLane()));
        builder.corridor(link);
        Door entranceDoor = new Door(link.fromOpening());
        builder.opening(entranceDoor.opening(), BlockRole.DOOR);
        builder.opening(link.toOpening(), BlockRole.AIR);
        BlockPoint bossSpawn = arena.center(1);
        BlockPoint exit = bossSpawn.offset(0, 0, -(arenaHalf - 6));
        return new DungeonLayout(raid.id(), raid.theme(), 0L, hall, List.of(), List.of(link.corridor()),
                entranceDoor, List.of(), arena, bossSpawn, hall.center(1), exit, builder.plan());
    }

    // ------------------------------------------------------------------ path & placement

    /** Self-avoiding walk of {@code length} grid cells (randomized depth-first search with backtracking). */
    private static List<int[]> randomPath(Random random, int length) {
        boolean[][] used = new boolean[GRID][GRID];
        List<int[]> path = new ArrayList<>();
        int[] start = {random.nextInt(GRID), random.nextInt(GRID)};
        path.add(start);
        used[start[0]][start[1]] = true;
        if (!extend(random, path, used, length)) {
            throw new IllegalStateException("no path of length " + length); // impossible for length <= 49
        }
        return path;
    }

    private static boolean extend(Random random, List<int[]> path, boolean[][] used, int length) {
        if (path.size() == length) {
            return true;
        }
        int[] last = path.get(path.size() - 1);
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        shuffle(random, directions);
        for (int[] direction : directions) {
            int x = last[0] + direction[0];
            int z = last[1] + direction[1];
            if (x < 0 || z < 0 || x >= GRID || z >= GRID || used[x][z]) {
                continue;
            }
            used[x][z] = true;
            path.add(new int[]{x, z});
            if (extend(random, path, used, length)) {
                return true;
            }
            path.remove(path.size() - 1);
            used[x][z] = false;
        }
        return false;
    }

    private static <T> void shuffle(Random random, T[] items) {
        for (int i = items.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T swap = items[i];
            items[i] = items[j];
            items[j] = swap;
        }
    }

    private static int oddSize(Random random) {
        return ROOM_MIN + 2 * random.nextInt((ROOM_MAX - ROOM_MIN) / 2 + 1);
    }

    private static int cellStart(int cell) {
        return GRID_OFFSET + cell * CELL;
    }

    /** The column (or row) every space of this cell covers; corridors are centered near it. */
    private static int cellCenter(int cell) {
        return cellStart(cell) + CELL / 2 - 1;
    }

    /** Places a space inside its cell: margin to the cell edges, covering the cell's middle band. */
    private static BlockBox place(Random random, int[] cell, int sizeX, int sizeZ, int height) {
        int minX = placeAxis(random, cell[0], sizeX);
        int minZ = placeAxis(random, cell[1], sizeZ);
        return new BlockBox(minX, 0, minZ, minX + sizeX - 1, height - 1, minZ + sizeZ - 1);
    }

    private static int placeAxis(Random random, int cell, int size) {
        int start = cellStart(cell);
        int center = cellCenter(cell);
        int low = Math.max(start + CELL_MARGIN, center + CENTER_BAND - (size - 1));
        int high = Math.min(start + CELL - 1 - CELL_MARGIN - (size - 1), center - CENTER_BAND);
        if (low > high) {
            throw new IllegalStateException("space of size " + size + " does not fit a cell");
        }
        return low + random.nextInt(high - low + 1);
    }

    // ------------------------------------------------------------------ corridors

    /**
     * Where two spaces in neighbouring cells are joined.
     *
     * @param fromOpening the hole in {@code from}'s wall (one block thick)
     * @param fromLane    the strip inside {@code from} in front of its opening (kept free of pillars and spawns)
     */
    private record Link(BlockBox corridor, BlockBox passage, BlockBox floor, BlockBox fromOpening,
                        BlockBox toOpening, BlockBox fromLane, BlockBox toLane, int dirX, int dirZ) {
    }

    /** Straight corridor between {@code from} and {@code to} (neighbours along X or Z), centered on {@code lateral}. */
    private static Link connect(BlockBox from, BlockBox to, int dirX, int dirZ, int lateral, int half) {
        int inner = half - 1;
        if (dirX != 0) {
            int fromWall = dirX > 0 ? from.maxX() : from.minX();
            int toWall = dirX > 0 ? to.minX() : to.maxX();
            int start = Math.min(fromWall, toWall) + 1;
            int end = Math.max(fromWall, toWall) - 1;
            return new Link(
                    new BlockBox(start, 0, lateral - half, end, CORRIDOR_HEIGHT - 1, lateral + half),
                    new BlockBox(start, 1, lateral - inner, end, OPENING_TOP, lateral + inner),
                    new BlockBox(start, 0, lateral - inner, end, 0, lateral + inner),
                    new BlockBox(fromWall, 1, lateral - inner, fromWall, OPENING_TOP, lateral + inner),
                    new BlockBox(toWall, 1, lateral - inner, toWall, OPENING_TOP, lateral + inner),
                    lane(fromWall - dirX, fromWall - dirX * LANE_DEPTH, lateral - half, lateral + half, true),
                    lane(toWall + dirX, toWall + dirX * LANE_DEPTH, lateral - half, lateral + half, true),
                    dirX, dirZ);
        }
        int fromWall = dirZ > 0 ? from.maxZ() : from.minZ();
        int toWall = dirZ > 0 ? to.minZ() : to.maxZ();
        int start = Math.min(fromWall, toWall) + 1;
        int end = Math.max(fromWall, toWall) - 1;
        return new Link(
                new BlockBox(lateral - half, 0, start, lateral + half, CORRIDOR_HEIGHT - 1, end),
                new BlockBox(lateral - inner, 1, start, lateral + inner, OPENING_TOP, end),
                new BlockBox(lateral - inner, 0, start, lateral + inner, 0, end),
                new BlockBox(lateral - inner, 1, fromWall, lateral + inner, OPENING_TOP, fromWall),
                new BlockBox(lateral - inner, 1, toWall, lateral + inner, OPENING_TOP, toWall),
                lane(fromWall - dirZ, fromWall - dirZ * LANE_DEPTH, lateral - half, lateral + half, false),
                lane(toWall + dirZ, toWall + dirZ * LANE_DEPTH, lateral - half, lateral + half, false),
                dirX, dirZ);
    }

    private static BlockBox lane(int depthA, int depthB, int lateralMin, int lateralMax, boolean alongX) {
        int low = Math.min(depthA, depthB);
        int high = Math.max(depthA, depthB);
        return alongX
                ? new BlockBox(low, 1, lateralMin, high, OPENING_TOP, lateralMax)
                : new BlockBox(lateralMin, 1, low, lateralMax, OPENING_TOP, high);
    }

    /** Lanes inside chain element {@code index}: in front of the openings to its neighbours. */
    private static List<BlockBox> lanesOf(int index, List<Link> links) {
        List<BlockBox> lanes = new ArrayList<>();
        if (index > 0) {
            lanes.add(links.get(index - 1).toLane());
        }
        if (index < links.size()) {
            lanes.add(links.get(index).fromLane());
        }
        return lanes;
    }

    // ------------------------------------------------------------------ spawn points

    private static List<BlockPoint> spawnPoints(Random random, BlockBox room, List<BlockBox> lanes,
                                                List<BlockPoint> pillars) {
        BlockBox inside = room.interior();
        List<BlockPoint> candidates = new ArrayList<>();
        for (int x = inside.minX() + 2; x <= inside.maxX() - 2; x += 2) {
            for (int z = inside.minZ() + 2; z <= inside.maxZ() - 2; z += 2) {
                BlockPoint point = new BlockPoint(x, 1, z);
                if (!inAny(lanes, point) && !pillars.contains(point)) {
                    candidates.add(point);
                }
            }
        }
        BlockPoint[] shuffled = candidates.toArray(new BlockPoint[0]);
        shuffle(random, shuffled);
        List<BlockPoint> chosen = new ArrayList<>();
        for (int i = 0; i < shuffled.length && chosen.size() < MAX_SPAWN_POINTS; i++) {
            chosen.add(shuffled[i]);
        }
        return chosen;
    }

    private static boolean inAny(List<BlockBox> boxes, BlockPoint point) {
        for (BlockBox box : boxes) {
            if (box.contains(point)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ build plan

    /** Collects fills by stage so that the plan is applied in a safe order whatever the call order was. */
    private static final class Builder {
        private final List<BlockFill> shells = new ArrayList<>();
        private final List<BlockFill> carves = new ArrayList<>();
        private final List<BlockFill> floors = new ArrayList<>();
        private final List<BlockFill> openings = new ArrayList<>();
        private final List<BlockFill> decoration = new ArrayList<>();
        private final List<BlockPoint> pillars = new ArrayList<>();

        /**
         * A closed room-like space: walls, ceiling, floor, ceiling lights, corner pillars ({@code pillarInset} from
         * the corners, 0 = none, never in a lane), and a floor emblem or (for arenas) a border marking.
         */
        void space(BlockBox box, int pillarInset, boolean emblem, boolean arena, List<BlockBox> lanes) {
            shells.add(new BlockFill(box, BlockRole.WALL));
            carves.add(new BlockFill(box.interior(), BlockRole.AIR));
            floors.add(new BlockFill(new BlockBox(box.minX() + 1, box.minY(), box.minZ() + 1, box.maxX() - 1,
                    box.minY(), box.maxZ() - 1), BlockRole.FLOOR));
            if (pillarInset > 0 && box.sizeX() > 2 * pillarInset + 2 && box.sizeZ() > 2 * pillarInset + 2) {
                int[] xs = {box.minX() + pillarInset, box.maxX() - pillarInset};
                int[] zs = {box.minZ() + pillarInset, box.maxZ() - pillarInset};
                for (int x : xs) {
                    for (int z : zs) {
                        BlockPoint base = new BlockPoint(x, box.minY() + 1, z);
                        if (!inAny(lanes, base)) {
                            pillars.add(base);
                            decoration.add(new BlockFill(new BlockBox(x, box.minY() + 1, z, x, box.maxY() - 1, z),
                                    BlockRole.PILLAR));
                        }
                    }
                }
            }
            ceilingLights(box, arena ? 7 : 6);
            BlockPoint center = box.center(box.minY());
            if (emblem) {
                decoration.add(new BlockFill(new BlockBox(center.x() - 1, box.minY(), center.z() - 1, center.x() + 1,
                        box.minY(), center.z() + 1), BlockRole.ACCENT));
            }
            if (arena) {
                int y = box.minY();
                decoration.add(new BlockFill(new BlockBox(box.minX() + 1, y, box.minZ() + 1, box.maxX() - 1, y,
                        box.minZ() + 1), BlockRole.ACCENT));
                decoration.add(new BlockFill(new BlockBox(box.minX() + 1, y, box.maxZ() - 1, box.maxX() - 1, y,
                        box.maxZ() - 1), BlockRole.ACCENT));
                decoration.add(new BlockFill(new BlockBox(box.minX() + 1, y, box.minZ() + 1, box.minX() + 1, y,
                        box.maxZ() - 1), BlockRole.ACCENT));
                decoration.add(new BlockFill(new BlockBox(box.maxX() - 1, y, box.minZ() + 1, box.maxX() - 1, y,
                        box.maxZ() - 1), BlockRole.ACCENT));
                decoration.add(new BlockFill(new BlockBox(center.x() - 2, y, center.z() - 2, center.x() + 2, y,
                        center.z() + 2), BlockRole.ACCENT));
            }
        }

        /** Light blocks set into the ceiling, symmetric around the center, every {@code spacing} blocks. */
        private void ceilingLights(BlockBox box, int spacing) {
            BlockPoint center = box.center(box.maxY());
            for (int x = center.x() - spacing * ((center.x() - box.minX() - 2) / spacing); x <= box.maxX() - 2;
                 x += spacing) {
                for (int z = center.z() - spacing * ((center.z() - box.minZ() - 2) / spacing); z <= box.maxZ() - 2;
                     z += spacing) {
                    decoration.add(new BlockFill(new BlockBox(x, box.maxY(), z, x, box.maxY(), z), BlockRole.LIGHT));
                }
            }
        }

        void corridor(Link link) {
            shells.add(new BlockFill(link.corridor(), BlockRole.WALL));
            carves.add(new BlockFill(link.passage(), BlockRole.AIR));
            floors.add(new BlockFill(link.floor(), BlockRole.FLOOR));
            BlockBox corridor = link.corridor();
            int y = corridor.maxY();
            if (link.dirX() != 0) {
                int z = (corridor.minZ() + corridor.maxZ()) / 2;
                for (int x = corridor.minX() + 2; x <= corridor.maxX() - 1; x += 5) {
                    decoration.add(new BlockFill(new BlockBox(x, y, z, x, y, z), BlockRole.LIGHT));
                }
            } else {
                int x = (corridor.minX() + corridor.maxX()) / 2;
                for (int z = corridor.minZ() + 2; z <= corridor.maxZ() - 1; z += 5) {
                    decoration.add(new BlockFill(new BlockBox(x, y, z, x, y, z), BlockRole.LIGHT));
                }
            }
        }

        void opening(BlockBox box, BlockRole role) {
            openings.add(new BlockFill(box, role));
        }

        List<BlockPoint> pillarsIn(BlockBox box) {
            return pillars.stream().filter(box::contains).toList();
        }

        List<BlockFill> plan() {
            List<BlockFill> plan = new ArrayList<>(shells);
            plan.addAll(carves);
            plan.addAll(floors);
            plan.addAll(decoration);
            plan.addAll(openings);
            return List.copyOf(plan);
        }
    }
}
