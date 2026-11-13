package com.eternalrealm.monster;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Idle field monsters with nobody near think every fourth tick (docs/modules/bestiary.md). */
public class MonsterThrottleTest {
    private static final double RANGE = 32.0;
    private static final double FAR = 40.0 * 40.0;

    @Test
    public void anIdleMonsterWithNobodyNearRunsOneTickInFour() {
        int runs = 0;
        for (int tick = 100; tick < 200; tick++) {
            if (!MonsterThrottle.skips(tick, 17, true, true, FAR, RANGE)) {
                runs++;
            }
        }
        assertEquals(100 / MonsterThrottle.EVERY, runs);
    }

    @Test
    public void monstersTakeTurnsBySpreadingOverTheTicks() {
        int[] running = new int[MonsterThrottle.EVERY];
        for (int id = 0; id < 400; id++) {
            for (int tick = 100; tick < 100 + MonsterThrottle.EVERY; tick++) {
                if (MonsterThrottle.ownTick(tick, id)) {
                    running[tick - 100]++;
                }
            }
        }
        for (int count : running) {
            assertEquals(100, count);
        }
        assertTrue(MonsterThrottle.ownTick(-3, 3));
    }

    @Test
    public void anythingElseRunsEveryTick() {
        for (int tick = 100; tick < 120; tick++) {
            assertFalse("busy", MonsterThrottle.skips(tick, 5, false, true, FAR, RANGE));
            assertFalse("a player near", MonsterThrottle.skips(tick, 5, true, true, 31.9 * 31.9, RANGE));
            assertFalse("no players in the level", MonsterThrottle.skips(tick, 5, true, false, FAR, RANGE));
        }
        for (int tick = 0; tick < MonsterThrottle.SETTLE_TICKS; tick++) {
            assertFalse("just arrived", MonsterThrottle.skips(tick, 5, true, true, FAR, RANGE));
        }
        assertTrue(MonsterThrottle.skips(MonsterThrottle.SETTLE_TICKS + 1, 0, true, true, 32.1 * 32.1, RANGE));
    }
}
