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
        Temper temper = new Temper(160);
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
    public void lingeringWarnsHalfwayThenProvokes() {
        Temper temper = new Temper(160);
        Temper.Look look = Temper.Look.NOTHING;
        int ticks = 0;
        UUID warned = null;
        while (look.provoked() == null) {
            look = temper.look(List.of(A), 20, false);
            ticks += 20;
            if (look.warned() != null) {
                assertNull("warned once", warned);
                warned = look.warned();
                assertEquals("halfway", 80, ticks);
            }
            assertTrue(ticks <= 160);
        }
        assertEquals(A, warned);
        assertEquals(160, ticks);
        assertEquals(A, look.provoked());
        assertTrue(temper.mayTarget(A));
    }

    @Test
    public void walkingAwayResetsTheCount() {
        Temper temper = new Temper(160);
        for (int i = 0; i < 7; i++) {
            assertNull(temper.look(List.of(A), 20, false).provoked());
        }
        assertEquals(140, temper.lingered(A));
        temper.look(List.of(), 20, false);
        assertEquals("forgotten once out of reach", 0, temper.lingered(A));
        assertNull(temper.look(List.of(A), 20, false).provoked());
        assertTrue(temper.isCalm());
    }

    @Test
    public void settlesDownAfterAWhileWithNobodyToFight() {
        Temper temper = new Temper(160);
        temper.provoke(A);
        for (int t = 0; t < Temper.SETTLE_TICKS - 20; t += 20) {
            temper.look(List.of(), 20, true); // fighting keeps it angry
        }
        assertFalse(temper.isCalm());
        for (int t = 0; t < Temper.SETTLE_TICKS - 20; t += 20) {
            temper.look(List.of(), 20, false);
        }
        assertFalse(temper.isCalm());
        temper.look(List.of(), 20, false);
        assertTrue("settled after " + Temper.SETTLE_TICKS + " idle ticks", temper.isCalm());
        assertFalse(temper.mayTarget(A));
    }

    @Test
    public void oneNewFoePerLookAndProvokersAreNotCountedAgain() {
        Temper temper = new Temper(40);
        temper.look(List.of(A, B), 20, false);
        Temper.Look look = temper.look(List.of(A, B), 20, false);
        assertEquals(A, look.provoked());
        assertTrue(temper.lingered(B) >= 40);
        look = temper.look(List.of(A, B), 20, true);
        assertEquals(B, look.provoked());
        assertSame(Temper.Look.NOTHING, temper.look(List.of(A, B), 20, true));
    }

    @Test
    public void zeroLingerNeverProvokesByStaying() {
        Temper temper = new Temper(0);
        for (int i = 0; i < 100; i++) {
            assertSame(Temper.Look.NOTHING, temper.look(List.of(A), 20, false));
        }
        assertTrue(temper.isCalm());
        assertThrows(IllegalArgumentException.class, () -> new Temper(-1));
        assertThrows(IllegalArgumentException.class, () -> temper.look(List.of(), 0, false));
    }
}
