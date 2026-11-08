package com.eternalrealm.dungeon.hazard;

import com.eternalrealm.combat.Telegraph;
import com.eternalrealm.dungeon.BlockBox;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HazardFieldTest {
    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);
    /** A 15x15 room interior in layout coordinates; the instance origin is at (1000, 64, 0). */
    private static final BlockBox ROOM = new BlockBox(10, 1, 10, 24, 7, 24);
    private static final double OX = 1000.0;
    private static final double OY = 64.0;
    private static final double OZ = 0.0;

    private static HazardField field(HazardSite site) {
        return new HazardField(List.of(site), OX, OY, OZ, 42L);
    }

    private static HazardField.Victim at(UUID id, double localX, double localZ) {
        return new HazardField.Victim(id, OX + localX, OY + 1.0, OZ + localZ);
    }

    /** Ticks from {@code from} to {@code to} (inclusive) with fixed victims; returns the events with their tick. */
    private static List<Object[]> run(HazardField field, long from, long to, List<HazardField.Victim> victims) {
        List<Object[]> events = new ArrayList<>();
        for (long now = from; now <= to; now++) {
            for (HazardEvent event : field.tick(now, victims)) {
                events.add(new Object[] {now, event});
            }
        }
        return events;
    }

    @Test
    public void aPeriodicTrapWarnsBeforeItHitsAndOnlyHitsWhoIsInside() {
        HazardSite geyser = new HazardSite(0, HazardKind.SAND_GEYSER, ROOM, 17.5, 17.5, 0.0, 2.2, 0.0, 20);
        HazardField field = field(geyser);
        List<HazardField.Victim> victims = List.of(at(ALICE, 17.5, 18.0), at(BOB, 22.5, 22.5));
        List<Object[]> events = run(field, 100, 100 + 20 + HazardKind.SAND_GEYSER.warnTicks(), victims);
        assertEquals(2, events.size());
        HazardEvent.Warn warn = (HazardEvent.Warn) events.get(0)[1];
        assertEquals("first warning after the entry delay", 120L, events.get(0)[0]);
        assertEquals(120L + HazardKind.SAND_GEYSER.warnTicks(), warn.fireTick());
        HazardEvent.Strike strike = (HazardEvent.Strike) events.get(1)[1];
        assertEquals("the hit comes exactly when the marker is full", warn.fireTick(), events.get(1)[0]);
        assertEquals(warn.telegraph(), strike.telegraph());
        assertEquals("only the victim inside the marker", List.of(ALICE), strike.hit());
        assertEquals(OY + 1.0, strike.telegraph().y(), 1.0E-9);
        assertEquals(Telegraph.Style.DANGER, strike.telegraph().style());
    }

    @Test
    public void nothingHappensWhileTheZoneIsEmpty() {
        HazardField field = field(new HazardSite(0, HazardKind.LAVA_GEYSER, ROOM, 17.5, 17.5, 0.0, 2.3, 0.0, 20));
        assertTrue(run(field, 0, 400, List.of(at(ALICE, 40.0, 40.0))).isEmpty());
        assertTrue(run(field, 401, 600, List.of()).isEmpty());
    }

    @Test
    public void aWarnedHitAlwaysFollowsAndTheNextWaitsForTheCooldown() {
        HazardKind kind = HazardKind.FLAME_JET;
        BlockBox corridor = new BlockBox(30, 1, 11, 49, 4, 13);
        HazardSite jet = new HazardSite(0, kind, corridor, 40.0, 12.5, 270.0, 8.0, 3.0, 0);
        HazardField field = field(jet);
        List<HazardEvent> first = field.tick(0, List.of(at(ALICE, 40.0, 12.5)));
        assertEquals(1, first.size());
        assertTrue(first.get(0) instanceof HazardEvent.Warn);
        // everyone leaves: the hit still comes (and hits nobody)
        List<Object[]> rest = run(field, 1, kind.warnTicks(), List.of());
        assertEquals(1, rest.size());
        HazardEvent.Strike strike = (HazardEvent.Strike) rest.get(0)[1];
        assertTrue(strike.hit().isEmpty());
        // back in the zone: no new warning before the cooldown is over
        long struck = (long) rest.get(0)[0];
        List<Object[]> next = run(field, struck + 1, struck + kind.cooldownTicks(), List.of(at(ALICE, 35.0, 12.5)));
        assertEquals(1, next.size());
        assertEquals(struck + kind.cooldownTicks(), next.get(0)[0]);
    }

    @Test
    public void aStripHitsAlongTheCorridorOnly() {
        BlockBox corridor = new BlockBox(30, 1, 11, 49, 4, 13);
        HazardSite jet = new HazardSite(0, HazardKind.FLAME_JET, corridor, 40.0, 12.5, 270.0, 8.0, 3.0, 0);
        Telegraph strip = jet.telegraph(OX, OY, OZ);
        assertTrue(strip.contains(OX + 36.5, OY + 1, OZ + 12.5));
        assertTrue(strip.contains(OX + 43.5, OY + 1, OZ + 12.5));
        assertTrue("an edge of the passage", strip.contains(OX + 40.0, OY + 1, OZ + 13.9));
        assertTrue(!strip.contains(OX + 45.0, OY + 1, OZ + 12.5));
        assertTrue(!strip.contains(OX + 34.5, OY + 1, OZ + 12.5));
    }

    @Test
    public void aProximityTrapWaitsForSomeoneToStepOnIt() {
        HazardSite tile = new HazardSite(0, HazardKind.CRUMBLING_FLOOR, ROOM, 17.5, 17.5, 0.0, 1.5, 0.0, 0);
        HazardField field = field(tile);
        assertTrue("in the room but not on the tile", run(field, 0, 200, List.of(at(ALICE, 12.5, 12.5))).isEmpty());
        List<HazardEvent> stepped = field.tick(201, List.of(at(ALICE, 17.0, 18.0)));
        assertEquals(1, stepped.size());
        HazardEvent.Warn warn = (HazardEvent.Warn) stepped.get(0);
        assertEquals(201L + HazardKind.CRUMBLING_FLOOR.warnTicks(), warn.fireTick());
        // she steps off in time
        List<Object[]> after = run(field, 202, warn.fireTick(), List.of(at(ALICE, 21.5, 18.0)));
        assertEquals(1, after.size());
        assertTrue(((HazardEvent.Strike) after.get(0)[1]).hit().isEmpty());
    }

    @Test
    public void aTargetedTrapFallsWhereSomeoneStood() {
        HazardSite rocks = new HazardSite(0, HazardKind.ROCKFALL, ROOM, 17.5, 17.5, 0.0, 1.8, 0.0, 5);
        HazardField field = field(rocks);
        List<HazardField.Victim> standing = List.of(at(ALICE, 12.0, 20.0));
        List<Object[]> events = run(field, 0, 5, standing);
        assertEquals(1, events.size());
        Telegraph marker = ((HazardEvent.Warn) events.get(0)[1]).telegraph();
        assertEquals(OX + 12.0, marker.x(), 1.0E-9);
        assertEquals(OZ + 20.0, marker.z(), 1.0E-9);
        // she moved 3 blocks: the rocks miss
        List<Object[]> hit = run(field, 6, 5 + HazardKind.ROCKFALL.warnTicks(), List.of(at(ALICE, 15.0, 20.0)));
        assertEquals(1, hit.size());
        assertTrue(((HazardEvent.Strike) hit.get(0)[1]).hit().isEmpty());
        // a victim at the wall: the marker stays inside the room
        HazardField corner = field(rocks);
        List<Object[]> clamped = run(corner, 0, 5, List.of(at(BOB, 10.05, 24.95)));
        Telegraph cornerMarker = ((HazardEvent.Warn) clamped.get(0)[1]).telegraph();
        assertEquals(OX + 10.5, cornerMarker.x(), 1.0E-9);
        assertEquals(OZ + 24.5, cornerMarker.z(), 1.0E-9);
    }

    @Test
    public void choosingTheVictimIsDeterministic() {
        HazardSite rocks = new HazardSite(0, HazardKind.ICE_SPIKES, ROOM, 17.5, 17.5, 0.0, 1.8, 0.0, 0);
        List<HazardField.Victim> two = List.of(at(ALICE, 12.5, 12.5), at(BOB, 22.5, 22.5));
        Telegraph first = ((HazardEvent.Warn) field(rocks).tick(7, two).get(0)).telegraph();
        Telegraph again = ((HazardEvent.Warn) field(rocks).tick(7, two).get(0)).telegraph();
        assertEquals(first, again);
    }

    @Test
    public void resetForgetsWarnings() {
        HazardField field = field(new HazardSite(0, HazardKind.SPORE_BLOOM, ROOM, 17.5, 17.5, 0.0, 2.6, 0.0, 0));
        field.tick(0, List.of(at(ALICE, 17.5, 17.5)));
        assertTrue(field.anyPending());
        field.reset();
        assertNull(field.pending(0));
        assertTrue(run(field, 1, 40, List.of()).isEmpty());
    }
}
