package com.eternalrealm.world.field;

import org.junit.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LordTurnsTest {
    @Test
    public void turnsComeOnceAtTheirMarksInOrder() {
        EnumSet<LordTurns.Turn> done = LordTurns.none();
        assertTrue(LordTurns.due(0.95, done).isEmpty());
        assertEquals(List.of(LordTurns.Turn.CALL), LordTurns.due(0.7, done));
        done.addAll(LordTurns.due(0.7, done));
        assertTrue("once", LordTurns.due(0.65, done).isEmpty());
        assertEquals(List.of(LordTurns.Turn.RAGE), LordTurns.due(0.49, done));
        done.add(LordTurns.Turn.RAGE);
        assertEquals(List.of(LordTurns.Turn.CALL_AGAIN), LordTurns.due(0.1, done));
    }

    @Test
    public void oneHeavyBlowCanCrossSeveralMarks() {
        assertEquals(List.of(LordTurns.Turn.CALL, LordTurns.Turn.RAGE, LordTurns.Turn.CALL_AGAIN),
                LordTurns.due(0.2, LordTurns.none()));
        assertEquals(List.of(LordTurns.Turn.CALL, LordTurns.Turn.RAGE), LordTurns.due(0.5, LordTurns.none()));
    }

    @Test
    public void callsAndTheRageHaveTheirMessages() {
        assertTrue(LordTurns.Turn.CALL.calls() && LordTurns.Turn.CALL_AGAIN.calls());
        assertEquals("threat.eternalrealm.lord_calls", LordTurns.Turn.CALL_AGAIN.messageKey());
        assertEquals("threat.eternalrealm.lord_rages", LordTurns.Turn.RAGE.messageKey());
        assertTrue(LordTurns.HELPERS >= 1 && LordTurns.RAGE_DAMAGE > 1.0);
    }
}
