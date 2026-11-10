package com.eternalrealm.world;

import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AreaTrackerTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    /** Two fields split at x = 300 (west = 1, east = 2), so borders are easy to walk. */
    private static final AreaMap HALVES = new AreaMap(List.of(Areas.ARCELIA, Areas.ELDER_FOREST),
            new AreaMap.Locator() {
                @Override
                public int areaAt(double dx, double dz) {
                    return dx < 300 ? 1 : 2;
                }

                @Override
                public double borderDistance(double dx, double dz) {
                    return Math.abs(dx - 300);
                }
            });

    private final AreaTracker tracker = new AreaTracker(HALVES, 8.0);

    @Test
    public void theFirstPositionOfAPlayerIsAlwaysReported() {
        AreaTransition first = tracker.update(ALICE, 10, 10).orElseThrow();
        assertTrue(first.isFirstPosition());
        assertEquals(0, first.previousAreaId());
        assertEquals(1, first.area().id());
        assertEquals(Optional.empty(), tracker.update(ALICE, 20, -30));
        assertEquals(1, tracker.currentArea(ALICE).orElseThrow().id());
    }

    @Test
    public void aBorderIsOnlyCrossedOnceThePlayerIsWellInside() {
        tracker.update(ALICE, 290, 0);
        assertEquals("just across the border", Optional.empty(), tracker.update(ALICE, 305, 0));
        assertEquals(Optional.empty(), tracker.update(ALICE, 307.9, 0));
        AreaTransition entered = tracker.update(ALICE, 308, 0).orElseThrow();
        assertEquals(1, entered.previousAreaId());
        assertEquals(2, entered.area().id());
        assertFalse(entered.isFirstPosition());
        assertEquals("walking back a little does not flip", Optional.empty(), tracker.update(ALICE, 295, 0));
        assertEquals(1, tracker.update(ALICE, 291.9, 0).orElseThrow().area().id());
    }

    @Test
    public void teleportsAcrossTheFieldMapAreReportedAtOnce() {
        AreaTracker standard = new AreaTracker(AreaMap.standard(), 8.0);
        standard.update(ALICE, 0, 0);
        AreaTransition jump = standard.update(ALICE, 100, -1000).orElseThrow();
        assertEquals(1, jump.previousAreaId());
        assertEquals(6, jump.area().id());
    }

    @Test
    public void playersAreTrackedSeparatelyAndForgotten() {
        tracker.update(ALICE, 0, 0);
        assertTrue(tracker.update(BOB, 2000, 0).isPresent());
        assertEquals(2, tracker.size());
        assertEquals(2, tracker.currentArea(BOB).orElseThrow().id());
        tracker.forget(BOB);
        assertTrue(tracker.currentArea(BOB).isEmpty());
        assertTrue("reported again after a re-login", tracker.update(BOB, 2000, 0).orElseThrow().isFirstPosition());
    }

    @Test
    public void withoutHysteresisTheBorderIsExact() {
        AreaTracker exact = new AreaTracker(HALVES, 0.0);
        exact.update(ALICE, 299, 0);
        assertEquals(2, exact.update(ALICE, 300, 0).orElseThrow().area().id());
        assertEquals(1, exact.update(ALICE, 299.5, 0).orElseThrow().area().id());
    }

    @Test
    public void invalidArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new AreaTracker(AreaMap.standard(), -1));
        assertThrows(IllegalArgumentException.class, () -> new AreaTracker(AreaMap.standard(), Double.NaN));
        assertThrows(NullPointerException.class, () -> new AreaTracker(null, 1));
        assertThrows(NullPointerException.class, () -> tracker.update(null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> tracker.update(ALICE, Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new AreaTransition(1, Areas.ARCELIA));
        assertThrows(IllegalArgumentException.class, () -> new AreaTransition(-1, Areas.ARCELIA));
    }
}
