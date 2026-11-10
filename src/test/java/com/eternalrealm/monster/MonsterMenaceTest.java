package com.eternalrealm.monster;

import org.junit.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MonsterMenaceTest {
    @Test
    public void higherLevelsLookMoreMenacing() {
        assertEquals(0, MonsterMenace.tier(1));
        assertEquals(0, MonsterMenace.tier(14));
        assertEquals(1, MonsterMenace.tier(15));
        assertEquals(2, MonsterMenace.tier(25));
        assertEquals(3, MonsterMenace.tier(35));
        assertEquals(4, MonsterMenace.tier(45));
        assertEquals(4, MonsterMenace.tier(80));
        double last = -1.0;
        for (int tier = 0; tier <= 4; tier++) {
            assertTrue(MonsterMenace.particleChance(tier) > last);
            last = MonsterMenace.particleChance(tier);
        }
        assertFalse(MonsterMenace.shrouded(2));
        assertTrue(MonsterMenace.shrouded(3));
    }

    @Test
    public void everyFieldHasItsOwnLook() {
        Set<MonsterMenace.Element> seen = EnumSet.noneOf(MonsterMenace.Element.class);
        for (int field = 1; field <= 10; field++) {
            seen.add(MonsterMenace.elementOf(field));
        }
        assertEquals(MonsterMenace.Element.values().length, seen.size());
        assertEquals(MonsterMenace.Element.PLAIN, MonsterMenace.elementOf(0));
        assertEquals(MonsterMenace.Element.VOID, MonsterMenace.elementOf(7));
    }

    @Test
    public void levelsAreReadFromNamePlateArguments() {
        assertEquals(12, MonsterMenace.parseLevel("12"));
        assertEquals(50, MonsterMenace.parseLevel(" 50 "));
        assertEquals(-1, MonsterMenace.parseLevel("森グモ"));
        assertEquals(-1, MonsterMenace.parseLevel(""));
        assertEquals(-1, MonsterMenace.parseLevel(null));
        assertEquals(-1, MonsterMenace.parseLevel("12345"));
        assertEquals(-1, MonsterMenace.parseLevel("１２")); // full-width digits are not a level
    }
}
