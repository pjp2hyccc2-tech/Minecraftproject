package com.eternalrealm.world.map;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The maps' name plates give way to each other (2026-11-14: names of the waystones filled the map). */
public class LabelLayoutTest {
    private static final float W = 400.0F;
    private static final float H = 300.0F;

    private static LabelLayout.Box box(float x, float y, int rank, boolean always) {
        return LabelLayout.Box.centred(x, y, 40.0F, 10.0F, rank, always);
    }

    @Test
    public void platesThatWouldCoverEachOtherGiveWayByRank() {
        List<LabelLayout.Box> boxes = List.of(
                box(100, 100, 3, false),   // 0: a far waystone
                box(110, 104, 1, false),   // 1: a dungeon over it: placed first
                box(300, 100, 2, false),   // 2: alone
                box(100, 200, 2, false),   // 3: first of two of the same rank
                box(105, 202, 2, false));  // 4: gives way to the one asked before it
        List<Integer> drawn = LabelLayout.place(boxes, 0, 0, W, H);
        assertTrue(drawn.contains(1));
        assertFalse(drawn.contains(0));
        assertTrue(drawn.contains(2));
        assertTrue(drawn.contains(3));
        assertFalse(drawn.contains(4));
        assertEquals("by rank, then as asked", List.of(1, 2, 3), drawn);
    }

    @Test
    public void platesThatMustShowAlwaysShowAndKeepTheirRoom() {
        List<LabelLayout.Box> boxes = List.of(
                box(100, 100, 0, false),   // 0: the lowest rank, but covered by the pointed-at plate
                box(102, 101, 9, true),    // 1: the place under the mouse
                box(104, 102, 9, true),    // 2: the chosen one: shows too, even over the other
                box(-30, 50, 0, true));    // 3: half outside the map: still drawn (it must show)
        List<Integer> drawn = LabelLayout.place(boxes, 0, 0, W, H);
        assertEquals(List.of(1, 2, 3), drawn);
    }

    @Test
    public void platesOutsideTheMapOrTouchingAreLeftOut() {
        List<LabelLayout.Box> boxes = new ArrayList<>();
        boxes.add(box(10, 150, 0, false));                       // sticks out on the left
        boxes.add(box(200, 150, 0, false));
        boxes.add(box(200 + 40 + LabelLayout.GAP / 2, 150, 0, false)); // closer than the gap: touches
        boxes.add(box(200 + 40 + LabelLayout.GAP * 2, 150, 0, false)); // a little further: fits
        List<Integer> drawn = LabelLayout.place(boxes, 0, 0, W, H);
        assertEquals(List.of(1, 3), drawn);
        assertTrue(LabelLayout.place(List.of(), 0, 0, W, H).isEmpty());
    }

    @Test
    public void aCrowdOfWaystoneNamesThinsOut() {
        // the realm map zoomed out: 55 stones in a small window, their names asked for at once
        List<LabelLayout.Box> boxes = new ArrayList<>();
        for (int i = 0; i < 55; i++) {
            boxes.add(box(20 + (i * 37) % 360, 20 + (i * 53) % 260, 3, false));
        }
        List<Integer> drawn = LabelLayout.place(boxes, 0, 0, W, H);
        assertTrue("some names show: " + drawn.size(), drawn.size() >= 10);
        assertTrue("not all of them: " + drawn.size(), drawn.size() < 55);
        for (int a = 0; a < drawn.size(); a++) {
            for (int b = a + 1; b < drawn.size(); b++) {
                assertFalse(boxes.get(drawn.get(a)).touches(boxes.get(drawn.get(b))));
            }
        }
    }
}
