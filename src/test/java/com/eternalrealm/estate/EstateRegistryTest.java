package com.eternalrealm.estate;

import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class EstateRegistryTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID GUILD = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Test
    public void keysNameOneEstatePerKindAndOwner() {
        EstateKey key = new EstateKey(EstateKey.ROOM, ALICE, "attic");
        assertTrue(key.sameEstate(key.withDesign("suite")));
        assertFalse(key.sameEstate(new EstateKey(EstateKey.GUILD, ALICE, "attic")));
        assertEquals("room/" + ALICE, key.id());
        assertEquals("estates/room/attic", key.blueprintFolder());
        assertThrows(IllegalArgumentException.class, () -> new EstateKey("Room", ALICE, "attic"));
        assertThrows(IllegalArgumentException.class, () -> new EstateKey("room", ALICE, "../x"));
        assertThrows(NullPointerException.class, () -> new EstateKey("room", null, "attic"));
    }

    @Test
    public void claimingGivesEachEstateItsOwnPlot() {
        EstateRegistry registry = new EstateRegistry();
        EstateRegistry.Change alice = registry.claim(new EstateKey(EstateKey.ROOM, ALICE, "attic"), 1L);
        EstateRegistry.Change bob = registry.claim(new EstateKey(EstateKey.ROOM, BOB, "attic"), 2L);
        EstateRegistry.Change guild = registry.claim(new EstateKey(EstateKey.GUILD, GUILD, "hall"), 3L);
        assertEquals(EstateResult.OK, alice.result());
        assertNotEquals(alice.record().orElseThrow().plot(), bob.record().orElseThrow().plot());
        assertEquals(EstatePlots.GUILD_REGION, guild.record().orElseThrow().plot().region());
        assertEquals("one estate per kind and owner", EstateResult.ALREADY_CLAIMED,
                registry.claim(new EstateKey(EstateKey.ROOM, ALICE, "suite"), 4L).result());
        assertEquals("a guild house does not count as a room", EstateResult.OK,
                registry.claim(new EstateKey(EstateKey.GUILD, ALICE, "hall"), 5L).result());
        EstatePlot alicePlot = alice.record().orElseThrow().plot();
        assertEquals(alice.record(), registry.atPlot(alicePlot));
        assertEquals("attic", registry.get(EstateKey.ROOM, ALICE).orElseThrow().key().design());
        assertEquals(2, registry.ofKind(EstateKey.ROOM).size());
    }

    @Test
    public void aMoveKeepsThePreviousPlotUntilItIsFinished() {
        EstateRegistry registry = new EstateRegistry();
        EstateRecord claimed = registry.claim(new EstateKey(EstateKey.ROOM, ALICE, "attic"), 1L).record().orElseThrow();
        long revision = registry.revision();
        EstateRecord moving = registry.startMove(claimed.id(), "suite").record().orElseThrow();
        assertTrue(registry.revision() > revision);
        assertEquals("suite", moving.key().design());
        assertEquals(claimed.plot(), moving.previous());
        assertEquals("players still use the old build", claimed.plot(), moving.livePlot());
        assertNotEquals(claimed.plot(), moving.plot());
        assertEquals(Optional.of(moving), registry.atPlot(claimed.plot()));
        assertEquals(Optional.of(moving), registry.atPlot(moving.plot()));
        assertEquals(EstateResult.BUSY, registry.startMove(claimed.id(), "attic").result());

        assertEquals(Optional.of(claimed.plot()), registry.finishMove(claimed.id()));
        EstateRecord moved = registry.get(claimed.id()).orElseThrow();
        assertFalse(moved.moving());
        assertEquals(moving.plot(), moved.livePlot());
        assertEquals(Optional.empty(), registry.atPlot(claimed.plot()));
        assertEquals(Set.of(claimed.plot()), registry.wiping());
        assertTrue("taken until wiped", registry.isTaken(claimed.plot()));
        EstatePlot fresh = registry.claim(new EstateKey(EstateKey.ROOM, BOB, "attic"), 2L).record().orElseThrow().plot();
        assertNotEquals("a plot waiting to be wiped is not handed out", claimed.plot(), fresh);
        assertTrue(registry.wiped(claimed.plot()));
        assertFalse(registry.isTaken(claimed.plot()));
        assertFalse(registry.wiped(claimed.plot()));
        assertEquals(Optional.empty(), registry.finishMove(claimed.id()));
    }

    @Test
    public void anAbandonedMoveGoesBackToTheOldDesign() {
        EstateRegistry registry = new EstateRegistry();
        EstateRecord claimed = registry.claim(new EstateKey(EstateKey.ROOM, ALICE, "attic"), 1L).record().orElseThrow();
        EstateRecord moving = registry.startMove(claimed.id(), "suite").record().orElseThrow();
        assertEquals(Optional.of(moving.plot()), registry.abandonMove(claimed.id(), "attic"));
        EstateRecord back = registry.get(claimed.id()).orElseThrow();
        assertEquals(claimed.key(), back.key());
        assertEquals(claimed.plot(), back.plot());
        assertFalse(back.moving());
        assertEquals(Set.of(moving.plot()), registry.wiping());
    }

    @Test
    public void releasedPlotsWaitToBeWipedAndOrphansAreCollected() {
        EstateRegistry registry = new EstateRegistry();
        EstateRecord claimed = registry.claim(new EstateKey(EstateKey.ROOM, ALICE, "attic"), 1L).record().orElseThrow();
        EstateRecord moving = registry.startMove(claimed.id(), "suite").record().orElseThrow();
        assertEquals(Optional.of(moving), registry.release(claimed.id()));
        assertEquals(Set.of(claimed.plot(), moving.plot()), registry.wiping());
        assertEquals(Optional.empty(), registry.get(claimed.id()));
        assertEquals(Optional.empty(), registry.release(claimed.id()));
        EstatePlot stray = new EstatePlot(0, 40);
        assertTrue(registry.markOrphan(stray));
        assertFalse(registry.markOrphan(stray));
        assertTrue(registry.isTaken(stray));
        EstateRecord bob = registry.claim(new EstateKey(EstateKey.ROOM, BOB, "attic"), 2L).record().orElseThrow();
        assertFalse("a plot in use is no orphan", registry.markOrphan(bob.plot()));
    }

    @Test
    public void snapshotsRoundTripAndInconsistentDataIsRefused() {
        EstateRegistry registry = new EstateRegistry();
        EstateRecord claimed = registry.claim(new EstateKey(EstateKey.ROOM, ALICE, "attic"), 1L).record().orElseThrow();
        registry.startMove(claimed.id(), "suite");
        registry.claim(new EstateKey(EstateKey.GUILD, GUILD, "hall"), 2L);
        registry.markOrphan(new EstatePlot(0, 9));
        EstateData data = registry.snapshot();
        EstateRegistry copy = EstateRegistry.of(data);
        assertEquals(data, copy.snapshot());
        assertTrue(copy.isTaken(new EstatePlot(0, 9)));
        assertEquals("the next plot is not one in use", new EstatePlot(0, 2),
                copy.claim(new EstateKey(EstateKey.ROOM, BOB, "attic"), 3L).record().orElseThrow().plot());

        EstateRecord a = new EstateRecord(new EstateKey(EstateKey.ROOM, ALICE, "attic"), new EstatePlot(0, 1), null, 1L);
        EstateRecord b = new EstateRecord(new EstateKey(EstateKey.ROOM, BOB, "attic"), new EstatePlot(0, 1), null, 1L);
        assertThrows(IllegalArgumentException.class, () -> EstateRegistry.of(new EstateData(List.of(a, b), List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> EstateRegistry.of(new EstateData(List.of(a), List.of(new EstatePlot(0, 1)))));
        assertThrows(IllegalArgumentException.class, () -> EstateRegistry.of(new EstateData(List.of(a, a), List.of())));
        assertThrows("a room in the guild region", IllegalArgumentException.class,
                () -> new EstateRecord(new EstateKey(EstateKey.ROOM, ALICE, "attic"), new EstatePlot(1, 0), null, 1L));
    }
}
