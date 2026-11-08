package com.eternalrealm.dungeon.hazard;

import com.eternalrealm.combat.Telegraph;
import com.eternalrealm.dungeon.BlockBox;
import com.eternalrealm.dungeon.DungeonDefinition;
import com.eternalrealm.dungeon.DungeonLayout;
import com.eternalrealm.dungeon.DungeonLayoutGenerator;
import com.eternalrealm.dungeon.DungeonTheme;
import com.eternalrealm.dungeon.Dungeons;
import com.eternalrealm.dungeon.RoomLayout;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HazardPlannerTest {
    @Test
    public void everyDungeonThemeHasCorridorAndRoomTraps() {
        for (DungeonDefinition dungeon : Dungeons.all()) {
            List<HazardKind> kinds = HazardKind.of(dungeon.theme());
            assertTrue(dungeon.id(), kinds.stream().anyMatch(kind -> kind.placement().corridors()));
            assertTrue(dungeon.id(), kinds.stream().anyMatch(kind -> kind.placement().rooms()));
        }
        assertTrue("the final boss has its own attacks", HazardKind.of(DungeonTheme.END_THRONE).isEmpty());
        for (HazardKind kind : HazardKind.values()) {
            assertTrue(kind + " warns long enough", kind.warnTicks() >= HazardKind.MIN_WARN_TICKS);
            assertTrue(kind + " rests longer than it warns", kind.cooldownTicks() > kind.warnTicks());
            assertTrue(kind + " damage", kind.damageFraction() > 0.0 && kind.damageFraction() <= 0.2);
            boolean used = false;
            for (DungeonTheme theme : DungeonTheme.values()) {
                used |= HazardKind.of(theme).contains(kind);
            }
            assertTrue(kind + " belongs to a theme", used);
        }
    }

    @Test
    public void planningIsDeterministicPerLayoutSeed() {
        for (DungeonDefinition dungeon : Dungeons.all()) {
            DungeonLayout layout = DungeonLayoutGenerator.generate(dungeon, 1234L);
            assertEquals(HazardPlanner.plan(layout), HazardPlanner.plan(DungeonLayoutGenerator.generate(dungeon, 1234L)));
        }
        DungeonLayout a = DungeonLayoutGenerator.generate(Dungeons.D3, 1L);
        DungeonLayout b = DungeonLayoutGenerator.generate(Dungeons.D3, 2L);
        assertNotEquals(HazardPlanner.plan(a), HazardPlanner.plan(b));
    }

    @Test
    public void trapsStayInCorridorsAndCombatRooms() {
        Map<HazardKind, Integer> seen = new HashMap<>();
        for (DungeonDefinition dungeon : Dungeons.all()) {
            for (long seed = 0; seed < 30; seed++) {
                DungeonLayout layout = DungeonLayoutGenerator.generate(dungeon, seed);
                List<HazardSite> sites = HazardPlanner.plan(layout);
                assertFalse(dungeon.id() + " seed " + seed + " has traps", sites.isEmpty());
                List<BlockBox> rooms = new ArrayList<>();
                for (RoomLayout room : layout.rooms()) {
                    rooms.add(room.bounds().interior());
                }
                for (int i = 0; i < sites.size(); i++) {
                    HazardSite site = sites.get(i);
                    assertEquals("indices are positions", i, site.index());
                    assertTrue(HazardKind.of(dungeon.theme()).contains(site.kind()));
                    seen.merge(site.kind(), 1, Integer::sum);
                    boolean inCorridor = layout.corridors().stream().anyMatch(c -> c.contains(site.zone()));
                    boolean inRoom = rooms.contains(site.zone());
                    assertTrue("zone in a corridor or a room: " + site, inCorridor || inRoom);
                    assertFalse("never in the entrance hall", layout.entranceHall().intersects(site.zone()));
                    assertFalse("never in the boss arena", layout.bossArena().intersects(site.zone()));
                    assertTrue(site.kind() + " placed where it may be",
                            inRoom ? site.kind().placement().rooms() : site.kind().placement().corridors());
                    assertTrue("centre inside its zone: " + site, site.inZone(site.x(), 1.0, site.z()));
                    if (inRoom && site.kind().trigger() != HazardKind.Trigger.TARGETED) {
                        Telegraph marker = site.telegraph(0, 0, 0);
                        double reach = site.kind().shape() == Telegraph.Kind.CROSS
                                ? site.a() : marker.boundingRadius();
                        BlockBox zone = site.zone();
                        assertTrue("marker inside the room: " + site, site.x() - reach >= zone.minX() - 0.01
                                && site.x() + reach <= zone.maxX() + 1.01 && site.z() - reach >= zone.minZ() - 0.01
                                && site.z() + reach <= zone.maxZ() + 1.01);
                    }
                }
                for (BlockBox room : rooms) {
                    long targeted = sites.stream().filter(s -> s.zone().equals(room)
                            && s.kind().trigger() == HazardKind.Trigger.TARGETED).count();
                    assertTrue("at most one targeted trap per room", targeted <= 1);
                }
            }
        }
        for (DungeonDefinition dungeon : Dungeons.all()) {
            for (HazardKind kind : HazardKind.of(dungeon.theme())) {
                assertNotNull(kind + " is placed for some seed", seen.get(kind));
            }
        }
    }

    @Test
    public void corridorStripsLieAlongTheCorridor() {
        DungeonLayout layout = DungeonLayoutGenerator.generate(Dungeons.D6, 99L);
        int checked = 0;
        for (HazardSite site : HazardPlanner.plan(layout)) {
            if (site.kind().shape() != Telegraph.Kind.RECT) {
                continue;
            }
            Telegraph strip = site.telegraph(0, 0, 0);
            assertTrue("covers its centre", strip.contains(site.x(), 1.0, site.z()));
            double radians = Math.toRadians(site.yaw());
            double sideX = Math.cos(radians);
            double sideZ = Math.sin(radians);
            assertFalse("not wider than the passage", strip.contains(site.x() + sideX * 2.2, 1.0, site.z() + sideZ * 2.2));
            assertTrue("fits between the walls", strip.b() <= 3.0 + 1.0E-9);
            checked++;
        }
        assertTrue("the volcano has flame jets in its corridors", checked > 0);
    }

    @Test
    public void theFinalRaidHasNoTraps() {
        assertTrue(HazardPlanner.plan(DungeonLayoutGenerator.raidArena(Dungeons.FINAL_RAID)).isEmpty());
    }

    @Test
    public void shortCorridorsAreLeftAlone() {
        assertEquals(null, HazardPlanner.passage(new BlockBox(10, 0, 10, 16, 5, 14)));
        HazardPlanner.Passage passage = HazardPlanner.passage(new BlockBox(10, 0, 10, 29, 5, 14));
        assertNotNull(passage);
        assertTrue(passage.alongX());
        assertEquals(20, passage.length());
        assertEquals(12, passage.lane());
        assertEquals(new BlockBox(10, 1, 11, 29, 4, 13), passage.zone());
    }
}
