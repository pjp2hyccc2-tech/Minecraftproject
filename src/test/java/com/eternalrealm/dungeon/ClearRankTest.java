package com.eternalrealm.dungeon;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClearRankTest {
    private static final long LIMIT = 20L * 60L * 20L;

    @Test
    public void fastFlawlessClearsAreS() {
        assertEquals(ClearRank.S, ClearRank.of(LIMIT * 30 / 100, LIMIT, 0));
        assertEquals(ClearRank.S, ClearRank.of(LIMIT * 40 / 100, LIMIT, 0));
    }

    @Test
    public void fallsAndTimeLowerTheGrade() {
        assertEquals(ClearRank.A, ClearRank.of(LIMIT * 30 / 100, LIMIT, 1));
        assertEquals(ClearRank.A, ClearRank.of(LIMIT * 55 / 100, LIMIT, 0));
        assertEquals(ClearRank.B, ClearRank.of(LIMIT * 70 / 100, LIMIT, 3));
        assertEquals(ClearRank.C, ClearRank.of(LIMIT * 70 / 100, LIMIT, 4));
        assertEquals(ClearRank.C, ClearRank.of(LIMIT * 95 / 100, LIMIT, 0));
    }

    @Test
    public void noLimitMeansTwentyMinutes() {
        assertEquals(ClearRank.S, ClearRank.of(LIMIT * 40 / 100, 0, 0));
        assertEquals(ClearRank.C, ClearRank.of(LIMIT * 2, -1, 0));
    }

    @Test
    public void bonusTiers() {
        assertEquals(3, ClearRank.S.bonusTier());
        assertEquals(0, ClearRank.C.bonusTier());
        assertEquals("a", ClearRank.A.id());
    }
}
