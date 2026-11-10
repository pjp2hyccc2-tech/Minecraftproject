package com.eternalrealm.dungeon.decor;

import com.eternalrealm.dungeon.BlockBox;
import com.eternalrealm.dungeon.BlockPoint;
import com.eternalrealm.dungeon.DungeonDefinition;
import com.eternalrealm.dungeon.DungeonLayout;
import com.eternalrealm.dungeon.DungeonLayoutGenerator;
import com.eternalrealm.dungeon.DungeonTheme;
import com.eternalrealm.dungeon.Dungeons;
import com.eternalrealm.dungeon.RoomLayout;
import org.junit.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The decoration keeps every gameplay rule of the layout (docs/modules/dungeons.md): nothing in door openings, lanes or
 * on spawn points, corridor passages and the boss arena's air stay free, the way from the entrance to the arena stays
 * walkable, every fighting space is lit, and everything it builds is cleared with the instance. And each theme has its
 * own vocabulary.
 */
public class DungeonDecorTest {
    private static final long[] SEEDS = {1L, 2L, 3L};
    private static final Map<DungeonLayout, DungeonDecor.Planner> PLANNED = new java.util.HashMap<>();

    private static List<DungeonLayout> layouts() {
        List<DungeonLayout> layouts = new ArrayList<>();
        for (long seed : SEEDS) {
            for (DungeonDefinition dungeon : Dungeons.all()) {
                layouts.add(DungeonLayoutGenerator.generate(dungeon, seed));
            }
        }
        layouts.add(DungeonLayoutGenerator.raidArena(Dungeons.FINAL_RAID));
        return layouts;
    }

    /** The planner after its run, once per layout for the whole class (planning takes a moment). */
    private static synchronized DungeonDecor.Planner planned(DungeonLayout layout) {
        return PLANNED.computeIfAbsent(layout, DungeonDecor::planned);
    }

    private static DecorPlan plan(DungeonLayout layout) {
        return planned(layout).plan;
    }

    private static String name(DungeonLayout layout) {
        return layout.id() + "/" + layout.seed();
    }

    @Test
    public void theSameLayoutGetsTheSameDecoration() {
        DungeonLayout layout = DungeonLayoutGenerator.generate(Dungeons.D3, 42L);
        DecorPlan a = DungeonDecor.plan(layout);
        DecorPlan b = DungeonDecor.plan(layout);
        assertEquals(a, b);
        assertTrue("decorated: " + a.blocks().size(), a.blocks().size() > 3000);
    }

    @Test
    public void theDerivedOpeningsAreTheLayoutsDoors() {
        for (DungeonLayout layout : layouts()) {
            List<Geometry.Link> links = Geometry.links(layout);
            assertEquals(name(layout), layout.entranceDoor().opening(), links.get(0).fromOpening());
            for (int i = 0; i < layout.doors().size(); i++) {
                assertEquals(name(layout), layout.doors().get(i).opening(), links.get(i + 1).fromOpening());
            }
        }
    }

    @Test
    public void nothingIsBuiltInDoorsLanesSpawnPointsPassagesOrTheArenaAir() {
        for (DungeonLayout layout : layouts()) {
            DecorPlan plan = plan(layout);
            List<BlockBox> hard = new ArrayList<>();
            List<BlockBox> passages = new ArrayList<>();
            for (Geometry.Link link : Geometry.links(layout)) {
                hard.addAll(List.of(link.fromOpening(), link.toOpening(), link.fromLane(), link.toLane()));
                passages.add(link.passage());
            }
            for (RoomLayout room : layout.rooms()) {
                for (BlockPoint p : room.spawnPoints()) {
                    hard.add(new BlockBox(p.x() - 1, 1, p.z() - 1, p.x() + 1, 3, p.z() + 1));
                }
            }
            BlockBox arena = layout.bossArena().interior();
            for (DecorBlock block : plan.blocks()) {
                String where = name(layout) + " " + block;
                for (BlockBox box : hard) {
                    if (box.contains(block.x(), block.y(), block.z())) {
                        fail("decoration in a door, lane or spawn point: " + where);
                    }
                }
                boolean blocks = !block.isRole() && DecorBlocks.blocks(block.state()) || block.isRole();
                for (BlockBox passage : passages) {
                    if (passage.contains(block.x(), block.y(), block.z()) && blocks) {
                        boolean arch = block.y() == passage.maxY() && block.state().contains("_stairs[")
                                && block.state().contains("half=top");
                        if (!arch) {
                            fail("something to bump into in a corridor: " + where);
                        }
                    }
                }
                if (arena.contains(block.x(), block.y(), block.z()) && blocks) {
                    fail("something in the boss arena's air: " + where);
                }
            }
        }
    }

    @Test
    public void everyWalkableFloorOfTheFightingSpacesIsLit() {
        for (DungeonLayout layout : layouts()) {
            DungeonDecor.Planner planner = planned(layout);
            byte[] light = planner.c.light(DungeonDecor.paletteLight(layout.theme()));
            for (Spaces.Space space : planner.spaces) {
                BlockBox in = space.inside();
                for (int x = in.minX(); x <= in.maxX(); x++) {
                    for (int z = in.minZ(); z <= in.maxZ(); z++) {
                        int y = planner.feet(space, x, z);
                        if (y != Integer.MIN_VALUE && Canvas.light(light, x, y, z) < DungeonDecor.TARGET_LIGHT) {
                            fail(name(layout) + " " + space.kind() + " is dark at " + x + "," + y + "," + z + ": "
                                    + Canvas.light(light, x, y, z));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void theWayFromTheEntranceToTheArenaAndEverySpawnPointIsWalkable() {
        for (DungeonLayout layout : layouts()) {
            DungeonDecor.Planner planner = planned(layout);
            Canvas c = planner.c;
            BitSet reached = c.walk(layout.entry());
            BlockPoint spawn = layout.bossSpawn();
            assertTrue(name(layout) + ": the boss arena cannot be reached",
                    reached.get(Canvas.index(spawn.x(), spawn.y(), spawn.z())));
            for (RoomLayout room : layout.rooms()) {
                for (BlockPoint p : room.spawnPoints()) {
                    assertTrue(name(layout) + ": spawn point " + p + " cannot be reached",
                            reached.get(Canvas.index(p.x(), p.y(), p.z())));
                }
            }
            BlockPoint revival = planner.plan.revivalPoint();
            assertTrue(name(layout) + ": the revival point " + revival + " cannot be reached",
                    reached.get(Canvas.index(revival.x(), revival.y(), revival.z())));
            for (Spaces.SetPiece piece : planner.setPieces) {
                BlockBox in = piece.box().interior();
                boolean any = false;
                for (int x = in.minX(); x <= in.maxX() && !any; x++) {
                    for (int z = in.minZ(); z <= in.maxZ() && !any; z++) {
                        any = reached.get(Canvas.index(x, 1, z));
                    }
                }
                assertTrue(name(layout) + ": set piece " + piece.kind() + " cannot be reached", any);
            }
        }
    }

    @Test
    public void theRevivalPointStandsInTheHallFacingTheEntranceDoor() {
        for (DungeonLayout layout : layouts()) {
            DungeonDecor.Planner planner = planned(layout);
            DecorPlan plan = planner.plan;
            BlockPoint p = plan.revivalPoint();
            assertTrue(name(layout), plan.hall().interior().contains(p));
            assertTrue(name(layout), plan.hall().contains(layout.entranceHall()));
            assertTrue(name(layout) + ": nothing to stand on at " + p, planner.c.standable(p.x(), p.y(), p.z()));
            Geometry.Side front = Geometry.sideOf(layout.entranceHall(), layout.entranceDoor().opening());
            assertEquals(name(layout), front.yaw(), plan.revivalYaw(), 0.0F);
        }
    }

    @Test
    public void everythingBuiltIsClearedWithTheInstanceAndFluidsAreWalledIn() {
        for (DungeonLayout layout : layouts()) {
            DungeonDecor.Planner planner = planned(layout);
            Canvas c = planner.c;
            for (DecorBlock block : planner.plan.blocks()) {
                if (!c.covered(block.x(), block.y(), block.z())) {
                    fail(name(layout) + ": not cleared: " + block);
                }
                if (block.isRole() || !DecorBlocks.fluid(block.state())) {
                    continue;
                }
                boolean listed = planner.plan.fluidBoxes().stream()
                        .anyMatch(box -> box.contains(block.x(), block.y(), block.z()));
                assertTrue(name(layout) + ": fluid not drained first: " + block, listed);
                String fluid = DecorBlocks.name(block.state());
                int[][] around = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};
                for (int[] d : around) {
                    String next = c.state(block.x() + d[0], block.y() + d[1], block.z() + d[2]);
                    boolean sameFluid = DecorBlocks.name(next).equals(fluid);
                    if (!sameFluid && !c.blocks(block.x() + d[0], block.y() + d[1], block.z() + d[2])) {
                        fail(name(layout) + ": " + fluid + " can flow out of " + block + " into " + next);
                    }
                }
            }
            for (BlockBox box : planner.plan.extraBoxes()) {
                assertTrue(name(layout) + ": " + box, DungeonLayout.BOUNDS.contains(new BlockBox(box.minX(), 0,
                        box.minZ(), box.maxX(), 0, box.maxZ())) && box.minY() >= Canvas.MIN_Y
                        && box.maxY() <= Canvas.MAX_Y);
            }
        }
    }

    @Test
    public void statesAreWellFormedAndNothingValuableIsLeftAround() {
        Set<String> valuable = Set.of("gold_block", "iron_block", "diamond_block", "emerald_block", "netherite_block",
                "lapis_block", "redstone_block", "coal_block", "raw_gold_block", "raw_iron_block", "raw_copper_block",
                "beacon", "ancient_debris", "conduit", "enchanting_table", "ender_chest", "respawn_anchor",
                "dragon_head", "dragon_wall_head", "tnt", "sculk_shrieker", "sculk_sensor");
        Set<String> roles = Set.of("#wall", "#floor", "#pillar", "#accent", "#light");
        for (DungeonLayout layout : layouts()) {
            for (DecorBlock block : plan(layout).blocks()) {
                if (block.isRole()) {
                    assertTrue(block.toString(), roles.contains(block.state()));
                    continue;
                }
                assertTrue("malformed: " + block, DecorBlocks.wellFormed(block.state()));
                assertTrue("valuable: " + block, !valuable.contains(DecorBlocks.name(block.state())));
            }
        }
    }

    @Test
    public void eachThemeHasItsOwnVocabulary() {
        Map<DungeonTheme, Set<String>> words = new EnumMap<>(DungeonTheme.class);
        for (DungeonLayout layout : layouts()) {
            Set<String> ids = words.computeIfAbsent(layout.theme(), theme -> new HashSet<>());
            for (DecorBlock block : plan(layout).blocks()) {
                if (!block.isRole()) {
                    ids.add(DecorBlocks.id(block.state()));
                }
            }
        }
        Set<String> common = Set.of("minecraft:air", "minecraft:light", "minecraft:chain", "minecraft:iron_bars",
                "minecraft:lantern");
        for (Map.Entry<DungeonTheme, Set<String>> entry : words.entrySet()) {
            entry.getValue().removeAll(common);
            assertTrue(entry.getKey() + " uses only " + entry.getValue().size() + " blocks", entry.getValue().size() >= 25);
        }
        List<DungeonTheme> themes = new ArrayList<>(words.keySet());
        for (int i = 0; i < themes.size(); i++) {
            for (int j = i + 1; j < themes.size(); j++) {
                Set<String> a = words.get(themes.get(i));
                Set<String> b = words.get(themes.get(j));
                Set<String> both = new HashSet<>(a);
                both.retainAll(b);
                Set<String> either = new HashSet<>(a);
                either.addAll(b);
                double similarity = both.size() / (double) either.size();
                assertTrue(themes.get(i) + " and " + themes.get(j) + " share " + both, similarity < 0.35);
            }
        }
    }

    @Test
    public void theAmbienceOfEveryThemeHasMotesAndSounds() {
        for (DungeonTheme theme : DungeonTheme.values()) {
            DungeonAmbience ambience = DungeonAmbience.of(theme);
            assertTrue(theme.id(), !ambience.motes().isEmpty() && !ambience.sounds().isEmpty());
            assertTrue(theme.id(), ambience.motesPerSecond() > 0 && ambience.motesPerSecond() <= 20);
            assertTrue(theme.id(), ambience.soundEvery() >= 100);
            for (DungeonAmbience.Mote mote : ambience.motes()) {
                assertTrue(mote.particle(), mote.particle().matches("[a-z_]+:[a-z_]+(/[a-z_]+:[a-z_]+)?"));
            }
            for (String sound : ambience.sounds()) {
                assertTrue(sound, sound.matches("[a-z_]+:[a-z_.]+"));
            }
        }
        for (DungeonDefinition dungeon : Dungeons.all()) {
            assertEquals(dungeon.theme(), DungeonAmbience.themeOf(dungeon.id()).orElseThrow());
        }
        assertEquals(DungeonTheme.END_THRONE, DungeonAmbience.themeOf(Dungeons.FINAL_RAID.id()).orElseThrow());
        assertTrue(DungeonAmbience.themeOf("nowhere").isEmpty());
    }
}
