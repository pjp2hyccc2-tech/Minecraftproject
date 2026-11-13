package com.eternalrealm.world.guide;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PathSearchTest {
    /**
     * A little world of blocks: '#' solid, 'L' lava, '~' water, 'c' a carpet (a low floor in its own cell: not open, but
     * stood on), the rest air; floor everywhere at y = 63.
     */
    private static final class Blocks implements PathSearch.Terrain {
        private final Map<Long, Character> blocks = new HashMap<>();
        private final int size;

        Blocks(int size) {
            this.size = size;
            for (int x = -1; x <= size; x++) {
                for (int z = -1; z <= size; z++) {
                    put(x, 63, z, '#');
                }
            }
        }

        void put(int x, int y, int z, char block) {
            blocks.put(PathSearch.key(x, y, z), block);
        }

        /** A wall of solid blocks from y 64 to 64 + height - 1. */
        void wall(int x, int z, int height) {
            for (int y = 64; y < 64 + height; y++) {
                put(x, y, z, '#');
            }
        }

        char at(int x, int y, int z) {
            if (x < -1 || z < -1 || x > size || z > size) {
                return '#';
            }
            return blocks.getOrDefault(PathSearch.key(x, y, z), ' ');
        }

        @Override
        public boolean open(int x, int y, int z) {
            char block = at(x, y, z);
            return block == ' ' || block == '~';
        }

        @Override
        public boolean standable(int x, int y, int z) {
            if (at(x, y, z) == 'c') {
                return open(x, y + 1, z);
            }
            char below = at(x, y - 1, z);
            boolean floor = below == '#' || at(x, y, z) == '~';
            return floor && open(x, y, z) && open(x, y + 1, z) && below != 'L';
        }

        @Override
        public double penalty(int x, int y, int z) {
            if (at(x, y - 1, z) == 'L' || at(x, y, z) == 'L') {
                return -1;
            }
            return at(x, y, z) == '~' ? 2.0 : 0.0;
        }
    }

    private static PathSearch run(Blocks world, int sx, int sz, double gx, double gz, int radius) {
        PathSearch search = new PathSearch(world, new PathSearch.Node(sx, 64, sz), gx + 0.5, Double.NaN, gz + 0.5,
                0.8, radius, 50_000);
        while (search.status() == PathSearch.Status.RUNNING) {
            search.step(50);
        }
        return search;
    }

    @Test
    public void aStraightRoadIsWalkedStraight() {
        Blocks world = new Blocks(20);
        PathSearch search = run(world, 0, 0, 10, 0, 40);
        assertEquals(PathSearch.Status.FOUND, search.status());
        List<PathSearch.Node> path = search.path();
        assertEquals(11, path.size());
        assertEquals(new PathSearch.Node(10, 64, 0), path.get(path.size() - 1));
    }

    @Test
    public void wallsAreWalkedAroundAndCornersNeverCut() {
        Blocks world = new Blocks(20);
        for (int z = 0; z <= 8; z++) {
            world.wall(5, z, 3);
        }
        PathSearch search = run(world, 0, 4, 10, 4, 40);
        assertEquals(PathSearch.Status.FOUND, search.status());
        List<PathSearch.Node> path = search.path();
        for (PathSearch.Node node : path) {
            assertTrue("never inside the wall", !(node.x() == 5 && node.z() >= 0 && node.z() <= 8));
        }
        for (int i = 1; i < path.size(); i++) {
            PathSearch.Node a = path.get(i - 1);
            PathSearch.Node b = path.get(i);
            if (a.x() != b.x() && a.z() != b.z()) {
                assertTrue("a diagonal step never slips past a wall corner",
                        world.open(b.x(), 64, a.z()) && world.open(a.x(), 64, b.z()));
            }
        }
    }

    @Test
    public void oneBlockIsSteppedUpButTwoAreNot() {
        Blocks world = new Blocks(20);
        for (int z = -1; z <= 20; z++) {
            world.wall(6, z, 1); // a one-block step across the whole field
        }
        world.wall(7, 0, 1);
        PathSearch up = run(world, 0, 0, 10, 0, 40);
        assertEquals(PathSearch.Status.FOUND, up.status());
        assertTrue(up.path().stream().anyMatch(node -> node.y() == 65));

        Blocks cliff = new Blocks(20);
        for (int z = -1; z <= 20; z++) {
            cliff.wall(6, z, 2); // two blocks high everywhere: no way over
        }
        PathSearch blocked = run(cliff, 0, 0, 10, 0, 40);
        assertEquals(PathSearch.Status.PARTIAL, blocked.status());
        PathSearch.Node last = blocked.path().get(blocked.path().size() - 1);
        assertEquals("it gets as close as it can", 5, last.x());
    }

    @Test
    public void dropsOfUpToThreeBlocksAreTaken() {
        Blocks world = new Blocks(20);
        for (int x = -1; x <= 4; x++) {
            for (int z = -1; z <= 20; z++) {
                world.wall(x, z, 3); // a plateau three blocks high on the west side
            }
        }
        PathSearch search = new PathSearch(world, new PathSearch.Node(2, 67, 2), 10.5, Double.NaN, 2.5, 0.8, 40,
                50_000);
        while (search.status() == PathSearch.Status.RUNNING) {
            search.step(100);
        }
        assertEquals(PathSearch.Status.FOUND, search.status());
        assertEquals(64, search.path().get(search.path().size() - 1).y());
    }

    @Test
    public void lavaIsNeverCrossedAndWaterCostsMore() {
        Blocks world = new Blocks(20);
        for (int z = -1; z <= 20; z++) {
            world.put(5, 63, z, 'L'); // a lava ditch, but for a bridge at z = 15
        }
        world.put(5, 63, 15, '#');
        PathSearch search = run(world, 0, 0, 10, 0, 40);
        assertEquals(PathSearch.Status.FOUND, search.status());
        for (PathSearch.Node node : search.path()) {
            assertFalse("never over lava", node.x() == 5 && node.z() != 15);
        }
        assertTrue("over the bridge", search.path().contains(new PathSearch.Node(5, 64, 15)));
    }

    @Test
    public void aFarGoalEndsWhereItIsClosestWithinTheRadius() {
        Blocks world = new Blocks(200);
        PathSearch search = run(world, 0, 0, 180, 0, 24);
        assertEquals(PathSearch.Status.PARTIAL, search.status());
        PathSearch.Node last = search.path().get(search.path().size() - 1);
        assertTrue("it heads for the goal as far as the radius allows", last.x() >= 24);
    }

    @Test
    public void theSearchRunsAFewNodesAtATime() {
        Blocks world = new Blocks(40);
        PathSearch search = new PathSearch(world, new PathSearch.Node(0, 64, 0), 30.5, Double.NaN, 30.5, 0.8, 60,
                50_000);
        assertEquals(PathSearch.Status.RUNNING, search.step(5));
        assertEquals(5, search.expanded());
        while (search.status() == PathSearch.Status.RUNNING) {
            search.step(5);
        }
        assertEquals(PathSearch.Status.FOUND, search.status());
    }

    @Test
    public void standingNowhereFails() {
        Blocks world = new Blocks(10);
        PathSearch search = new PathSearch(world, new PathSearch.Node(3, 70, 3), 5, Double.NaN, 5, 1, 20, 1000);
        assertEquals(PathSearch.Status.FAILED, search.status());
        assertTrue(search.path().isEmpty());
    }

    @Test
    public void dotsAreSpacedEvenlyAlongTheRoute() {
        List<PathSearch.Node> path = List.of(new PathSearch.Node(0, 64, 0), new PathSearch.Node(1, 64, 0),
                new PathSearch.Node(2, 64, 0), new PathSearch.Node(3, 64, 0), new PathSearch.Node(4, 64, 0));
        List<double[]> dots = PathSearch.dots(path, 1.5);
        assertEquals(3, dots.size());
        assertEquals(0.5, dots.get(0)[0], 1e-9);
        assertEquals(2.0, dots.get(1)[0], 1e-9);
        assertEquals(3.5, dots.get(2)[0], 1e-9);
        assertEquals(0.5, dots.get(2)[2], 1e-9);
        assertTrue(PathSearch.dots(List.of(), 1.0).isEmpty());
    }

    /** Carpets are not open (they have a collision box) but are walked over, diagonals included. */
    @Test
    public void carpetsAreWalkedOver() {
        Blocks world = new Blocks(12);
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 12; z++) {
                world.put(x, 64, z, 'c');
            }
        }
        PathSearch search = run(world, 0, 0, 8, 8, 40);
        assertEquals(PathSearch.Status.FOUND, search.status());
        List<PathSearch.Node> path = search.path();
        for (PathSearch.Node node : path) {
            assertEquals("on the carpet: " + node, 64, node.y());
        }
        assertTrue("diagonal across the carpet: " + path.size(), path.size() <= 10);
    }
}
