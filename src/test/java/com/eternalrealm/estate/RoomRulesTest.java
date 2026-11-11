package com.eternalrealm.estate;

import org.junit.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RoomRulesTest {
    @Test
    public void roomsGetDearerAndHaveDistinctDesigns() {
        Set<String> ids = new HashSet<>();
        long previous = 0;
        for (RoomDesign design : RoomDesign.values()) {
            assertTrue(design.id(), ids.add(design.id()));
            assertTrue(EstateKey.isName(design.id()));
            assertTrue("prices rise with the order", design.price() > previous);
            previous = design.price();
            assertEquals(Optional.of(design), RoomDesign.byId(design.id()));
            UUID owner = UUID.randomUUID();
            assertEquals(new EstateKey(EstateKey.ROOM, owner, design.id()), design.key(owner));
        }
        assertEquals(Optional.empty(), RoomDesign.byId("palace"));
    }

    @Test
    public void rentingNeedsNoRoomAndTheGold() {
        RoomDesign garret = RoomDesign.GARRET;
        assertEquals(RoomRules.Outcome.OK, RoomRules.rent(Optional.empty(), garret, garret.price()));
        assertEquals(RoomRules.Outcome.NOT_ENOUGH_GOLD, RoomRules.rent(Optional.empty(), garret, garret.price() - 1));
        assertEquals("one room per player", RoomRules.Outcome.HAS_ROOM,
                RoomRules.rent(Optional.of(garret), RoomDesign.CANAL, 1_000_000L));
    }

    @Test
    public void movingCostsTheDifferenceOrGivesHalfOfItBack() {
        assertEquals(RoomDesign.CANAL.price() - RoomDesign.GARRET.price(),
                RoomRules.changeCost(RoomDesign.GARRET, RoomDesign.CANAL));
        assertEquals(-(RoomDesign.GRAND.price() - RoomDesign.TOWER.price()) / 2,
                RoomRules.changeCost(RoomDesign.GRAND, RoomDesign.TOWER));
        long cost = RoomRules.changeCost(RoomDesign.GARRET, RoomDesign.GRAND);
        assertEquals(RoomRules.Outcome.OK, RoomRules.change(Optional.of(RoomDesign.GARRET), RoomDesign.GRAND, cost,
                false));
        assertEquals(RoomRules.Outcome.NOT_ENOUGH_GOLD, RoomRules.change(Optional.of(RoomDesign.GARRET),
                RoomDesign.GRAND, cost - 1, false));
        assertEquals("a cheaper room needs no gold", RoomRules.Outcome.OK,
                RoomRules.change(Optional.of(RoomDesign.GRAND), RoomDesign.GARRET, 0L, false));
        assertEquals(RoomRules.Outcome.SAME_ROOM, RoomRules.change(Optional.of(RoomDesign.TOWER), RoomDesign.TOWER,
                1_000_000L, false));
        assertEquals(RoomRules.Outcome.BUSY, RoomRules.change(Optional.of(RoomDesign.TOWER), RoomDesign.GRAND,
                1_000_000L, true));
        assertEquals(RoomRules.Outcome.NO_ROOM, RoomRules.change(Optional.empty(), RoomDesign.GRAND, 1_000_000L,
                false));
        // going up and back down never makes money
        for (RoomDesign a : RoomDesign.values()) {
            for (RoomDesign b : RoomDesign.values()) {
                assertTrue(RoomRules.changeCost(a, b) + RoomRules.changeCost(b, a) >= 0);
            }
        }
    }

    @Test
    public void givingUpNeedsAnEmptyRoomAndGivesHalfThePriceBack() {
        assertEquals(RoomDesign.CANAL.price() / 2, RoomRules.refund(RoomDesign.CANAL));
        assertEquals(RoomRules.Outcome.OK, RoomRules.giveUp(Optional.of(RoomDesign.CANAL), false, true));
        assertEquals(RoomRules.Outcome.NOT_EMPTY, RoomRules.giveUp(Optional.of(RoomDesign.CANAL), false, false));
        assertEquals(RoomRules.Outcome.BUSY, RoomRules.giveUp(Optional.of(RoomDesign.CANAL), true, true));
        assertEquals(RoomRules.Outcome.NO_ROOM, RoomRules.giveUp(Optional.empty(), false, true));
        for (RoomRules.Outcome outcome : RoomRules.Outcome.values()) {
            assertTrue(outcome.messageKey().startsWith("message.eternalrealm.inn."));
        }
    }
}
