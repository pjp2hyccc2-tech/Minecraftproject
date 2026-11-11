package com.eternalrealm.testkit.bots;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BotIdsTest {
    @Test
    public void everyBotHasItsOwnFixedIdAndName() {
        Set<UUID> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (int number = 1; number <= BotIds.MAX; number++) {
            UUID id = BotIds.idOf(number);
            assertTrue("unique id", ids.add(id));
            assertTrue("unique name", names.add(BotIds.nameOf(number)));
            assertEquals("the number is read back from the id", number, BotIds.numberOf(id).getAsInt());
            assertTrue(BotIds.isBot(id));
            assertTrue("a valid player name", BotIds.nameOf(number).matches("[A-Za-z0-9_]{3,16}"));
        }
        assertEquals("the same id in every session", BotIds.idOf(7), BotIds.idOf(7));
        assertEquals("Bot01", BotIds.nameOf(1));
        assertEquals("Bot30", BotIds.nameOf(30));
    }

    @Test
    public void playersAreNotBots() {
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertFalse(BotIds.isBot(player));
        assertTrue(BotIds.numberOf(player).isEmpty());
        assertTrue(BotIds.numberOf(null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> BotIds.idOf(0));
        assertThrows(IllegalArgumentException.class, () -> BotIds.idOf(BotIds.MAX + 1));
    }

    /** Thirty bots in a crowd: no two look the same, and none is too dark to see. */
    @Test
    public void thirtyBotsWearThirtyDistinctColours() {
        for (int a = 1; a <= 30; a++) {
            int ca = BotIds.colorOf(a);
            int brightest = Math.max(ca >> 16 & 0xFF, Math.max(ca >> 8 & 0xFF, ca & 0xFF));
            assertTrue("bright enough: " + Integer.toHexString(ca), brightest >= 150);
            for (int b = a + 1; b <= 30; b++) {
                int cb = BotIds.colorOf(b);
                int dr = (ca >> 16 & 0xFF) - (cb >> 16 & 0xFF);
                int dg = (ca >> 8 & 0xFF) - (cb >> 8 & 0xFF);
                int db = (ca & 0xFF) - (cb & 0xFF);
                double distance = Math.sqrt(dr * dr + dg * dg + db * db);
                assertTrue("Bot" + a + " and Bot" + b + " look alike: " + Integer.toHexString(ca) + " "
                        + Integer.toHexString(cb), distance >= 40.0);
            }
        }
    }

    @Test
    public void hsvCornersAreThePrimaries() {
        assertEquals(0xFF0000, BotIds.hsv(0.0, 1.0, 1.0));
        assertEquals(0x00FF00, BotIds.hsv(1.0 / 3.0, 1.0, 1.0));
        assertEquals(0x0000FF, BotIds.hsv(2.0 / 3.0, 1.0, 1.0));
        assertEquals(0xFFFFFF, BotIds.hsv(0.5, 0.0, 1.0));
        assertEquals(0x000000, BotIds.hsv(0.25, 1.0, 0.0));
    }
}
