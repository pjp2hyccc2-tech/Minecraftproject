package com.eternalrealm.item.bag;

import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.item.EquipmentBase;
import com.eternalrealm.item.EquipmentData;
import com.eternalrealm.item.ItemRarity;
import com.eternalrealm.item.ItemTier;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The bag's tabs, sort and capacity, the pickup feed and the equipment comparison. */
public class BagTest {
    private static final List<BagEntry> ENTRIES = List.of(
            new BagEntry(5, "eternalrealm:wolf_fang", 12, BagCategory.MATERIAL, ItemRarity.COMMON, 0),
            new BagEntry(2, "eternalrealm:small_potion", 3, BagCategory.CONSUMABLE, ItemRarity.COMMON, 0),
            new BagEntry(9, "eternalrealm:iron_spear", 1, BagCategory.EQUIPMENT, ItemRarity.RARE, 10),
            new BagEntry(1, "eternalrealm:iron_longsword", 1, BagCategory.EQUIPMENT, ItemRarity.COMMON, 5),
            new BagEntry(35, "minecraft:torch", 16, BagCategory.OTHER, ItemRarity.COMMON, 0),
            new BagEntry(7, "eternalrealm:steel_helmet", 1, BagCategory.EQUIPMENT, ItemRarity.RARE, 15));

    @Test
    public void tabsShowTheirCategoryInSlotOrder() {
        assertEquals(6, Bag.view(ENTRIES, BagTab.ALL).size());
        List<BagEntry> gear = Bag.view(ENTRIES, BagTab.EQUIPMENT);
        assertEquals(List.of(1, 7, 9), gear.stream().map(BagEntry::slot).toList());
        assertEquals(1, Bag.view(ENTRIES, BagTab.CONSUMABLE).size());
        assertEquals(1, Bag.view(ENTRIES, BagTab.MATERIAL).size());
        assertEquals(35, Bag.view(ENTRIES, BagTab.OTHER).get(0).slot());
    }

    @Test
    public void sortPutsEquipmentFirstRarerAndHigherFirst() {
        // helmet (rare, 15), spear (rare, 10), sword (common), potion, fang, torch
        assertEquals(List.of(7, 9, 1, 2, 5, 35), Bag.sortedSlots(ENTRIES));
        assertEquals(6, Bag.used(ENTRIES));
        assertEquals(35, Bag.CAPACITY);
        assertFalse(Bag.isBagSlot(0));
        assertTrue(Bag.isBagSlot(35));
        assertFalse(Bag.isBagSlot(36));
    }

    @Test(expected = IllegalArgumentException.class)
    public void theWeaponSlotIsNotPartOfTheBag() {
        Bag.sortedSlots(List.of(new BagEntry(0, "eternalrealm:iron_longsword", 1, BagCategory.EQUIPMENT,
                ItemRarity.COMMON, 1)));
    }

    @Test
    public void feedMergesTheSameItemAndFades() {
        PickupFeed<String> feed = new PickupFeed<>();
        feed.add("fang", PickupFeed.Kind.ITEM, "a", ItemRarity.COMMON, 2, 0);
        feed.add("fang", PickupFeed.Kind.ITEM, "b", ItemRarity.COMMON, 3, 30);
        assertEquals(1, feed.visible(30).size());
        assertEquals(5, feed.visible(30).get(0).count());
        assertEquals("b", feed.visible(30).get(0).payload());
        // after the merge window a new row starts, newest on top
        feed.add("fang", PickupFeed.Kind.ITEM, "c", ItemRarity.COMMON, 1, 30 + PickupFeed.MERGE_TICKS + 1);
        long now = 30 + PickupFeed.MERGE_TICKS + 1;
        assertEquals(2, feed.visible(now).size());
        assertEquals(1, feed.visible(now).get(0).count());
        // rows hold, fade, then disappear
        PickupFeed.Row<String> row = feed.visible(now).get(0);
        assertEquals(1.0F, row.alpha(now + PickupFeed.HOLD_TICKS), 1.0E-6F);
        assertTrue(row.alpha(now + PickupFeed.HOLD_TICKS + PickupFeed.FADE_TICKS / 2) < 1.0F);
        assertTrue(feed.visible(now + PickupFeed.HOLD_TICKS + PickupFeed.FADE_TICKS + 1).isEmpty());
    }

    @Test
    public void feedShowsFiveRowsAndHighlightsRareItemsAndWarnings() {
        PickupFeed<String> feed = new PickupFeed<>();
        for (int i = 0; i < 8; i++) {
            feed.add("item" + i, PickupFeed.Kind.ITEM, "x", i == 7 ? ItemRarity.EPIC : ItemRarity.COMMON, 1, i);
        }
        List<PickupFeed.Row<String>> rows = feed.visible(8);
        assertEquals(PickupFeed.MAX_ROWS, rows.size());
        assertEquals("item7", rows.get(0).key());
        assertTrue(rows.get(0).highlighted());
        assertFalse(rows.get(1).highlighted());
        feed.add("full", PickupFeed.Kind.WARNING, "w", ItemRarity.COMMON, 1, 9);
        feed.add("full", PickupFeed.Kind.WARNING, "w", ItemRarity.COMMON, 1, 10);
        assertEquals(2, feed.visible(10).stream().filter(r -> r.kind() == PickupFeed.Kind.WARNING).count());
        assertTrue(feed.visible(10).get(0).highlighted());
    }

    @Test
    public void comparisonShowsTheChangeAgainstWhatIsEquipped() {
        EquipmentData sword = EquipmentData.of(EquipmentBase.weapon(WeaponType.ONE_HANDED_SWORD, ItemTier.IRON), ItemRarity.COMMON, List.of());
        EquipmentData better = EquipmentData.of(EquipmentBase.weapon(WeaponType.ONE_HANDED_SWORD, ItemTier.STEEL), ItemRarity.COMMON, List.of());
        List<EquipmentComparison.Delta> rows = EquipmentComparison.compare(sword, better);
        EquipmentComparison.Delta attack = rows.stream()
                .filter(row -> row.stat() == EquipmentComparison.Stat.ATTACK).findFirst().orElseThrow();
        assertEquals(1, attack.direction());
        assertTrue(rows.stream().noneMatch(row -> row.stat() == EquipmentComparison.Stat.DEFENSE));
        // nothing equipped: every row starts from zero
        for (EquipmentComparison.Delta row : EquipmentComparison.compare(null, sword)) {
            assertEquals(0.0, row.before(), 0.0);
        }
        double[] total = EquipmentComparison.total(List.of(sword, better));
        assertEquals(EquipmentComparison.values(sword)[0] + EquipmentComparison.values(better)[0], total[0], 1.0E-9);
    }
}
