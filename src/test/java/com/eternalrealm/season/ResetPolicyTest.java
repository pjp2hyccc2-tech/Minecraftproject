package com.eternalrealm.season;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ResetPolicyTest {
    @Test
    public void theStandardSeasonResetsEverythingSeasonScoped() {
        ResetPolicy policy = ResetPolicy.defaults();
        assertTrue(policy.resetLevel());
        assertTrue(policy.resetStats());
        assertTrue(policy.resetGold());
        assertTrue(policy.resetQuests());
        assertEquals(ResetPolicy.InventoryReset.RESET, policy.inventory());
    }

    @Test
    public void aLevelResetAlsoResetsTheStatAllocation() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResetPolicy(true, false, true, true, ResetPolicy.InventoryReset.RESET));
        ResetPolicy carryOver = new ResetPolicy(false, false, false, true, ResetPolicy.InventoryReset.KEEP);
        assertFalse(carryOver.resetLevel());
        assertFalse(carryOver.resetStats());
        assertEquals(ResetPolicy.InventoryReset.KEEP, carryOver.inventory());
        new ResetPolicy(false, true, false, false, ResetPolicy.InventoryReset.REMOVE_SEASON_LIMITED);
        assertThrows(NullPointerException.class, () -> new ResetPolicy(true, true, true, true, null));
    }

    @Test
    public void inventoryResetsHaveStableConfigIds() {
        assertEquals("reset", ResetPolicy.InventoryReset.RESET.id());
        assertEquals("keep", ResetPolicy.InventoryReset.KEEP.id());
        assertEquals("remove_season_limited", ResetPolicy.InventoryReset.REMOVE_SEASON_LIMITED.id());
        for (ResetPolicy.InventoryReset reset : ResetPolicy.InventoryReset.values()) {
            assertEquals(Optional.of(reset), ResetPolicy.InventoryReset.byId(reset.id()));
        }
        assertTrue(ResetPolicy.InventoryReset.byId("RESET").isEmpty());
    }
}
