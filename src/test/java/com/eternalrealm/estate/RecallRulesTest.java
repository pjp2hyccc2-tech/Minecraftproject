package com.eternalrealm.estate;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecallRulesTest {
    private static RecallRules.Situation ready() {
        return new RecallRules.Situation(true, true, false, false, false, false, false, false, false, 0L);
    }

    @Test
    public void aPlayerWithAReadyRoomMayGoHome() {
        assertEquals(RecallRules.Check.OK, RecallRules.check(ready()));
    }

    @Test
    public void everyRefusalHasItsReason() {
        RecallRules.Situation s = ready();
        assertEquals(RecallRules.Check.NO_ROOM, RecallRules.check(new RecallRules.Situation(false, false, false,
                false, false, false, false, false, false, 0L)));
        assertEquals(RecallRules.Check.NOT_READY, RecallRules.check(new RecallRules.Situation(true, false, false,
                false, false, false, false, false, false, 0L)));
        assertEquals(RecallRules.Check.ALREADY_HOME, RecallRules.check(new RecallRules.Situation(true, true, true,
                false, false, false, false, false, false, 0L)));
        assertEquals(RecallRules.Check.IN_COMBAT, RecallRules.check(new RecallRules.Situation(true, true, false,
                true, false, false, false, false, false, 0L)));
        assertEquals(RecallRules.Check.IN_DUNGEON, RecallRules.check(new RecallRules.Situation(true, true, false,
                true, true, false, false, false, false, 0L)));
        assertEquals(RecallRules.Check.IN_BOSS_FIGHT, RecallRules.check(new RecallRules.Situation(true, true, false,
                true, false, true, false, false, false, 0L)));
        assertEquals(RecallRules.Check.IN_PVP, RecallRules.check(new RecallRules.Situation(true, true, false,
                true, false, false, true, false, false, 0L)));
        assertEquals(RecallRules.Check.DOWN, RecallRules.check(new RecallRules.Situation(true, true, false,
                false, true, false, false, true, false, 0L)));
        assertEquals(RecallRules.Check.CASTING, RecallRules.check(new RecallRules.Situation(true, true, false,
                false, false, false, false, false, true, 0L)));
        assertEquals(RecallRules.Check.COOLDOWN, RecallRules.check(new RecallRules.Situation(true, true, false,
                false, false, false, false, false, false, 1L)));
        assertTrue(s.hasRoom());
        for (RecallRules.Check check : RecallRules.Check.values()) {
            assertTrue(check.messageKey().startsWith("message.eternalrealm.recall."));
        }
        for (RecallRules.Interrupt interrupt : RecallRules.Interrupt.values()) {
            assertTrue(interrupt.messageKey().startsWith("message.eternalrealm.recall.broken."));
        }
    }

    @Test
    public void theCooldownCountsFromTheLastArrival() {
        assertEquals(0L, RecallRules.cooldownLeft(500L, -1L));
        assertEquals(RecallRules.COOLDOWN_TICKS, RecallRules.cooldownLeft(1000L, 1000L));
        assertEquals(RecallRules.COOLDOWN_TICKS - 200L, RecallRules.cooldownLeft(1200L, 1000L));
        assertEquals(0L, RecallRules.cooldownLeft(1000L + RecallRules.COOLDOWN_TICKS, 1000L));
        assertEquals("thirty seconds (the owner: forty at most)", 30 * 20, RecallRules.COOLDOWN_TICKS);
        assertEquals("a cast of three seconds", 60, RecallRules.CAST_TICKS);
    }

    @Test
    public void anArrivalFromAnEarlierRunNeverCounts() {
        // the world was opened again: the tick counter starts at zero, the old arrival was at tick 12,000
        assertEquals(0L, RecallRules.cooldownLeft(40L, 12_000L));
        for (long now = 0; now < 100_000; now += 997) {
            for (long arrival = -5; arrival < 100_000; arrival += 1_301) {
                long left = RecallRules.cooldownLeft(now, arrival);
                assertTrue(left >= 0 && left <= RecallRules.COOLDOWN_TICKS);
            }
        }
    }

    @Test
    public void theCapitalNeedsNoRoom() {
        RecallRules.Situation free = new RecallRules.Situation(RecallRules.Destination.CAPITAL, false, false, false,
                false, false, false, false, false, false, 0L);
        assertEquals(RecallRules.Check.OK, RecallRules.check(free));
        assertEquals(RecallRules.Check.ALREADY_IN_CAPITAL, RecallRules.check(new RecallRules.Situation(
                RecallRules.Destination.CAPITAL, false, false, true, false, false, false, false, false, false, 0L)));
        assertEquals(RecallRules.Check.IN_COMBAT, RecallRules.check(new RecallRules.Situation(
                RecallRules.Destination.CAPITAL, true, true, false, true, false, false, false, false, false, 0L)));
        assertEquals(RecallRules.Check.IN_DUNGEON, RecallRules.check(new RecallRules.Situation(
                RecallRules.Destination.CAPITAL, true, true, false, false, true, false, false, false, false, 0L)));
        assertEquals(RecallRules.Check.COOLDOWN, RecallRules.check(new RecallRules.Situation(
                RecallRules.Destination.CAPITAL, false, false, false, false, false, false, false, false, false, 5L)));
        assertEquals(RecallRules.Check.CASTING, RecallRules.check(new RecallRules.Situation(
                RecallRules.Destination.CAPITAL, false, false, false, false, false, false, false, false, true, 0L)));
    }

    @Test
    public void destinationsHaveIdsAndTexts() {
        assertEquals(RecallRules.Destination.CAPITAL, RecallRules.Destination.byId("capital"));
        assertEquals("older clients send nothing: the room", RecallRules.Destination.ROOM,
                RecallRules.Destination.byId(""));
        assertEquals("hud.eternalrealm.recall.casting", RecallRules.Destination.ROOM.castingKey());
        assertEquals("hud.eternalrealm.recall.casting_capital", RecallRules.Destination.CAPITAL.castingKey());
        assertEquals("hud.eternalrealm.recall.done_capital", RecallRules.Destination.CAPITAL.doneKey());
        assertEquals("message.eternalrealm.recall.already_capital",
                RecallRules.Check.ALREADY_IN_CAPITAL.messageKey());
    }

    @Test
    public void theCastBreaksOnMovingAndEndsAfterThreeSeconds() {
        assertFalse(RecallRules.moved(0.1, 0.0, 0.1));
        assertFalse("looking around and the odd nudge", RecallRules.moved(0.2, 0.05, -0.2));
        assertTrue(RecallRules.moved(0.5, 0.0, 0.0));
        assertTrue("falling or jumping off counts", RecallRules.moved(0.0, -0.6, 0.0));
        assertEquals(0.0, RecallRules.progress(100L, 100L), 1e-9);
        assertEquals(0.5, RecallRules.progress(100L, 130L), 1e-9);
        assertEquals(1.0, RecallRules.progress(100L, 400L), 1e-9);
        assertFalse(RecallRules.complete(100L, 159L));
        assertTrue(RecallRules.complete(100L, 160L));
    }
}
