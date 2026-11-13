package com.eternalrealm.world.map;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The minimap's wrapping texture: only the strips that come into view are painted. */
public class ScrollingWindowTest {
    private static final int SIZE = 256;

    @Test
    public void walkingEastPaintsOneColumnOfTheNewView() {
        List<int[]> strips = ScrollingWindow.entering(-128, -128, -127, -128, SIZE);
        assertEquals(1, strips.size());
        assertArrayEquals(new int[]{128, -128, 129, 128}, strips.get(0));
        assertEquals(1, strips.get(0)[2] - strips.get(0)[0]);
    }

    @Test
    public void everyNewlyVisibleBlockIsInAStrip() {
        int[][] moves = {{3, 0}, {-5, 0}, {0, 7}, {0, -2}, {4, -6}, {-9, 11}, {0, 0}};
        for (int[] move : moves) {
            int oldX = 1000;
            int oldZ = -300;
            int newX = oldX + move[0];
            int newZ = oldZ + move[1];
            List<int[]> strips = ScrollingWindow.entering(oldX, oldZ, newX, newZ, SIZE);
            Set<Long> painted = new HashSet<>();
            for (int[] strip : strips) {
                for (int x = strip[0]; x < strip[2]; x++) {
                    for (int z = strip[1]; z < strip[3]; z++) {
                        assertTrue("inside the new view", x >= newX && x < newX + SIZE && z >= newZ && z < newZ + SIZE);
                        painted.add((long) x << 32 | (z & 0xFFFFFFFFL));
                    }
                }
            }
            for (int x = newX; x < newX + SIZE; x++) {
                for (int z = newZ; z < newZ + SIZE; z++) {
                    boolean seenBefore = x >= oldX && x < oldX + SIZE && z >= oldZ && z < oldZ + SIZE;
                    if (!seenBefore) {
                        assertTrue("newly visible " + x + "," + z, painted.contains((long) x << 32 | (z & 0xFFFFFFFFL)));
                    }
                }
            }
        }
        assertTrue(ScrollingWindow.entering(0, 0, 0, 0, SIZE).isEmpty());
    }

    @Test
    public void aJumpRepaintsEverything() {
        assertNull(ScrollingWindow.entering(0, 0, SIZE, 0, SIZE));
        assertNull(ScrollingWindow.entering(0, 0, 0, -SIZE - 3, SIZE));
    }

    @Test
    public void worldBlocksKeepTheirTexelAndRectanglesWrap() {
        List<int[]> one = ScrollingWindow.texels(new int[]{512 + 10, -256 + 20, 512 + 30, -256 + 25}, SIZE);
        assertEquals(1, one.size());
        assertArrayEquals(new int[]{10, 20, 20, 5}, one.get(0));

        // a column across the wrap: from texel 250 to 3 in x, the whole height
        List<int[]> wrapped = ScrollingWindow.texels(new int[]{250, -128, 260, 128}, SIZE);
        int area = 0;
        for (int[] piece : wrapped) {
            assertTrue(piece[0] >= 0 && piece[0] + piece[2] <= SIZE && piece[1] >= 0 && piece[1] + piece[3] <= SIZE);
            area += piece[2] * piece[3];
        }
        assertEquals(10 * 256, area);
        assertEquals(4, wrapped.size());

        // negative coordinates land where floorMod puts them
        assertArrayEquals(new int[]{255, 255, 1, 1}, ScrollingWindow.texels(new int[]{-1, -1, 0, 0}, SIZE).get(0));
    }

    @Test
    public void chunksAreClippedToTheWindow() {
        assertArrayEquals(new int[]{0, 0, 16, 16}, ScrollingWindow.clip(new int[]{0, 0, 16, 16}, -128, -128, SIZE));
        assertArrayEquals(new int[]{112, 0, 120, 16}, ScrollingWindow.clip(new int[]{112, 0, 128, 16}, -136, -128, SIZE));
        assertNull(ScrollingWindow.clip(new int[]{512, 0, 528, 16}, -128, -128, SIZE));
    }
}
