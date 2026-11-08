package com.eternalrealm.dungeon;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Geometry of generated layouts over many seeds, and a voxel check of the build plan: the plan is applied to a
 * 256x24x256 grid and flood-filled from the entry, as a player would walk.
 */
public class DungeonLayoutGeneratorTest {
    private static final int GEOMETRY_SEEDS = 60;
    private static final int VOXEL_SEEDS = 6;

    // ------------------------------------------------------------------ geometry

    @Test
    public void layoutsStayInsideTheFootprintAndNothingOverlaps() {
        for (DungeonDefinition dungeon : Dungeons.all()) {
            for (long seed = 0; seed < GEOMETRY_SEEDS; seed++) {
                DungeonLayout layout = DungeonLayoutGenerator.generate(dungeon, seed);
                String label = dungeon.id() + " seed " + seed;
                assertEquals(label, dungeon.roomCount(), layout.rooms().size());
                for (BlockBox box : layout.occupiedBoxes()) {
                    assertTrue(label + " outside: " + box, DungeonLayout.BOUNDS.contains(box));
                    assertTrue(label + " too high", box.maxY() < DungeonLayout.MAX_HEIGHT);
                }
                for (BlockFill fill : layout.buildPlan()) {
                    assertTrue(label, DungeonLayout.BOUNDS.contains(fill.box()));
                }
                List<BlockBox> boxes = layout.occupiedBoxes();
                for (int i = 0; i < boxes.size(); i++) {
                    for (int j = i + 1; j < boxes.size(); j++) {
                        assertFalse(label + ": " + boxes.get(i) + " overlaps " + boxes.get(j),
                                boxes.get(i).intersects(boxes.get(j)));
                    }
                }
                BlockBox arenaFloor = layout.bossArena().interior();
                assertTrue(label, arenaFloor.sizeX() >= 25 && arenaFloor.sizeZ() >= 25);
            }
        }
    }

    @Test
    public void corridorsChainTheSpacesAndDoorsSitInSharedWalls() {
        for (DungeonDefinition dungeon : Dungeons.all()) {
            for (long seed = 0; seed < GEOMETRY_SEEDS; seed++) {
                DungeonLayout layout = DungeonLayoutGenerator.generate(dungeon, seed);
                String label = dungeon.id() + " seed " + seed;
                List<BlockBox> chain = layout.chain();
                assertEquals(chain.size() - 1, layout.corridors().size());
                for (int i = 0; i < layout.corridors().size(); i++) {
                    BlockBox corridor = layout.corridors().get(i);
                    assertTrue(label + ": corridor " + i + " touches its start", touches(corridor, chain.get(i)));
                    assertTrue(label + ": corridor " + i + " touches its end", touches(corridor, chain.get(i + 1)));
                    for (int k = 0; k < chain.size(); k++) {
                        if (k != i && k != i + 1) {
                            assertFalse(label + ": corridor " + i + " touches space " + k,
                                    touches(corridor, chain.get(k)));
                        }
                    }
                }
                assertDoorInSharedWall(label + " entrance", layout.entranceDoor(), layout.entranceHall(),
                        layout.corridors().get(0));
                for (int r = 0; r < layout.rooms().size(); r++) {
                    assertDoorInSharedWall(label + " room " + r, layout.door(r), layout.room(r).bounds(),
                            layout.corridors().get(r + 1));
                }
            }
        }
    }

    /** Whether two disjoint boxes are face neighbours (share a face area of at least one block). */
    private static boolean touches(BlockBox a, BlockBox b) {
        boolean overlapX = a.minX() <= b.maxX() && b.minX() <= a.maxX();
        boolean overlapY = a.minY() <= b.maxY() && b.minY() <= a.maxY();
        boolean overlapZ = a.minZ() <= b.maxZ() && b.minZ() <= a.maxZ();
        boolean adjacentX = a.maxX() + 1 == b.minX() || b.maxX() + 1 == a.minX();
        boolean adjacentZ = a.maxZ() + 1 == b.minZ() || b.maxZ() + 1 == a.minZ();
        return overlapY && ((adjacentX && overlapZ) || (adjacentZ && overlapX));
    }

    /** The door lies in a wall plane of {@code space}, away from its corners, with {@code corridor} right behind it. */
    private static void assertDoorInSharedWall(String label, Door door, BlockBox space, BlockBox corridor) {
        BlockBox opening = door.opening();
        assertTrue(label + ": door inside the space's shell", space.contains(opening));
        assertFalse(label + ": door is not in the interior", space.interior().contains(opening));
        boolean inXWall = opening.sizeX() == 1 && (opening.minX() == space.minX() || opening.minX() == space.maxX());
        boolean inZWall = opening.sizeZ() == 1 && (opening.minZ() == space.minZ() || opening.minZ() == space.maxZ());
        assertTrue(label + ": door in a wall plane", inXWall || inZWall);
        int outX = inXWall ? (opening.minX() == space.maxX() ? 1 : -1) : 0;
        int outZ = inZWall ? (opening.minZ() == space.maxZ() ? 1 : -1) : 0;
        BlockBox beyond = new BlockBox(opening.minX() + outX, opening.minY(), opening.minZ() + outZ,
                opening.maxX() + outX, opening.maxY(), opening.maxZ() + outZ);
        assertTrue(label + ": the corridor is right behind the door", corridor.contains(beyond));
        BlockBox inner = space.interior();
        assertTrue(label + ": not in a corner", inXWall
                ? opening.minZ() >= inner.minZ() && opening.maxZ() <= inner.maxZ()
                : opening.minX() >= inner.minX() && opening.maxX() <= inner.maxX());
        assertTrue(label + ": players fit through", opening.sizeY() >= 3 && opening.volume() >= 9);
    }

    @Test
    public void spawnPointsEntryAndExitAreUsable() {
        for (DungeonDefinition dungeon : Dungeons.all()) {
            for (long seed = 0; seed < GEOMETRY_SEEDS; seed++) {
                DungeonLayout layout = DungeonLayoutGenerator.generate(dungeon, seed);
                Set<BlockPoint> pillars = pillarBases(layout);
                for (RoomLayout room : layout.rooms()) {
                    assertTrue(room.spawnPoints().size() >= 4);
                    assertEquals(room.spawnPoints().size(), new HashSet<>(room.spawnPoints()).size());
                    for (BlockPoint point : room.spawnPoints()) {
                        assertEquals("standing on the floor", 1, point.y());
                        assertTrue(room.bounds().interior().contains(point));
                        assertFalse("not inside a pillar", pillars.contains(point));
                    }
                }
                assertTrue(layout.entranceHall().interior().contains(layout.entry()));
                assertTrue(layout.bossArena().interior().contains(layout.bossSpawn()));
                assertTrue(layout.bossArena().interior().contains(layout.exit()));
                assertNotEquals(layout.bossSpawn(), layout.exit());
            }
        }
    }

    private static Set<BlockPoint> pillarBases(DungeonLayout layout) {
        Set<BlockPoint> bases = new HashSet<>();
        for (BlockFill fill : layout.buildPlan()) {
            if (fill.role() == BlockRole.PILLAR) {
                bases.add(new BlockPoint(fill.box().minX(), 1, fill.box().minZ()));
            }
        }
        return bases;
    }

    @Test
    public void theSameSeedGivesTheSameLayout() {
        Set<List<BlockBox>> distinct = new HashSet<>();
        for (long seed = 0; seed < 20; seed++) {
            DungeonLayout layout = DungeonLayoutGenerator.generate(Dungeons.D5, seed);
            assertEquals(layout, DungeonLayoutGenerator.generate(Dungeons.D5, seed));
            distinct.add(layout.chain());
        }
        assertTrue("seeds vary the layout", distinct.size() >= 15);
        assertNotEquals("the dungeon id is part of the seed", DungeonLayoutGenerator.generate(Dungeons.D4, 7).chain(),
                DungeonLayoutGenerator.generate(Dungeons.D5, 7).chain());
    }

    // ------------------------------------------------------------------ voxel walk

    private static final byte VOID = 0;
    private static final byte SOLID = 1;
    private static final byte OPEN = 2;
    private static final byte SEALED = 3;

    /** The build plan applied to a grid: void where nothing was built. */
    private static final class Voxels {
        final byte[] cells = new byte[DungeonLayout.FOOTPRINT * DungeonLayout.MAX_HEIGHT * DungeonLayout.FOOTPRINT];

        Voxels(DungeonLayout layout) {
            for (BlockFill fill : layout.buildPlan()) {
                byte value = switch (fill.role()) {
                    case AIR -> OPEN;
                    case DOOR -> SEALED;
                    default -> SOLID;
                };
                BlockBox box = fill.box();
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    for (int y = box.minY(); y <= box.maxY(); y++) {
                        for (int z = box.minZ(); z <= box.maxZ(); z++) {
                            cells[index(x, y, z)] = value;
                        }
                    }
                }
            }
        }

        static int index(int x, int y, int z) {
            return (x * DungeonLayout.MAX_HEIGHT + y) * DungeonLayout.FOOTPRINT + z;
        }

        byte at(BlockPoint point) {
            return cells[index(point.x(), point.y(), point.z())];
        }

        void open(Door door) {
            BlockBox box = door.opening();
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    for (int z = box.minZ(); z <= box.maxZ(); z++) {
                        cells[index(x, y, z)] = OPEN;
                    }
                }
            }
        }

        /** Air cells reachable from {@code start}; fails if the walk reaches unbuilt space (a hole in the shell). */
        Reach walk(BlockPoint start, String label) {
            BitSet seen = new BitSet(cells.length);
            BitSet reached = new BitSet(cells.length);
            ArrayDeque<BlockPoint> queue = new ArrayDeque<>();
            if (at(start) != OPEN) {
                fail(label + ": start is not free: " + start);
            }
            queue.add(start);
            seen.set(index(start.x(), start.y(), start.z()));
            int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            while (!queue.isEmpty()) {
                BlockPoint point = queue.poll();
                reached.set(index(point.x(), point.y(), point.z()));
                for (int[] step : steps) {
                    BlockPoint next = point.offset(step[0], step[1], step[2]);
                    if (!DungeonLayout.BOUNDS.contains(next)) {
                        fail(label + ": walked out of the footprint at " + next);
                    }
                    int i = index(next.x(), next.y(), next.z());
                    if (seen.get(i)) {
                        continue;
                    }
                    seen.set(i);
                    byte cell = cells[i];
                    if (cell == VOID) {
                        fail(label + ": hole in the shell, walked into unbuilt space at " + next);
                    }
                    if (cell == OPEN) {
                        queue.add(next);
                    }
                }
            }
            return new Reach(reached);
        }
    }

    /** Cells reached by one walk. */
    private record Reach(BitSet cells) {
        boolean contains(BlockPoint point) {
            return cells.get(Voxels.index(point.x(), point.y(), point.z()));
        }

        boolean containsAll(List<BlockPoint> points) {
            for (BlockPoint point : points) {
                if (!contains(point)) {
                    return false;
                }
            }
            return true;
        }
    }

    @Test
    public void theBuiltDungeonIsClosedAndConnectedThroughItsDoors() {
        for (DungeonDefinition dungeon : Dungeons.all()) {
            for (long seed = 0; seed < VOXEL_SEEDS; seed++) {
                DungeonLayout layout = DungeonLayoutGenerator.generate(dungeon, seed);
                String label = dungeon.id() + " seed " + seed;
                Voxels voxels = new Voxels(layout);
                assertEquals(label + ": entry floor", SOLID, voxels.at(layout.entry().offset(0, -1, 0)));

                // all doors sealed: the hall is a closed compartment, and so is every room
                Reach hall = voxels.walk(layout.entry(), label + " sealed hall");
                for (RoomLayout room : layout.rooms()) {
                    assertFalse(label + ": room " + room.index() + " reachable through a sealed door",
                            hall.contains(room.spawnPoints().get(0)));
                    Reach compartment = voxels.walk(room.spawnPoints().get(0), label + " room "
                            + room.index());
                    assertTrue(compartment.containsAll(room.spawnPoints()));
                    assertFalse(compartment.contains(layout.entry()));
                    assertFalse(compartment.contains(layout.exit()));
                    for (RoomLayout other : layout.rooms()) {
                        if (other != room) {
                            assertFalse(label + ": rooms " + room.index() + " and " + other.index() + " connected",
                                    compartment.contains(other.spawnPoints().get(0)));
                        }
                    }
                }

                // opening the doors in order unlocks the next room each time, the last one the arena
                voxels.open(layout.entranceDoor());
                for (int r = 0; r <= layout.rooms().size(); r++) {
                    Reach reachable = voxels.walk(layout.entry(), label + " open " + r);
                    if (r < layout.rooms().size()) {
                        assertTrue(label + ": room " + r + " reachable", reachable.containsAll(
                                layout.room(r).spawnPoints()));
                        if (r + 1 < layout.rooms().size()) {
                            assertFalse(reachable.contains(layout.room(r + 1).spawnPoints().get(0)));
                        }
                        assertFalse(label + ": arena locked", reachable.contains(layout.bossSpawn()));
                        voxels.open(layout.door(r));
                    } else {
                        assertTrue(label + ": boss reachable", reachable.contains(layout.bossSpawn()));
                        assertTrue(label + ": exit reachable", reachable.contains(layout.exit()));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ raid arena

    @Test
    public void theRaidArenaIsLargeClosedAndReachable() {
        DungeonLayout raid = DungeonLayoutGenerator.raidArena(Dungeons.FINAL_RAID);
        assertEquals("final", raid.id());
        assertEquals(DungeonTheme.END_THRONE, raid.theme());
        assertTrue(raid.rooms().isEmpty());
        assertTrue(raid.doors().isEmpty());
        BlockBox floor = raid.bossArena().interior();
        assertTrue("room for 20 players", floor.sizeX() >= 41 && floor.sizeZ() >= 41);
        assertTrue(DungeonLayout.BOUNDS.contains(raid.bossArena()));
        assertEquals(raid, DungeonLayoutGenerator.raidArena(Dungeons.FINAL_RAID));
        BlockBox passage = raid.entranceDoor().opening();
        assertTrue("wide entrance for a raid", Math.max(passage.sizeX(), passage.sizeZ()) >= 5);

        Voxels voxels = new Voxels(raid);
        Reach sealed = voxels.walk(raid.entry(), "raid sealed");
        assertFalse(sealed.contains(raid.bossSpawn()));
        voxels.open(raid.entranceDoor());
        Reach open = voxels.walk(raid.entry(), "raid open");
        assertTrue(open.contains(raid.bossSpawn()));
        assertTrue(open.contains(raid.exit()));
        List<BlockBox> boxes = new ArrayList<>(raid.occupiedBoxes());
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                assertFalse(boxes.get(i).intersects(boxes.get(j)));
            }
        }
    }

    @Test
    public void layoutsRejectInvalidGeometry() {
        DungeonLayout layout = DungeonLayoutGenerator.generate(Dungeons.D1, 1);
        BlockBox outside = new BlockBox(250, 0, 250, 260, 5, 260);
        try {
            new DungeonLayout(layout.id(), layout.theme(), layout.seed(), layout.entranceHall(), layout.rooms(),
                    layout.corridors(), layout.entranceDoor(), layout.doors(), layout.bossArena(), layout.bossSpawn(),
                    layout.entry(), layout.exit(), List.of(new BlockFill(outside, BlockRole.WALL)));
            fail("a fill outside the footprint must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            new DungeonLayout(layout.id(), layout.theme(), layout.seed(), layout.entranceHall(), layout.rooms(),
                    layout.corridors(), layout.entranceDoor(), layout.doors().subList(1, layout.doors().size()),
                    layout.bossArena(), layout.bossSpawn(), layout.entry(), layout.exit(), layout.buildPlan());
            fail("one door per room");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            new BlockBox(1, 0, 0, 0, 0, 0);
            fail("min > max");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
