package com.eternalrealm.dungeon;

import com.eternalrealm.boss.Difficulty;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ReadyCheckTest {
    private static final UUID LEADER = UUID.nameUUIDFromBytes(new byte[]{1});
    private static final UUID SECOND = UUID.nameUUIDFromBytes(new byte[]{2});
    private static final UUID THIRD = UUID.nameUUIDFromBytes(new byte[]{3});
    private static final UUID STRANGER = UUID.nameUUIDFromBytes(new byte[]{9});

    private static ReadyCheck check() {
        return new ReadyCheck("d3", Difficulty.HARD, LEADER, List.of(LEADER, SECOND, THIRD), 100);
    }

    @Test
    public void theLeaderAskedSoTheLeaderIsReadyAndTheRestArePreparing() {
        ReadyCheck check = check();
        assertTrue(check.isReady(LEADER));
        assertFalse(check.isReady(SECOND));
        assertEquals(1, check.readyCount());
        assertEquals(List.of(SECOND, THIRD), check.waitingFor());
        assertEquals(ReadyCheck.Phase.WAITING, check.phase(100));
        assertEquals("d3", check.contentId());
        assertEquals(Difficulty.HARD, check.difficulty());
    }

    @Test
    public void everyoneReadyStartsTheCountdownAndThenTheyGo() {
        ReadyCheck check = check();
        assertTrue(check.set(SECOND, true, 200));
        assertEquals(ReadyCheck.Phase.WAITING, check.phase(200));
        assertTrue(check.set(THIRD, true, 300));
        assertTrue(check.allReady());
        assertEquals(ReadyCheck.Phase.COUNTDOWN, check.phase(300));
        assertEquals(ReadyCheck.COUNTDOWN_TICKS, check.ticksLeft(300));
        assertEquals(ReadyCheck.Phase.COUNTDOWN, check.phase(300 + ReadyCheck.COUNTDOWN_TICKS - 1));
        assertEquals(ReadyCheck.Phase.GO, check.phase(300 + ReadyCheck.COUNTDOWN_TICKS));
    }

    @Test
    public void goingBackToPreparingStopsTheCountdown() {
        ReadyCheck check = check();
        check.set(SECOND, true, 200);
        check.set(THIRD, true, 300);
        assertTrue(check.set(SECOND, false, 320));
        assertEquals(ReadyCheck.Phase.WAITING, check.phase(300 + ReadyCheck.COUNTDOWN_TICKS));
        assertEquals(List.of(SECOND), check.waitingFor());
        assertTrue(check.set(SECOND, true, 400));
        assertEquals("the countdown starts over", ReadyCheck.Phase.COUNTDOWN, check.phase(400 + ReadyCheck.COUNTDOWN_TICKS
                - 1));
        assertTrue("even the leader may ask to wait", check.set(LEADER, false, 410));
        assertEquals(ReadyCheck.Phase.WAITING, check.phase(500));
    }

    @Test
    public void onlyChangesCountAndStrangersChangeNothing() {
        ReadyCheck check = check();
        int revision = check.revision();
        assertFalse("already ready", check.set(LEADER, true, 150));
        assertFalse("already preparing", check.set(SECOND, false, 150));
        assertFalse(check.set(STRANGER, true, 150));
        assertEquals(revision, check.revision());
        assertTrue(check.set(SECOND, true, 160));
        assertEquals(revision + 1, check.revision());
    }

    @Test
    public void aPartyNotReadyInTimeIsLetGo() {
        ReadyCheck check = check();
        check.set(SECOND, true, 200);
        assertEquals(ReadyCheck.TIMEOUT_TICKS, check.ticksLeft(100));
        assertEquals(ReadyCheck.Phase.WAITING, check.phase(100 + ReadyCheck.TIMEOUT_TICKS - 1));
        assertEquals(ReadyCheck.Phase.TIMED_OUT, check.phase(100 + ReadyCheck.TIMEOUT_TICKS));
        assertEquals(0, check.ticksLeft(100 + ReadyCheck.TIMEOUT_TICKS + 50));
    }

    @Test
    public void theCheckFollowsThePartyItAsked() {
        ReadyCheck check = check();
        assertTrue(check.sameMembers(List.of(THIRD, LEADER, SECOND)));
        assertFalse("someone left", check.sameMembers(List.of(LEADER, SECOND)));
        assertFalse("someone joined", check.sameMembers(List.of(LEADER, SECOND, THIRD, STRANGER)));
        assertFalse("someone was swapped", check.sameMembers(List.of(LEADER, SECOND, STRANGER)));
    }

    @Test
    public void aCheckNeedsAPartyLedByOneOfIt() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReadyCheck("d1", Difficulty.NORMAL, LEADER, List.of(LEADER), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ReadyCheck("d1", Difficulty.NORMAL, STRANGER, List.of(LEADER, SECOND), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ReadyCheck("d1", Difficulty.NORMAL, LEADER, List.of(LEADER, SECOND, SECOND), 0));
    }
}
