package com.eternalrealm.world;

import org.junit.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AreaTrackerTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private final AreaTracker tracker = new AreaTracker(AreaMap.standard(), 8.0);

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
    public void leavingAnAreaNeedsTheHysteresisMargin() {
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
    public void teleportsJumpStraightToTheNewArea() {
        tracker.update(ALICE, 0, 0);
        AreaTransition jump = tracker.update(ALICE, 0, -1400).orElseThrow();
        assertEquals(1, jump.previousAreaId());
        assertEquals(5, jump.area().id());
    }

    @Test
    public void playersAreTrackedIndependentlyAndCanBeForgotten() {
        tracker.update(ALICE, 0, 0);
        assertTrue(tracker.update(BOB, 2000, 0).isPresent());
        assertEquals(2, tracker.size());
        assertEquals(7, tracker.currentArea(BOB).orElseThrow().id());
        tracker.forget(BOB);
        assertTrue(tracker.currentArea(BOB).isEmpty());
        assertTrue("reported again after a re-login", tracker.update(BOB, 2000, 0).orElseThrow().isFirstPosition());
        assertEquals(1, tracker.currentArea(ALICE).orElseThrow().id());
    }

    @Test
    public void withoutHysteresisTheBorderIsExact() {
        AreaTracker exact = new AreaTracker(AreaMap.standard(), 0.0);
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
