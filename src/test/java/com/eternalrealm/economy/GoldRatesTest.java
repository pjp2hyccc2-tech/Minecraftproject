package com.eternalrealm.economy;

import com.eternalrealm.item.ArmoryDrops;
import com.eternalrealm.monster.DropTable;
import com.eternalrealm.monster.DropTables;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Gold earned in play is 1.2 times its formula; weapons drop 0.9 times as often (2026-11-14). */
public class GoldRatesTest {
    @Test
    public void earnedGoldIsOnePointTwoTimesTheFormula() {
        assertEquals(1.2, GoldRates.EARNED, 0.0);
        assertEquals(120L, GoldRates.earned(100L));
        assertEquals(12L, GoldRates.earned(10L));
        assertEquals(0L, GoldRates.earned(0L));
        assertEquals(30.0, GoldRates.earned(25.0), 1e-9);
    }

    @Test
    public void weaponsDropNineTimesInTen() {
        assertEquals(0.9, ArmoryDrops.WEAPON_DROP_RATE, 0.0);
        assertEquals(0.02 * 0.9, ArmoryDrops.wieldedDropChance(false), 1e-12);
        assertEquals(0.06 * 0.9, ArmoryDrops.wieldedDropChance(true), 1e-12);
        assertEquals(0.25 * 0.9, ArmoryDrops.lordWeaponChance(true), 1e-12);
        assertEquals(0.10 * 0.9, ArmoryDrops.calamityWeaponChance(false), 1e-12);
        for (DropTable table : DropTables.all()) {
            if (table.equipment() == null) {
                continue; // no equipment from this table
            }
            double chance = table.equipment().chance();
            assertTrue(table.id() + ": " + chance, chance <= 0.3 * 0.9 + 1e-12);
        }
    }
}
