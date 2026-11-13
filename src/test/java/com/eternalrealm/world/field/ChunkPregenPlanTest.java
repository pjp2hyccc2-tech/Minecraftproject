package com.eternalrealm.world.field;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The chunks made ahead of the players and the background pass over the whole map (docs/modules/worldgen.md). */
public class ChunkPregenPlanTest {

    @Test
    public void theMapAndItsMarginAreCovered() {
        ChunkPregenPlan plan = ChunkPregenPlan.astoria();
        int width = (FieldLayout.MAX_X >> 4) - (FieldLayout.MIN_X >> 4) + 1 + 2 * ChunkPregenPlan.MARGIN;
        int depth = (FieldLayout.MAX_Z >> 4) - (FieldLayout.MIN_Z >> 4) + 1 + 2 * ChunkPregenPlan.MARGIN;
        assertEquals(width * depth, plan.total());
        assertTrue("about 44 thousand chunks", plan.total() > 40_000 && plan.total() < 50_000);
        assertTrue(plan.contains(FieldLayout.MIN_X >> 4, FieldLayout.MIN_Z >> 4));
        assertTrue(plan.contains((FieldLayout.MAX_X >> 4) + ChunkPregenPlan.MARGIN, (FieldLayout.MAX_Z >> 4) + ChunkPregenPlan.MARGIN));
        assertFalse(plan.contains((FieldLayout.MAX_X >> 4) + ChunkPregenPlan.MARGIN + 1, 0));
        assertFalse(plan.contains(0, (FieldLayout.MIN_Z >> 4) - ChunkPregenPlan.MARGIN - 1));
    }

    @Test
    public void theSpiralVisitsEveryChunkOnceStartingAtTheCapital() {
        ChunkPregenPlan plan = new ChunkPregenPlan(-7, -5, 9, 4, 0, 0);
        long[] first = plan.nextInSpiral(1, 10, null);
        assertArrayEquals(new long[]{ChunkPregenPlan.key(0, 0)}, first);
        Set<Long> seen = new HashSet<>();
        seen.add(first[0]);
        plan.markDone(0, 0);
        int lastRing = 0;
        while (!plan.complete()) {
            long[] next = plan.nextInSpiral(5, 1000, null);
            assertTrue("progress", next.length > 0);
            for (long key : next) {
                int x = ChunkPregenPlan.keyX(key);
                int z = ChunkPregenPlan.keyZ(key);
                assertTrue("inside", plan.contains(x, z));
                assertTrue("once: " + x + "," + z, seen.add(key));
                int ring = Math.max(Math.abs(x), Math.abs(z));
                assertTrue("ring by ring", ring >= lastRing);
                lastRing = ring;
                plan.markDone(x, z);
            }
        }
        assertEquals(plan.total(), seen.size());
        assertEquals(0, plan.nextInSpiral(10, 1000, null).length);
    }

    @Test
    public void chunksThatCouldNotBeMadeComeAgainAfterTheSpiral() {
        ChunkPregenPlan plan = new ChunkPregenPlan(-2, -2, 2, 2, 0, 0);
        long skipped = ChunkPregenPlan.key(1, 1);
        while (true) {
            long[] next = plan.nextInSpiral(4, 100, key -> key == skipped);
            if (next.length == 0) {
                break;
            }
            for (long key : next) {
                plan.markDone(ChunkPregenPlan.keyX(key), ChunkPregenPlan.keyZ(key));
            }
        }
        assertEquals(plan.total() - 1, plan.doneCount());
        long[] again = plan.nextInSpiral(4, 100, null);
        assertArrayEquals(new long[]{skipped}, again);
    }

    @Test
    public void aheadIsTheBandBeyondTheViewNearestWhereThePlayerIsGoing() {
        ChunkPregenPlan plan = ChunkPregenPlan.astoria();
        int view = 8;
        // flying east from (0, 0) at 0.6 blocks per tick
        List<ChunkPregenPlan.Viewer> viewers = List.of(new ChunkPregenPlan.Viewer(8.0, 8.0, 0.6, 0.0));
        long[] ahead = plan.ahead(viewers, view, 50, null);
        assertEquals(50, ahead.length);
        Set<Long> unique = new HashSet<>();
        for (long key : ahead) {
            int x = ChunkPregenPlan.keyX(key);
            int z = ChunkPregenPlan.keyZ(key);
            int distance = Math.max(Math.abs(x), Math.abs(z));
            assertTrue("beyond the view: " + x + "," + z, distance > view);
            assertTrue("within the band: " + x + "," + z, distance <= view + ChunkPregenPlan.LOOKAHEAD);
            assertTrue("ahead (east): " + x, x > 0);
            assertTrue(unique.add(key));
        }
        int firstX = ChunkPregenPlan.keyX(ahead[0]);
        assertTrue("the first is well ahead: " + firstX, firstX >= view + ChunkPregenPlan.LOOKAHEAD / 2 - 1);

        // standing still: the nearest ring all around comes first
        long[] around = plan.ahead(List.of(new ChunkPregenPlan.Viewer(8.0, 8.0, 0.0, 0.0)), view, 20, null);
        for (long key : around) {
            int distance = Math.max(Math.abs(ChunkPregenPlan.keyX(key)), Math.abs(ChunkPregenPlan.keyZ(key)));
            assertEquals(view + 1, distance);
        }
    }

    @Test
    public void aheadSkipsMadeChunksOthersAndTheEdgeOfTheMap() {
        ChunkPregenPlan plan = ChunkPregenPlan.astoria();
        int view = 6;
        plan.markDone(view + 1, 0);
        long skipped = ChunkPregenPlan.key(view + 1, 1);
        long[] ahead = plan.ahead(List.of(new ChunkPregenPlan.Viewer(8.0, 8.0, 0.0, 0.0)), view, 4000,
                key -> key == skipped);
        for (long key : ahead) {
            assertFalse(key == ChunkPregenPlan.key(view + 1, 0));
            assertFalse(key == skipped);
        }
        // a player at the far east edge: nothing outside the map and its margin
        int edgeX = (FieldLayout.MAX_X >> 4) + ChunkPregenPlan.MARGIN;
        long[] edge = plan.ahead(List.of(new ChunkPregenPlan.Viewer(edgeX * 16.0, 8.0, 0.6, 0.0)), view, 4000, null);
        for (long key : edge) {
            assertTrue(plan.contains(ChunkPregenPlan.keyX(key), ChunkPregenPlan.keyZ(key)));
        }
        // two players side by side: no chunk twice, nothing either of them already sees
        List<ChunkPregenPlan.Viewer> two = List.of(new ChunkPregenPlan.Viewer(8.0, 8.0, 0.0, 0.0),
                new ChunkPregenPlan.Viewer(8.0 + 16.0 * 10, 8.0, 0.0, 0.0));
        Set<Long> seen = new HashSet<>();
        for (long key : plan.ahead(two, view, 4000, null)) {
            assertTrue(seen.add(key));
            int x = ChunkPregenPlan.keyX(key);
            int z = ChunkPregenPlan.keyZ(key);
            assertTrue(Math.max(Math.abs(x), Math.abs(z)) > view);
            assertTrue(Math.max(Math.abs(x - 10), Math.abs(z)) > view);
        }
        assertEquals(0, plan.ahead(List.of(), view, 10, null).length);
    }

    @Test
    public void progressIsSavedAndOtherDataIsIgnored() {
        ChunkPregenPlan plan = ChunkPregenPlan.astoria();
        plan.markDone(0, 0);
        plan.markDone(-100, 50);
        assertFalse("twice counts once", plan.markDone(0, 0));
        assertFalse("outside", plan.markDone(10_000, 0));
        long[] saved = plan.toLongs();

        ChunkPregenPlan restored = ChunkPregenPlan.astoria();
        assertTrue(restored.restore(saved));
        assertEquals(2, restored.doneCount());
        assertTrue(restored.isDone(0, 0));
        assertTrue(restored.isDone(-100, 50));
        assertFalse(restored.isDone(1, 0));

        long[] otherMap = saved.clone();
        otherMap[0] = FieldLayout.VERSION + 1;
        assertFalse(ChunkPregenPlan.astoria().restore(otherMap));
        long[] otherArea = saved.clone();
        otherArea[3] = ChunkPregenPlan.key(1, 1);
        assertFalse(ChunkPregenPlan.astoria().restore(otherArea));
        assertFalse(ChunkPregenPlan.astoria().restore(new long[]{1, 2}));
        assertFalse(ChunkPregenPlan.astoria().restore(null));
    }

    @Test
    public void keysPackLikeTheGame() {
        long key = ChunkPregenPlan.key(-3, 7);
        assertEquals(-3, ChunkPregenPlan.keyX(key));
        assertEquals(7, ChunkPregenPlan.keyZ(key));
        // ChunkPos.asLong(x, z) = x & 0xFFFFFFFF | (z & 0xFFFFFFFF) << 32
        assertEquals((long) -3 & 0xFFFFFFFFL | (7L << 32), key);
    }
}
