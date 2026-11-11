package com.eternalrealm.guild.house;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StorageDiffTest {
    @Test
    public void depositsAndWithdrawalsPerKind() {
        Map<String, Integer> before = StorageDiff.count(List.of(Map.entry("iron", 64), Map.entry("iron", 10),
                Map.entry("bread", 5), Map.entry("gold", 3)));
        assertEquals(Map.of("iron", 74, "bread", 5, "gold", 3), before);
        Map<String, Integer> after = StorageDiff.count(List.of(Map.entry("iron", 64), Map.entry("bread", 5),
                Map.entry("gold", 3), Map.entry("sword", 1), Map.entry("air", 0)));
        List<StorageDiff.Change<String>> changes = StorageDiff.between(before, after);
        assertEquals(2, changes.size());
        assertEquals("iron", changes.get(0).key());
        assertEquals(10, changes.get(0).count());
        assertTrue(!changes.get(0).deposit());
        assertEquals("sword", changes.get(1).key());
        assertTrue(changes.get(1).deposit());
        assertEquals(after, StorageDiff.apply(before, changes));
    }

    @Test
    public void nothingChangedIsNoChange() {
        Map<String, Integer> same = Map.of("iron", 3);
        assertTrue(StorageDiff.between(same, same).isEmpty());
        assertTrue(StorageDiff.between(Map.of(), Map.of()).isEmpty());
    }
}
