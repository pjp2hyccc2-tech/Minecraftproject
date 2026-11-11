package com.eternalrealm.monster;

import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TemperTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    public void calmUntilHurt() {
        Temper temper = new Temper();
        assertTrue(temper.isCalm());
        assertFalse(temper.mayTarget(A));
        assertTrue("the first hit makes it angry", temper.provoke(A));
        assertFalse(temper.isCalm());
        assertTrue(temper.mayTarget(A));
        assertFalse("only the one who provoked it", temper.mayTarget(B));
        assertFalse("already angry", temper.provoke(B));
        assertTrue(temper.mayTarget(B));
    }

    @Test
    public void comingCloseProvokesAtOnce() {
        Temper temper = new Temper();
        Temper.Look look = temper.look(List.of(A), List.of(A), 10, false);
        assertEquals("within the aggro radius: at once, no waiting", A, look.provoked());
        assertTrue(temper.mayTarget(A));
        assertFalse(temper.isCalm());
        assertSame("nothing new the next look", Temper.Look.NOTHING, temper.look(List.of(A), List.of(A), 10, true));
    }

    @Test
    public void everyCloseOneIsProvokedTheFirstIsTheTarget() {
        Temper temper = new Temper();
        Temper.Look look = temper.look(List.of(A, B), List.of(A, B), 10, false);
        assertEquals(A, look.provoked());
        assertTrue(temper.mayTarget(A));
        assertTrue("the second one close by is a foe too", temper.mayTarget(B));
    }

    @Test
    public void aPlayerJustOutsideIsWarnedOncePerApproach() {
        Temper temper = new Temper();
        Temper.Look look = temper.look(List.of(), List.of(A), 10, false);
        assertNull(look.provoked());
        assertEquals("a little farther away: a warning", A, look.warned());
        assertTrue(temper.isCalm());
        for (int i = 0; i < 50; i++) {
            assertSame("staying there is no longer a reason to attack", Temper.Look.NOTHING,
                    temper.look(List.of(), List.of(A), 10, false));
        }
        assertTrue(temper.isCalm());
        assertTrue(temper.hasWarned(A));
        temper.look(List.of(), List.of(), 10, false);
        assertFalse("walking away forgets the warning", temper.hasWarned(A));
        assertEquals("coming back warns again", A, temper.look(List.of(), List.of(A), 10, false).warned());
        look = temper.look(List.of(A), List.of(A), 10, false);
        assertEquals("stepping inside the line", A, look.provoked());
        assertNull("no warning for a foe", look.warned());
    }

    @Test
    public void settlesDownAfterAWhileWithNobodyToFight() {
        Temper temper = new Temper();
        temper.provoke(A);
        for (int t = 0; t < Temper.SETTLE_TICKS - 10; t += 10) {
            temper.look(List.of(), List.of(), 10, true); // fighting keeps it angry
        }
        assertFalse(temper.isCalm());
        for (int t = 0; t < Temper.SETTLE_TICKS - 10; t += 10) {
            temper.look(List.of(), List.of(), 10, false);
        }
        assertFalse(temper.isCalm());
        temper.look(List.of(), List.of(), 10, false);
        assertTrue("settled after " + Temper.SETTLE_TICKS + " idle ticks", temper.isCalm());
        assertFalse(temper.mayTarget(A));
        assertEquals("a player still close when it settles turns it again", A,
                temper.look(List.of(A), List.of(A), 10, false).provoked());
    }

    @Test
    public void badStepsAreRefused() {
        Temper temper = new Temper();
        assertThrows(IllegalArgumentException.class, () -> temper.look(List.of(), List.of(), 0, false));
        assertThrows(NullPointerException.class, () -> temper.provoke(null));
    }
}
