package com.hitori.debug;

import com.hitori.Hitori;
import com.hitori.block.BalconyDoorBlock;
import com.hitori.block.HitoriDoorBlock;
import com.hitori.block.Props;
import com.hitori.block.SlidingDoorBlock;
import com.hitori.registry.ModBlocks;
import com.hitori.world.ApartmentBuilder;
import com.hitori.world.BlockSink;
import com.hitori.world.Fixtures;
import com.hitori.world.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Checks of コーポ白樺 and room 203 (owner: world): every marker of DESIGN §2 exists, points at the right block and is in
 * its room, standing markers are walkable, the doll's route is connected, the lights follow their switches and the
 * breaker, the TV, the taps and the bath filling, the doors and the closet's fusuma (with their collisions), the
 * devices and the ritual items' places work, and {@link Fixtures#resetAll} puts every block back. The game's self
 * test can call {@link #run} (stage by stage, with ticks in between); with {@code -Dhitori.selftest=true} and no
 * {@code com.hitori.debug.SelfTest}, {@link Runner} runs it alone and writes {@code run/hitori-selftest.txt}
 * ("SELFTEST RESULT: PASS/FAIL") and {@code run/hitori-layout.txt}.
 */
public final class WorldCheck {
    private WorldCheck() {}

    /** Markers where something stands (feet position: room for a 0.6 wide body, 2 high, a floor under it). */
    static final String[] STANDING = {"spawn", "peephole", "closet_hide", "closet_peek", "western_closet", "doll_route", "balcony",
            "balcony_western", "corridor", "corridor_door", "stairs_top", "stairs_bottom", "street"};
    /** Expected room of markers (exact names). */
    static final String[][] ROOMS = {{"spawn", "western"}, {"morning", "western"}, {"breaker", "genkan"}, {"intercom", "genkan"}, {"tv", "tatami"},
            {"table", "tatami"}, {"bathtub", "bath"}, {"bath_tap", "bath"}, {"washbasin", "washroom"}, {"kitchen_sink", "dk"}, {"rice", "dk"},
            {"salt", "dk"}, {"cup", "dk"}, {"knife", "dk"}, {"lighter", "dk"}, {"scissors", "western"}, {"thread", "western"},
            {"nail_clipper", "washroom"}, {"plush_shelf", "western"}, {"camera_bag", "western"}, {"laptop", "western"}, {"printout", "western"},
            {"phone", "western"}, {"closet_hide", "closet"}, {"closet_peek", "closet"}, {"closet_fusuma", "closet"}, {"western_closet", "western_closet"},
            {"balcony", "balcony"}, {"switch_genkan", "genkan"}, {"switch_hall", "hall"}, {"switch_toilet", "hall"}, {"switch_washroom", "hall"},
            {"switch_bath", "washroom"}, {"switch_dk", "dk"}, {"switch_western", "western"}, {"switch_tatami", "tatami"},
            {"lights_genkan", "genkan"}, {"lights_hall", "hall"}, {"lights_toilet", "toilet"}, {"lights_bath", "bath"},
            {"lights_washroom", "washroom"}, {"lights_dk", "dk"}, {"lights_tatami", "tatami"}, {"lights_western", "western"},
            {"fridge", "dk"}, {"gas_stove", "dk"}, {"washing_machine", "washroom"}, {"wall_clock", "dk"}, {"altar_shelf", "tatami"}};

    // ------------------------------------------------------------------------------------------------ stages
    private static int fillWait;

    /**
     * Runs stage {@code stage} of the checks and returns the number of ticks to wait before the next stage, or -1
     * when done. log receives lines; check(ok, what) records a result.
     */
    public static int run(ServerLevel level, int stage, Consumer<String> log, BiConsumer<Boolean, String> check) {
        switch (stage) {
            case 0 -> {
                long t0 = System.currentTimeMillis();
                for (int cx = -3; cx <= 4; cx++) for (int cz = -2; cz <= 2; cz++) level.setChunkForced(cx, cz, true);
                Fixtures.ensureBuilt(level);
                Fixtures.resetAll(level);
                log.accept("apartment chunks generated and forced in " + (System.currentTimeMillis() - t0) + " ms");
                return 40;
            }
            case 1 -> {
                markers(level, log, check);
                structure(level, log, check);
                return 5;
            }
            case 2 -> {
                routes(level, log, check);
                return 5;
            }
            case 3 -> {
                fixtures(level, log, check);
                return 5;
            }
            case 4 -> {
                // the bath: empty → half → full while the tap runs
                Fixtures.setBathWater(level, 0);
                Fixtures.setBathTap(level, true);
                check.accept(Fixtures.bathTapRunning(level), "the bath tap runs");
                fillWait = 0;
                return 230;
            }
            case 5 -> {
                check.accept(Fixtures.bathWater(level) == 1, "after 11.5 s the tub is half full (water " + Fixtures.bathWater(level) + ")");
                return 200;
            }
            case 6 -> {
                check.accept(Fixtures.bathWater(level) == 2, "after 21.5 s the tub is full (water " + Fixtures.bathWater(level) + ")");
                Fixtures.setBathTap(level, false);
                BlockState lamp = level.getBlockState(Layout.marker("hanging_lamp").pos());
                check.accept(lamp.is(ModBlocks.HANGING_LAMP.get()) && !lamp.getValue(Props.SWING), "the hanging lamp has stopped swinging");
                check.accept(Fixtures.lightsIn(level, "dk").stream().allMatch(p -> level.getBlockState(p).getValue(Props.LIT) == Fixtures.lightsOn(level, "dk")),
                        "the DK's flicker ended and its lamp is back to its switch");
                return 5;
            }
            case 7 -> {
                reset(level, log, check);
                return 60;
            }
            case 8 -> {
                light(level, log);
                Fixtures.setTv(level, true);
                for (String r : Layout.LIGHT_ROOMS) Fixtures.setLights(level, r, false);
                return 60;
            }
            case 9 -> {
                log.accept("the ritual's darkness (every light off, the TV on):");
                light(level, log);
                Fixtures.resetAll(level);
                return -1;
            }
            default -> {
                return -1;
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ geometry
    static final AABB BODY = new AABB(0.2, 0.0, 0.2, 0.8, 1.0, 0.8);

    /** True if a 0.6 wide body fits in the cell (doors, fusuma and the balcony doors count as open). */
    static boolean passable(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        Block b = s.getBlock();
        if (b instanceof HitoriDoorBlock || b instanceof SlidingDoorBlock || b instanceof BalconyDoorBlock) return true;
        VoxelShape shape = s.getCollisionShape(level, p);
        if (shape.isEmpty()) return true;
        for (AABB box : shape.toAabbs()) if (box.intersects(BODY)) return false;
        return true;
    }

    static boolean floor(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p.below());
        VoxelShape shape = s.getCollisionShape(level, p.below());
        return !shape.isEmpty() && shape.max(Direction.Axis.Y) >= 0.5;
    }

    static boolean walkable(ServerLevel level, BlockPos p) {
        return passable(level, p) && passable(level, p.above()) && floor(level, p);
    }

    // ------------------------------------------------------------------------------------------------ markers
    static void markers(ServerLevel level, Consumer<String> log, BiConsumer<Boolean, String> check) {
        String[] required = {"spawn", "door_front", "peephole", "breaker", "tv", "table", "bathtub", "bath_tap", "washbasin", "kitchen_sink",
                "rice", "salt", "cup", "knife", "lighter", "scissors", "thread", "nail_clipper", "plush_shelf", "camera_bag", "laptop", "printout",
                "closet_hide", "closet_fusuma", "closet_peek", "western_closet", "cam_suggest", "doll_route", "doll_hides", "woman_spots",
                "balcony", "intercom", "phone", "morning"};
        int missing = 0;
        for (String r : required) if (!Layout.has(r)) {
            missing++;
            log.accept("  missing marker " + r);
        }
        for (String room : Layout.LIGHT_ROOMS) {
            if (!Layout.has("switch_" + room)) missing++;
            if (!Layout.has("lights_" + room)) missing++;
        }
        for (String d : Layout.DOORS) if (!Layout.has("door_" + d)) missing++;
        check.accept(missing == 0, "every marker of DESIGN §2 exists (" + Layout.all().size() + " names)");
        check.accept(Layout.markers("bathtub").size() == 2 && Layout.markers("closet_hide").size() == 3 && Layout.markers("closet_fusuma").size() == 3
                && Layout.markers("cam_suggest").size() == 4, "list sizes: bathtub 2, closet_hide 3, closet_fusuma 3, cam_suggest 4");
        check.accept(Layout.markers("doll_route").size() >= 10 && Layout.markers("doll_hides").size() >= 6 && Layout.markers("woman_spots").size() >= 4,
                "doll_route ≥ 10, doll_hides ≥ 6, woman_spots ≥ 4");
        int bad = 0;
        for (String name : STANDING) {
            for (Layout.Marker m : Layout.markers(name)) {
                if (!walkable(level, m.pos())) {
                    bad++;
                    log.accept("  not walkable: " + name + " " + m.pos().toShortString() + " feet=" + level.getBlockState(m.pos()).getBlock()
                            + " head=" + level.getBlockState(m.pos().above()).getBlock() + " floor=" + level.getBlockState(m.pos().below()).getBlock());
                }
            }
        }
        check.accept(bad == 0, "standing markers are walkable (" + bad + " bad)");
        int hideBad = 0;
        for (Layout.Marker m : Layout.markers("doll_hides")) {
            BlockState s = level.getBlockState(m.pos());
            boolean ok = walkable(level, m.pos()) || s.is(ModBlocks.CHABUDAI.get()) || s.is(ModBlocks.DINING_TABLE.get());
            if (!ok) {
                hideBad++;
                log.accept("  bad doll hide " + m.pos().toShortString() + " " + s.getBlock());
            }
        }
        check.accept(hideBad == 0, "the doll's hiding spots are reachable cells or tables");
        int ghosts = 0;
        for (Layout.Marker m : Layout.markers("woman_spots")) {
            Layout.Room room = Layout.roomAt(m.pos());
            if (room == null || room.key().equals("corridor") || room.key().equals("balcony")) {
                ghosts++;
                log.accept("  woman spot outside the rooms " + m.pos().toShortString());
            }
        }
        check.accept(ghosts == 0, "the woman's spots are inside 203");
        int wrongRoom = 0;
        for (String[] r : ROOMS) {
            for (Layout.Marker m : Layout.markers(r[0])) {
                Layout.Room room = Layout.roomAt(m.pos());
                if (room == null || !room.key().equals(r[1])) {
                    wrongRoom++;
                    log.accept("  " + r[0] + " " + m.pos().toShortString() + " is in " + (room == null ? "no room" : room.key()) + ", expected " + r[1]);
                }
            }
        }
        check.accept(wrongRoom == 0, "markers are in their rooms (" + wrongRoom + " wrong)");
        // the things the markers point at
        expect(level, check, "tv", ModBlocks.CRT_TV);
        expect(level, check, "table", ModBlocks.CHABUDAI);
        expect(level, check, "bathtub", ModBlocks.BATHTUB);
        expect(level, check, "bath_tap", ModBlocks.BATH_TAP);
        expect(level, check, "washbasin", ModBlocks.WASHBASIN);
        expect(level, check, "kitchen_sink", ModBlocks.KITCHEN_SINK);
        expect(level, check, "rice", ModBlocks.RICE_BAG);
        expect(level, check, "salt", ModBlocks.SALT_SHAKER);
        expect(level, check, "cup", ModBlocks.CUP_GLASS);
        expect(level, check, "knife", ModBlocks.KNIFE_RACK);
        expect(level, check, "lighter", ModBlocks.DRAWER);
        expect(level, check, "scissors", ModBlocks.SEWING_BOX);
        expect(level, check, "thread", ModBlocks.SEWING_BOX);
        expect(level, check, "nail_clipper", ModBlocks.MIRROR_SMALL);
        expect(level, check, "plush_shelf", ModBlocks.PLUSH_DOLL);
        expect(level, check, "camera_bag", ModBlocks.CAMERA_BAG);
        expect(level, check, "laptop", ModBlocks.LAPTOP_BLOCK);
        expect(level, check, "printout", ModBlocks.PRINTOUT);
        expect(level, check, "breaker", ModBlocks.BREAKER_PANEL);
        expect(level, check, "intercom", ModBlocks.INTERCOM);
        expect(level, check, "morning", ModBlocks.DESK_CHAIR);
        expect(level, check, "door_front", ModBlocks.FRONT_DOOR);
        expect(level, check, "closet_fusuma", ModBlocks.OSHIIRE_FUSUMA);
        expect(level, check, "door_western_closet", ModBlocks.OSHIIRE_FUSUMA);
        expect(level, check, "door_toilet", ModBlocks.TOILET_DOOR);
        expect(level, check, "door_washroom", ModBlocks.INTERIOR_DOOR);
        expect(level, check, "door_western", ModBlocks.INTERIOR_DOOR);
        expect(level, check, "door_bath", ModBlocks.BATH_DOOR);
        expect(level, check, "door_tatami_dk", ModBlocks.FUSUMA);
        expect(level, check, "door_tatami_hall", ModBlocks.FUSUMA);
        expect(level, check, "door_balcony_tatami", ModBlocks.BALCONY_DOOR);
        expect(level, check, "door_balcony_western", ModBlocks.BALCONY_DOOR);
        expect(level, check, "curtains_tatami", ModBlocks.CURTAIN);
        expect(level, check, "curtains_western", ModBlocks.CURTAIN);
        expect(level, check, "fridge", ModBlocks.FRIDGE_OLD);
        expect(level, check, "gas_stove", ModBlocks.GAS_STOVE);
        expect(level, check, "washing_machine", ModBlocks.WASHING_MACHINE);
        expect(level, check, "desk_lamp", ModBlocks.DESK_LAMP);
        expect(level, check, "wall_clock", ModBlocks.WALL_CLOCK);
        expect(level, check, "hanging_lamp", ModBlocks.HANGING_LAMP);
        expect(level, check, "closet_futons", null);
        expect(level, check, "street_lamp", ModBlocks.STREET_LAMP_OLD);
        expect(level, check, "vending_machine", ModBlocks.VENDING_MACHINE_SMALL);
        expect(level, check, "swing", ModBlocks.SWING_SET);
        expect(level, check, "unit_204", ModBlocks.UNIT_DOOR);
        for (String room : Layout.LIGHT_ROOMS) {
            expect(level, check, "switch_" + room, "tatami".equals(room) ? ModBlocks.PULL_SWITCH : ModBlocks.LIGHT_SWITCH);
            expect(level, check, "lights_" + room, "tatami".equals(room) ? ModBlocks.HANGING_LAMP : ModBlocks.FLUORESCENT_RING);
            check.accept(room.equals(Fixtures.switchRoom(Layout.marker("switch_" + room).pos())), "switch_" + room + " controls " + room);
        }
        check.accept(level.getBlockState(Layout.marker("knife").pos()).getValue(Props.HAS_KNIFE)
                && level.getBlockState(Layout.marker("plush_shelf").pos()).getValue(Props.PRESENT), "the knife is on its rack and the plush on its shelf");
    }

    /** Every block of a marker list is the block (null: anything but air). */
    static void expect(ServerLevel level, BiConsumer<Boolean, String> check, String marker, Supplier<? extends Block> b) {
        int ok = 0, n = 0;
        StringBuilder found = new StringBuilder();
        for (Layout.Marker m : Layout.markers(marker)) {
            n++;
            BlockState s = level.getBlockState(m.pos());
            if (b == null ? !s.isAir() : s.is(b.get())) ok++;
            else found.append(" ").append(m.pos().toShortString()).append("=").append(s.getBlock().getDescriptionId().replace("block.hitori.", ""));
        }
        String what = b == null ? "filled" : b.get().getDescriptionId().replace("block.hitori.", "");
        check.accept(ok == n, marker + ": " + ok + "/" + n + " " + what + (found.length() > 0 ? " (found" + found + ")" : ""));
    }

    static void structure(ServerLevel level, Consumer<String> log, BiConsumer<Boolean, String> check) {
        check.accept(level.getBlockState(new BlockPos(24, 68, 1)).is(ModBlocks.GENKAN_TILE.get()), "the genkan's tiles at y 68");
        check.accept(level.getBlockState(new BlockPos(25, 69, 1)).is(ModBlocks.GENKAN_STEP.get()), "the genkan's wooden half step at x 25");
        check.accept(level.getBlockState(new BlockPos(26, 69, 4)).is(ModBlocks.OLD_FLOORING.get()), "the hall's flooring at y 69");
        check.accept(level.getBlockState(new BlockPos(25, 69, 10)).getBlock() instanceof com.hitori.block.TatamiBlock, "tatami in the tatami room");
        check.accept(level.getBlockState(new BlockPos(30, 73, 9)).is(ModBlocks.CEILING_BOARD.get())
                || level.getBlockState(new BlockPos(30, 73, 9)).is(ModBlocks.CEILING_BOARD_STAINED.get()), "the ceiling boards at y 73");
        check.accept(!level.getBlockState(new BlockPos(28, 71, 10)).isAir() && !level.getBlockState(new BlockPos(22, 71, 5)).isAir(), "walls stand");
        check.accept(level.getBlockState(new BlockPos(20, 79, ApartmentBuilder.RIDGE_Z)).is(ModBlocks.ROOF_SLATE.get()), "the roof's ridge");
        check.accept(level.getBlockState(new BlockPos(20, 68, -2)).is(ModBlocks.CORRIDOR_CONCRETE.get()), "the outdoor corridor's slab at y 68");
        check.accept(level.getBlockState(new BlockPos(-4, 66, -2)).getBlock() instanceof StairBlock, "the steel stairs at the west end");
        check.accept(level.getBlockState(new BlockPos(20, 64, 21)).is(ModBlocks.ASPHALT_WET.get()), "the street is wet asphalt at y 64");
        BlockState lamp = level.getBlockState(Layout.marker("street_lamp").pos());
        check.accept(lamp.is(ModBlocks.STREET_LAMP_OLD.get()) && lamp.getValue(Props.LIT), "the street lamp in front of 203 is lit");
        int plates = 0;
        for (int x0 : com.hitori.world.Site.UNIT_X0) {
            if (level.getBlockState(new BlockPos(x0 + 3, 70, -1)).is(ModBlocks.NAMEPLATE.get())) plates++;
            if (level.getBlockState(new BlockPos(x0 + 3, 66, -1)).is(ModBlocks.NAMEPLATE.get())) plates++;
        }
        check.accept(plates == 8, "nameplates 101..204 by every door (" + plates + "/8)");
        int barriers = 0;
        for (int x = -12; x <= 50; x++) if (level.getBlockState(new BlockPos(x, 70, -6)).is(Blocks.BARRIER)) barriers++;
        check.accept(barriers > 40, "invisible walls behind the property (" + barriers + ")");
    }

    // ------------------------------------------------------------------------------------------------ routes
    /** Grid search at feet level from a to b through walkable cells (doors count as open; one-block steps allowed). */
    static int gridPath(ServerLevel level, BlockPos a, BlockPos b, int limit) {
        if (a.equals(b)) return 0;
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Map<BlockPos, Integer> dist = new HashMap<>();
        q.add(a);
        dist.put(a, 0);
        while (!q.isEmpty()) {
            BlockPos p = q.poll();
            int d = dist.get(p);
            if (d > limit) break;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos n = p.relative(dir).above(dy);
                    if (dist.containsKey(n)) continue;
                    if (n.equals(b)) return d + 1;
                    if (!walkable(level, n)) continue;
                    if (dy == 1 && !passable(level, p.above(2))) continue;
                    dist.put(n, d + 1);
                    q.add(n);
                }
            }
        }
        return -1;
    }

    static void routes(ServerLevel level, Consumer<String> log, BiConsumer<Boolean, String> check) {
        List<Layout.Marker> pts = Layout.markers("doll_route");
        int broken = 0;
        for (int i = 0; i < pts.size(); i++) {
            BlockPos a = pts.get(i).pos(), b = pts.get((i + 1) % pts.size()).pos();
            if (gridPath(level, a, b, 300) < 0) {
                broken++;
                log.accept("  doll_route: no way from " + a.toShortString() + " to " + b.toShortString());
            }
        }
        check.accept(broken == 0, "doll_route is a connected loop (" + pts.size() + " points)");
        int unreachable = 0;
        BlockPos spawn = Layout.marker("spawn").pos();
        for (String m : new String[]{"closet_hide", "western_closet", "balcony", "peephole"}) {
            for (Layout.Marker mk : Layout.markers(m)) {
                if (gridPath(level, spawn, mk.pos(), 400) < 0) {
                    unreachable++;
                    log.accept("  unreachable from the spawn: " + m + " " + mk.pos().toShortString());
                }
            }
        }
        check.accept(unreachable == 0, "the closets, the balcony and the corridor are reachable from the spawn");
    }

    // ------------------------------------------------------------------------------------------------ fixtures
    static boolean lit(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.hasProperty(Props.LIT) && s.getValue(Props.LIT);
    }

    static void fixtures(ServerLevel level, Consumer<String> log, BiConsumer<Boolean, String> check) {
        // lights and switches
        int ok = 0;
        for (String room : Layout.LIGHT_ROOMS) {
            Fixtures.setLights(level, room, true);
            boolean on = Fixtures.lightsIn(level, room).stream().allMatch(p -> lit(level, p)) && Fixtures.lightsOn(level, room);
            boolean sw = "tatami".equals(room) || level.getBlockState(Layout.marker("switch_" + room).pos()).getValue(Props.ON);
            Fixtures.setLights(level, room, false);
            boolean off = Fixtures.lightsIn(level, room).stream().noneMatch(p -> lit(level, p)) && !Fixtures.lightsOn(level, room);
            if (on && off && sw) ok++;
            else log.accept("  lights " + room + " on=" + on + " off=" + off + " switch=" + sw);
        }
        check.accept(ok == Layout.LIGHT_ROOMS.length, "every room's lights follow its switch (" + ok + "/" + Layout.LIGHT_ROOMS.length + ")");
        Fixtures.setLights(level, "tatami", true);
        Fixtures.setTv(level, true);
        Fixtures.setBreaker(level, false);
        boolean dark = !lit(level, Layout.marker("lights_tatami").pos()) && !Fixtures.tvOn(level) && !Fixtures.breakerOn(level)
                && !level.getBlockState(Layout.marker("breaker").pos()).getValue(Props.LIT);
        Fixtures.setBreaker(level, true);
        boolean back = lit(level, Layout.marker("lights_tatami").pos()) && Fixtures.tvOn(level);
        check.accept(dark && back, "the breaker cuts every light and the TV, and brings them back (dark=" + dark + " back=" + back + ")");
        Fixtures.setLights(level, "tatami", false);
        // the TV
        Fixtures.setTvChannel(level, 2);
        boolean ch = Fixtures.tvChannel(level) == 2;
        Fixtures.setTvChannel(level, 0);
        Fixtures.setTv(level, false);
        check.accept(ch && !Fixtures.tvOn(level), "the TV changes channel and turns off");
        // taps
        Fixtures.setTap(level, "kitchen", true);
        Fixtures.setTap(level, "washbasin", true);
        boolean taps = Fixtures.tapRunning(level, "kitchen") && Fixtures.tapRunning(level, "washbasin");
        Fixtures.setTap(level, "kitchen", false);
        Fixtures.setTap(level, "washbasin", false);
        check.accept(taps && !Fixtures.tapRunning(level, "kitchen"), "the kitchen tap and the washbasin run and stop");
        Fixtures.setBathWater(level, 2);
        boolean full = Fixtures.bathWater(level) == 2 && level.getBlockState(Layout.markers("bathtub").get(1).pos()).getValue(Props.WATER) == 2;
        Fixtures.setBathLid(level, true);
        boolean lid = Fixtures.bathLid(level);
        Fixtures.setBathLid(level, false);
        Fixtures.setBathWater(level, 0);
        check.accept(full && lid && Fixtures.bathWater(level) == 0, "both halves of the tub fill and empty; the lid goes on");
        // the closet
        BlockPos first = Layout.markers("closet_fusuma").get(0).pos(), second = Layout.markers("closet_fusuma").get(1).pos();
        boolean shutColl = !level.getBlockState(first).getCollisionShape(level, first).isEmpty();
        Fixtures.setClosetFusuma(level, 1);
        boolean crack = Fixtures.closetFusuma(level) == 1 && level.getBlockState(first.above()).getValue(Props.OPEN3) == 1;
        Fixtures.setClosetFusuma(level, 2);
        boolean opening = level.getBlockState(first).getCollisionShape(level, first).isEmpty()
                && !level.getBlockState(second).getCollisionShape(level, second).isEmpty();
        Fixtures.setClosetFusuma(level, 0);
        check.accept(shutColl && crack && opening && Fixtures.closetFusuma(level) == 0,
                "the 押し入れ's fusuma shut / crack / open (open: the north end is free, the panels stack) " + shutColl + crack + opening);
        // doors
        int doors = 0;
        for (String d : Layout.DOORS) {
            Fixtures.setDoor(level, d, true);
            boolean o = Fixtures.doorOpen(level, d);
            Fixtures.setDoor(level, d, false);
            if (o && !Fixtures.doorOpen(level, d)) doors++;
            else log.accept("  door " + d + " open=" + o);
        }
        check.accept(doors == Layout.DOORS.length, "every door opens and shuts (" + doors + "/" + Layout.DOORS.length + ")");
        // devices
        int dev = 0;
        String[] devices = {"intercom", "washing_machine", "fridge", "laptop", "desk_lamp", "gas_stove", "tv", "kitchen_tap", "washbasin"};
        for (String k : devices) {
            Fixtures.setDevice(level, k, true);
            boolean a = Fixtures.deviceOn(level, k);
            Fixtures.setDevice(level, k, false);
            boolean b = !Fixtures.deviceOn(level, k);
            if (a && b) dev++;
            else log.accept("  device " + k + " on=" + a + " off=" + b);
        }
        check.accept(dev == devices.length, "every device switches (" + dev + "/" + devices.length + ")");
        // the ritual's things
        int items = 0;
        for (String k : new String[]{"knife", "plush", "lighter", "laptop"}) {
            Fixtures.setPresent(level, k, false);
            boolean gone = !Fixtures.present(level, k);
            Fixtures.setPresent(level, k, true);
            if (gone && Fixtures.present(level, k)) items++;
        }
        boolean rack = level.getBlockState(Layout.marker("knife").pos()).getValue(Props.HAS_KNIFE);
        Fixtures.setPresent(level, "laptop", false);
        boolean laptopGone = level.getBlockState(Layout.marker("laptop").pos()).isAir();
        Fixtures.setPresent(level, "laptop", true);
        check.accept(items == 4 && rack && laptopGone && level.getBlockState(Layout.marker("laptop").pos()).is(ModBlocks.LAPTOP_BLOCK.get()),
                "the knife, the plush, the lighter and the laptop leave and come back");
        // curtains, wet marks, falling things, the lamp swinging, a flicker
        Fixtures.setCurtains(level, "tatami", false);
        boolean opened = !level.getBlockState(Layout.markers("curtains_tatami").get(0).pos()).getValue(Props.CLOSED);
        Fixtures.setCurtains(level, "tatami", true);
        check.accept(opened && level.getBlockState(Layout.markers("curtains_tatami").get(0).pos().above()).getValue(Props.CLOSED), "the curtains open and close");
        BlockPos wet = new BlockPos(27, 70, 5);
        Fixtures.wetMark(level, wet);
        check.accept(level.getBlockState(wet).is(ModBlocks.PUDDLE.get()), "the doll leaves a wet mark in the hall");
        Fixtures.dropSomething(level, new BlockPos(24, 71, 5));
        Fixtures.swingLamp(level, 120);
        Fixtures.setLights(level, "dk", false);
        Fixtures.flicker(level, "dk", 60);
        check.accept(Fixtures.lightsIn(level, "tatami").size() == 1 && Fixtures.lightsIn(level, "hall").size() == 2, "lightsIn lists the lamps");
    }

    // ------------------------------------------------------------------------------------------------ reset
    /** A sink that records what the builders would write. */
    static final class Recorder implements BlockSink {
        final Map<BlockPos, BlockState> states = new HashMap<>();
        final int x1, z1, x2, z2;

        Recorder(int x1, int z1, int x2, int z2) {
            this.x1 = x1;
            this.z1 = z1;
            this.x2 = x2;
            this.z2 = z2;
        }

        @Override
        public boolean contains(int x, int z) {
            return x >= x1 && x <= x2 && z >= z1 && z <= z2;
        }

        @Override
        public boolean touches(int ax, int az, int bx, int bz) {
            return Math.max(ax, bx) >= x1 && Math.min(ax, bx) <= x2 && Math.max(az, bz) >= z1 && Math.min(az, bz) <= z2;
        }

        @Override
        public void set(int x, int y, int z, BlockState state) {
            if (contains(x, z)) states.put(new BlockPos(x, y, z), state);
        }

        @Override
        public void post(int x, int y, int z, BlockState state) {
            if (contains(x, z)) states.put(new BlockPos(x, y, z), null);
        }

        @Override
        public BlockState get(int x, int y, int z) {
            BlockState s = states.get(new BlockPos(x, y, z));
            return s == null ? Blocks.AIR.defaultBlockState() : s;
        }
    }

    static void reset(ServerLevel level, Consumer<String> log, BiConsumer<Boolean, String> check) {
        long t0 = System.currentTimeMillis();
        Fixtures.resetAll(level);
        long ms = System.currentTimeMillis() - t0;
        Recorder r = new Recorder(-12, -8, 48, 18);
        ApartmentBuilder.build(r);
        int diff = 0;
        Set<String> examples = new HashSet<>();
        Set<BlockPos> dying = Set.of(com.hitori.world.WorldTicker.DYING);
        for (Map.Entry<BlockPos, BlockState> e : r.states.entrySet()) {
            if (e.getValue() == null || dying.contains(e.getKey())) continue;
            BlockState now = level.getBlockState(e.getKey());
            if (now != e.getValue()) {
                diff++;
                if (examples.size() < 8) examples.add(e.getKey().toShortString() + " " + now + " != " + e.getValue());
            }
        }
        for (String s : examples) log.accept("  differs: " + s);
        check.accept(diff == 0, "resetAll restores every block of the building (" + r.states.size() + " checked, " + diff + " differ, " + ms + " ms)");
        boolean start = Fixtures.breakerOn(level) && Fixtures.lightsOn(level, "western") && !Fixtures.lightsOn(level, "tatami") && !Fixtures.tvOn(level)
                && Fixtures.bathWater(level) == 0 && Fixtures.closetFusuma(level) == 0 && Fixtures.present(level, "plush") && Fixtures.present(level, "knife");
        check.accept(start, "resetAll restores the evening's start (breaker on, only the western room lit, TV off, bath empty, closet shut, plush and knife in place)");
    }

    /** Light levels at a few places (for tuning: dark but readable). */
    static void light(ServerLevel level, Consumer<String> log) {
        Object[][] spots = {{"western (lit)", new BlockPos(30, 71, 9)}, {"desk", new BlockPos(31, 71, 9)}, {"tatami", new BlockPos(26, 71, 11)},
                {"closet", new BlockPos(23, 71, 11)}, {"dk", new BlockPos(24, 71, 6)}, {"hall", new BlockPos(27, 71, 4)}, {"bath", new BlockPos(31, 71, 2)},
                {"genkan", new BlockPos(24, 70, 1)}, {"corridor", new BlockPos(24, 70, -2)}, {"balcony", new BlockPos(25, 71, 14)},
                {"under the lamp", new BlockPos(26, 66, 20)}, {"street", new BlockPos(10, 66, 21)}};
        StringBuilder sb = new StringBuilder("light levels (block/sky):");
        for (Object[] s : spots) {
            BlockPos p = (BlockPos) s[1];
            sb.append(" ").append(s[0]).append("=").append(level.getBrightness(LightLayer.BLOCK, p)).append("/").append(level.getBrightness(LightLayer.SKY, p));
        }
        log.accept(sb.toString());
    }

    /** Writes every marker and room to a text file (run/hitori-layout.txt in CI). */
    public static void dumpLayout(java.nio.file.Path file) {
        List<String> lines = new ArrayList<>();
        for (Layout.Room r : Layout.rooms()) lines.add("room " + r.key() + " " + r.nameJa() + " " + r.box() + " " + r.acoustics());
        for (var e : Layout.all().entrySet()) {
            StringBuilder sb = new StringBuilder(e.getKey()).append(":");
            for (Layout.Marker m : e.getValue()) sb.append(" ").append(m.pos().toShortString()).append("/").append(m.facing().getName());
            lines.add(sb.toString());
        }
        try {
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    // ================================================================================================ standalone runner
    public static boolean standalone() {
        if (!Boolean.getBoolean("hitori.selftest")) return false;
        try {
            Class.forName("com.hitori.debug.SelfTest", false, WorldCheck.class.getClassLoader());
            return false;
        } catch (ClassNotFoundException e) {
            return true;
        }
    }

    @Mod.EventBusSubscriber(modid = Hitori.MODID)
    public static final class Runner {
        private static final List<String> LINES = new ArrayList<>();
        private static int failures;
        private static int stage = -1;
        private static int wait;
        private static long ticks;

        private Runner() {}

        @SubscribeEvent
        public static void started(ServerStartedEvent event) {
            if (!standalone()) return;
            stage = 0;
            wait = 40;
            log("world check starting");
            dumpLayout(java.nio.file.Path.of("hitori-layout.txt"));
        }

        static void log(String s) {
            LINES.add(s);
            Hitori.LOGGER.info("[hitori-selftest] {}", s);
            try {
                Files.writeString(java.nio.file.Path.of("hitori-selftest.txt"), String.join("\n", LINES) + "\n", StandardCharsets.UTF_8);
            } catch (IOException ignored) {
            }
        }

        static void check(boolean ok, String what) {
            if (!ok) failures++;
            log((ok ? "PASS " : "FAIL ") + what);
        }

        @SubscribeEvent
        public static void tick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || stage < 0 || stage == 99) return;
            MinecraftServer server = event.getServer();
            if (++ticks > 20 * 60 * 8) {
                check(false, "global timeout at stage " + stage);
                finish(server);
                return;
            }
            if (wait > 0) {
                wait--;
                return;
            }
            ServerLevel level = server.getLevel(Layout.DIMENSION);
            if (level == null) {
                check(false, "the dimension hitori:shirakaba exists");
                finish(server);
                return;
            }
            try {
                int next = run(level, stage, Runner::log, Runner::check);
                if (next < 0) {
                    finish(server);
                    return;
                }
                stage++;
                wait = next;
            } catch (Throwable e) {
                check(false, "exception at stage " + stage + ": " + e);
                Hitori.LOGGER.error("[hitori-selftest] exception", e);
                finish(server);
            }
        }

        private static void finish(MinecraftServer server) {
            stage = 99;
            log("SELFTEST RESULT: " + (failures == 0 ? "PASS" : "FAIL (" + failures + ")"));
            server.halt(false);
        }
    }
}
