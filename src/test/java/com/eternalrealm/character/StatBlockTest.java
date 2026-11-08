package com.eternalrealm.character;

import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

public class StatBlockTest {
    @Test
    public void uniformAndTotal() {
        StatBlock block = StatBlock.uniform(5);
        for (StatType type : StatType.values()) {
            assertEquals(5, block.get(type));
        }
        assertEquals(30, block.total());
        assertEquals(0, StatBlock.ZERO.total());
    }

    @Test
    public void withIsImmutable() {
        StatBlock original = StatBlock.ZERO;
        StatBlock changed = original.with(StatType.STR, 7);
        assertEquals(0, original.get(StatType.STR));
        assertEquals(7, changed.get(StatType.STR));
        assertEquals(7, changed.total());
    }

    @Test
    public void ofDefaultsMissingStatsToZero() {
        Map<StatType, Integer> map = new EnumMap<>(StatType.class);
        map.put(StatType.LUK, 3);
        StatBlock block = StatBlock.of(map);
        assertEquals(3, block.get(StatType.LUK));
        assertEquals(0, block.get(StatType.STR));
        assertEquals(map.get(StatType.LUK), block.asMap().get(StatType.LUK));
    }

    @Test
    public void plusSaturatesAtMaxValue() {
        StatBlock sum = StatBlock.uniform(StatBlock.MAX_VALUE).plus(StatBlock.uniform(10));
        assertEquals(StatBlock.MAX_VALUE, sum.get(StatType.VIT));
    }

    @Test
    public void rejectsOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class, () -> StatBlock.uniform(-1));
        assertThrows(IllegalArgumentException.class, () -> StatBlock.ZERO.with(StatType.AGI, StatBlock.MAX_VALUE + 1));
    }

    @Test
    public void valueEquality() {
        assertEquals(StatBlock.uniform(2), StatBlock.uniform(2));
        assertEquals(StatBlock.uniform(2).hashCode(), StatBlock.uniform(2).hashCode());
        assertNotEquals(StatBlock.uniform(2), StatBlock.uniform(3));
    }

    @Test
    public void statIdsAreStable() {
        // Persistence keys and wire ids must never change.
        assertEquals("str", StatType.STR.id());
        assertEquals("luk", StatType.LUK.id());
        assertEquals(StatType.INT, StatType.byNetworkId(3).orElseThrow());
        assertEquals(StatType.DEX, StatType.byId("dex").orElseThrow());
        assertEquals(false, StatType.byNetworkId(6).isPresent());
        assertEquals(false, StatType.byNetworkId(-1).isPresent());
        assertEquals(StatType.COUNT, StatType.values().length);
    }
}
