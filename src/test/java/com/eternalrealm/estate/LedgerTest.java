package com.eternalrealm.estate;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LedgerTest {
    @Test
    public void theLedgerTotalsEachThingAndSaysWhereItLies() {
        List<Ledger.Entry> entries = Ledger.aggregate(List.of(
                new Ledger.Stored("chest.2", "minecraft:iron_ingot", 64, StorageLabel.MATERIAL),
                new Ledger.Stored("chest.1", "eternalrealm:longsword#12", 1, StorageLabel.GEAR),
                new Ledger.Stored("barrel.1", "minecraft:iron_ingot", 20, StorageLabel.MATERIAL),
                new Ledger.Stored("chest.2", "minecraft:iron_ingot", 3, StorageLabel.MATERIAL),
                new Ledger.Stored("floor", "minecraft:bread", 0, StorageLabel.FOOD)));
        assertEquals(2, entries.size());
        assertEquals("gear first (label order)", "eternalrealm:longsword#12", entries.get(0).itemKey());
        Ledger.Entry iron = entries.get(1);
        assertEquals(87, iron.count());
        assertEquals(3, iron.stacks());
        assertEquals(List.of("barrel.1", "chest.2"), iron.places());
        assertEquals(StorageLabel.MATERIAL, iron.kind());
    }

    @Test
    public void searchingMatchesEveryWordInAnyLanguage() {
        String words = "鉄のインゴット Iron Ingot minecraft:iron_ingot";
        assertTrue(Ledger.matches("", words));
        assertTrue(Ledger.matches("iron", words));
        assertTrue(Ledger.matches("INGOT iron", words));
        assertTrue(Ledger.matches("インゴット", words));
        assertFalse(Ledger.matches("gold", words));
        assertFalse(Ledger.matches("iron gold", words));
    }

    @Test
    public void labelsCycleAndHaveIds() {
        StorageLabel label = StorageLabel.NONE;
        for (int i = 0; i < StorageLabel.values().length; i++) {
            label = label.next();
        }
        assertEquals(StorageLabel.NONE, label);
        for (StorageLabel each : StorageLabel.values()) {
            assertEquals(java.util.Optional.of(each), StorageLabel.byId(each.id()));
        }
    }
}
