package com.reisatsu.debug;

import com.reisatsu.Reisatsu;
import com.reisatsu.block.AutoDoorBlock;
import com.reisatsu.block.Props;
import com.reisatsu.block.RestaurantDoorBlock;
import com.reisatsu.block.SwingDoorBlock;
import com.reisatsu.block.entity.AutoDoorBlockEntity;
import com.reisatsu.block.entity.SwingDoorBlockEntity;
import com.reisatsu.registry.ModBlocks;
import com.reisatsu.world.BlockSink;
import com.reisatsu.world.Circuit;
import com.reisatsu.world.ExteriorBuilder;
import com.reisatsu.world.Fixtures;
import com.reisatsu.world.Layout;
import com.reisatsu.world.RestaurantBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
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

/**
 * Checks of the restaurant (owner: world): every marker exists, standing markers are walkable and in their room,
 * the routes are connected for a 0.6 x 1.95 mob (grid search and a real zombie's path), doors open and close, taps
 * toggle, circuits switch the lights, devices and tables work, block entities exist, and {@link Fixtures#resetAll}
 * puts every block back. The game's self test can call {@link #run} (stage by stage, it needs ticks in between);
 * with {@code -Dreisatsu.selftest=true} and no {@code com.reisatsu.debug.SelfTest}, {@link Runner} runs it alone and
 * writes {@code run/reisatsu-selftest.txt} ("SELFTEST RESULT: PASS/FAIL") and {@code run/reisatsu-layout.txt}.
 */
public final class WorldCheck {
    private WorldCheck() {}

    /** Markers that are where something stands or walks (feet position: passable, 2 high, solid floor). */
    static final String[] STANDING = {"spawn", "medium", "medium_car", "wake", "visitor_spawn", "visitor_path", "waitress_route", "waitress_station",
            "waitress_bow", "child_count", "chef_board", "chef_stove", "chef_pass", "mirror_woman", "manager_route", "breaker_stand",
            "exorcism_medium", "exorcism_spirits", "exorcism_player", "film_3"};
    /** Expected room of markers (prefix match). */
    static final Object[][] ROOMS = {{"chef_", "kitchen"}, {"sink_w", "womens"}, {"sink_m", "mens"}, {"sink_kitchen", "kitchen"},
            {"mirror_woman", "cavity"}, {"manager_desk", "office"}, {"lockers", "staff"}, {"tv", "staff"}, {"monitors", "office"},
            {"key_office", "staff"}, {"drink_bar", "dining"}, {"fryer", "kitchen"}, {"gas_range", "kitchen"}, {"dishwasher", "kitchen"},
            {"stuffed_bear", "waiting"}, {"child_count", "waiting"}, {"waitress_station", "dining"}, {"waitress_route", "dining"},
            {"seat_7", "dining"}, {"film_3", "fridge"}, {"stall_occupied", "womens"}, {"hand_dryer_w", "womens"}, {"hand_dryer_m", "mens"},
            {"exorcism_", "dining"}, {"table_", "dining"}, {"button_", "dining"}, {"register", "waiting"}, {"phone", "waiting"},
            {"call_display", "waiting"}, {"note_handover", "staff"}, {"note_clipping", "staff"}, {"note_diary", "office"},
            {"note_drawing", "waiting"}, {"note_flyer", "vestibule"}, {"note_fire", "kitchen"}, {"note_cleaning", "womens"},
            {"note_reservation", "waiting"}};
    static final String[] ROUTES = {"visitor_path", "waitress_route", "manager_route"};

    // ------------------------------------------------------------------------------------------------ stages
    /**
     * Runs stage {@code stage} of the checks and returns the number of ticks to wait before the next stage, or -1
     * when done. log receives lines; check(ok, what) records a result.
     */
    public static int run(ServerLevel level, int stage, Consumer<String> log, BiConsumer<Boolean, String> check) {
        switch (stage) {
            case 0 -> {
                long t0 = System.currentTimeMillis();
                for (int cx = -2; cx <= 5; cx++) for (int cz = -2; cz <= 4; cz++) level.setChunkForced(cx, cz, true);
                Fixtures.ensureBuilt(level);
                log.accept("restaurant chunks generated and forced in " + (System.currentTimeMillis() - t0) + " ms");
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
                // the sensor: a mob in front of the outer doors (doors enabled)
                Fixtures.resetAll(level);
                Fixtures.setAutoDoorsEnabled(level, true);
                check.accept(Fixtures.autoDoorsEnabled(level), "the key switch enables the automatic doors");
                sensorMob = spawn(level, new BlockPos(4, 65, 32));
                swingMob = spawn(level, new BlockPos(29, 65, 10));
                // a mob that never moves never touches the blocks it stands in: nudge it once
                if (swingMob != null) swingMob.move(net.minecraft.world.entity.MoverType.SELF, new net.minecraft.world.phys.Vec3(0, -0.01, 0));
                return 12;
            }
            case 5 -> {
                check.accept(Fixtures.isAutoDoorOpen(level, "outer"), "the outer doors open for a mob in front of them");
                BlockState sw = level.getBlockState(new BlockPos(29, 65, 10));
                check.accept(sw.getBlock() instanceof SwingDoorBlock && sw.getValue(Props.SWING) != 0, "a swing door swings open when a mob walks into it");
                if (sensorMob != null) sensorMob.discard();
                if (swingMob != null) swingMob.discard();
                return 70;
            }
            case 6 -> {
                check.accept(!Fixtures.isAutoDoorOpen(level, "outer"), "the outer doors close again once the sensor is clear");
                BlockState sw = level.getBlockState(new BlockPos(29, 65, 10));
                check.accept(sw.getBlock() instanceof SwingDoorBlock && sw.getValue(Props.SWING) == 0, "the swing door flaps shut");
                Fixtures.setAutoDoorsEnabled(level, false);
                Fixtures.openAutoDoor(level, "inner", 20);
                check.accept(Fixtures.isAutoDoorOpen(level, "inner"), "openAutoDoor opens the inner doors even while switched off");
                Fixtures.setAutoDoorsLocked(level, true);
                check.accept(!Fixtures.isAutoDoorOpen(level, "inner"), "locking shuts the doors at once");
                Fixtures.openAutoDoor(level, "outer", 20);
                check.accept(!Fixtures.isAutoDoorOpen(level, "outer"), "locked doors stay shut when the game opens them");
                Fixtures.setAutoDoorsLocked(level, false);
                return 5;
            }
            case 7 -> {
                reset(level, log, check);
                return 5;
            }
            case 8 -> {
                light(level, log);
                return -1;
            }
            default -> {
                return -1;
            }
        }
    }

    private static Mob sensorMob, swingMob;

    static Mob spawn(ServerLevel level, BlockPos p) {
        Zombie z = EntityType.ZOMBIE.create(level);
        if (z == null) return null;
        z.setNoAi(true);
        z.setSilent(true);
        z.setPersistenceRequired();
        z.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
        level.addFreshEntity(z);
        return z;
    }

    // ------------------------------------------------------------------------------------------------ markers
    static boolean passable(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.getBlock() instanceof DoorBlock || s.getBlock() instanceof AutoDoorBlock || s.getBlock() instanceof SwingDoorBlock) return true;
        return s.getCollisionShape(level, p).isEmpty();
    }

    static boolean floor(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p.below());
        return !s.getCollisionShape(level, p.below()).isEmpty();
    }

    static boolean walkable(ServerLevel level, BlockPos p) {
        return passable(level, p) && passable(level, p.above()) && floor(level, p);
    }

    static void markers(ServerLevel level, Consumer<String> log, BiConsumer<Boolean, String> check) {
        String[] required = {"spawn", "medium", "medium_car", "wake", "door_outer", "door_inner", "door_switch", "seat_7", "call_display", "register",
                "phone", "visitor_path", "visitor_spawn", "waitress_route", "waitress_station", "waitress_bow", "child_count", "child_hides",
                "chef_board", "chef_stove", "chef_pass", "pass_bell", "mirror_woman", "mirror", "sink_w", "sink_m", "sink_kitchen", "manager_desk",
                "manager_route", "breaker", "cameras", "monitors", "lockers", "stall_occupied", "tv", "hand_dryer_w", "hand_dryer_m", "drink_bar",
                "fryer", "gas_range", "dishwasher", "fridge_door", "stuffed_bear", "menu_board", "curve_mirror", "flowers", "film_1", "film_2",
                "film_3", "key_office", "exorcism_medium", "exorcism_spirits", "exorcism_player"};
        int missing = 0;
        for (String r : required) if (!Layout.has(r)) {
            missing++;
            log.accept("  missing marker " + r);
        }
        for (int n = 1; n <= 17; n++) {
            if (!Layout.has("table_" + n)) missing++;
            if (!Layout.has("button_" + n)) missing++;
        }
        for (String id : new String[]{"report", "handover", "clipping", "reservation", "diary", "drawing", "flyer", "fire", "cleaning", "medium"}) {
            if (!Layout.has("note_" + id)) missing++;
        }
        check.accept(missing == 0, "every marker of DESIGN §2 exists (" + Layout.all().size() + " names)");
        check.accept(Layout.markers("waitress_route").size() >= 10, "waitress_route has at least 10 points");
        check.accept(Layout.markers("child_hides").size() >= 6, "child_hides has at least 6 spots");

        int bad = 0;
        for (String name : STANDING) {
            for (Layout.Marker m : Layout.markers(name)) {
                if (name.equals("medium_car")) continue;
                if (m.pos().equals(Layout.marker("seat_7").pos())) continue;   // the visitor's seat on the bench
                if (!walkable(level, m.pos())) {
                    bad++;
                    log.accept("  not walkable: " + name + " " + m.pos().toShortString() + " feet=" + level.getBlockState(m.pos()).getBlock()
                            + " head=" + level.getBlockState(m.pos().above()).getBlock() + " floor=" + level.getBlockState(m.pos().below()).getBlock());
                }
            }
        }
        check.accept(bad == 0, "standing markers are walkable (" + bad + " bad)");
        int hideBad = 0;
        for (Layout.Marker m : Layout.markers("child_hides")) {
            // the child crouches under the table of booth 3: that one is a table cell, the others are walkable
            boolean ok = walkable(level, m.pos()) || level.getBlockState(m.pos()).is(ModBlocks.RESTAURANT_TABLE.get());
            if (!ok) {
                hideBad++;
                log.accept("  bad hide " + m.pos().toShortString());
            }
        }
        check.accept(hideBad == 0, "child hiding spots are reachable cells");
        int wrongRoom = 0;
        for (var e : Layout.all().entrySet()) {
            for (Object[] r : ROOMS) {
                if (!e.getKey().startsWith((String) r[0])) continue;
                for (Layout.Marker m : e.getValue()) {
                    BlockPos p = m.pos().getY() > 68 ? m.pos().below(m.pos().getY() - 68) : m.pos();
                    Layout.Room room = Layout.roomAt(p);
                    if (room == null || !room.key().equals(r[1])) {
                        wrongRoom++;
                        log.accept("  " + e.getKey() + " " + m.pos().toShortString() + " is in " + (room == null ? "no room" : room.key()) + ", expected " + r[1]);
                    }
                }
            }
        }
        check.accept(wrongRoom == 0, "markers are in their rooms (" + wrongRoom + " wrong)");
        // the things the markers point at
        expect(level, check, "seat_7", ModBlocks.BOOTH_SEAT.get());
        expect(level, check, "register", ModBlocks.REGISTER.get());
        expect(level, check, "phone", ModBlocks.BUSINESS_PHONE.get());
        expect(level, check, "call_display", ModBlocks.CALL_DISPLAY.get());
        expect(level, check, "door_switch", ModBlocks.DOOR_SWITCH.get());
        expect(level, check, "door_outer", ModBlocks.AUTO_DOOR.get());
        expect(level, check, "door_inner", ModBlocks.AUTO_DOOR.get());
        expect(level, check, "breaker", ModBlocks.BREAKER_PANEL.get());
        expect(level, check, "pass_bell", ModBlocks.PASS_BELL.get());
        expect(level, check, "tv", ModBlocks.CRT_TV.get());
        expect(level, check, "hand_dryer_w", ModBlocks.HAND_DRYER.get());
        expect(level, check, "hand_dryer_m", ModBlocks.HAND_DRYER.get());
        expect(level, check, "stuffed_bear", ModBlocks.STUFFED_BEAR.get());
        expect(level, check, "menu_board", ModBlocks.MENU_BOARD.get());
        expect(level, check, "curve_mirror", ModBlocks.CURVE_MIRROR.get());
        expect(level, check, "flowers", ModBlocks.FLOWER_OFFERING.get());
        expect(level, check, "fridge_door", ModBlocks.FRIDGE_DOOR.get());
        expect(level, check, "stall_occupied", ModBlocks.STALL_DOOR.get());
        expectAll(level, check, "mirror", ModBlocks.MIRROR.get());
        expectAll(level, check, "sink_w", ModBlocks.RESTROOM_SINK.get());
        expectAll(level, check, "sink_m", ModBlocks.RESTROOM_SINK.get());
        expectAll(level, check, "sink_kitchen", ModBlocks.KITCHEN_SINK.get());
        expectAll(level, check, "cameras", ModBlocks.SECURITY_CAMERA.get());
        expectAll(level, check, "monitors", ModBlocks.SECURITY_MONITORS.get());
        expectAll(level, check, "lockers", ModBlocks.LOCKER.get());
        expectAll(level, check, "drink_bar", ModBlocks.DRINK_BAR.get());
        expectAll(level, check, "fryer", ModBlocks.FRYER.get());
        expectAll(level, check, "gas_range", ModBlocks.GAS_RANGE.get());
        int buttons = 0, tables = 0;
        for (int n = 1; n <= 17; n++) {
            if (level.getBlockState(Layout.marker("button_" + n).pos()).is(ModBlocks.CALL_BUTTON.get())) buttons++;
            if (level.getBlockState(Layout.marker("table_" + n).pos()).is(ModBlocks.RESTAURANT_TABLE.get())) tables++;
            if (Layout.tableAt(Layout.marker("button_" + n).pos()) != n) log.accept("  tableAt(button_" + n + ") wrong");
        }
        check.accept(buttons == 17 && tables == 17, "17 tables with their call buttons (" + tables + " tables, " + buttons + " buttons)");
        int notes = 0, noteBlocks = 0;
        for (String id : new String[]{"handover", "clipping", "reservation", "diary", "drawing", "flyer", "fire", "cleaning"}) {
            noteBlocks++;
            BlockPos p = Layout.marker("note_" + id).pos();
            BlockState s = level.getBlockState(p);
            boolean holder = s.is(ModBlocks.NOTE_PAPER.get()) || s.is(ModBlocks.RESERVATION_BOOK.get()) || s.is(ModBlocks.NOTICE_BOARD.get())
                    || s.is(ModBlocks.BULLETIN_BOARD.get());
            if (holder && id.equals(Fixtures.noteAt(p))) notes++;
            else log.accept("  note " + id + " at " + p.toShortString() + ": " + s.getBlock());
        }
        check.accept(notes == noteBlocks, "every found note has its paper / book / board (" + notes + "/" + noteBlocks + ")");
    }

    static void expect(ServerLevel level, BiConsumer<Boolean, String> check, String marker, net.minecraft.world.level.block.Block b) {
        BlockPos p = Layout.marker(marker).pos();
        BlockState s = level.getBlockState(p);
        check.accept(s.is(b), marker + " is a " + b.getDescriptionId().replace("block.reisatsu.", "") + " at " + p.toShortString()
                + (s.is(b) ? "" : " (found " + s.getBlock().getDescriptionId() + ")"));
    }

    static void expectAll(ServerLevel level, BiConsumer<Boolean, String> check, String marker, net.minecraft.world.level.block.Block b) {
        int ok = 0, n = 0;
        for (Layout.Marker m : Layout.markers(marker)) {
            n++;
            if (level.getBlockState(m.pos()).is(b)) ok++;
        }
        check.accept(ok == n, marker + ": " + ok + "/" + n + " are " + b.getDescriptionId().replace("block.reisatsu.", ""));
    }

    static void structure(ServerLevel level, Consumer<String> log, BiConsumer<Boolean, String> check) {
        int bes = 0;
        for (BlockPos p : new BlockPos[]{new BlockPos(3, 65, 30), new BlockPos(4, 65, 30), new BlockPos(3, 65, 26), new BlockPos(4, 65, 26)}) {
            if (level.getBlockEntity(p) instanceof AutoDoorBlockEntity) bes++;
        }
        for (BlockPos p : new BlockPos[]{new BlockPos(29, 65, 10), new BlockPos(30, 65, 10)}) {
            if (level.getBlockEntity(p) instanceof SwingDoorBlockEntity) bes++;
        }
        check.accept(bes == 6, "the door block entities exist (" + bes + "/6)");
        // the shell: walls, windows, ceiling
        check.accept(level.getBlockState(new BlockPos(20, 67, 30)).is(ModBlocks.SHOP_WINDOW.get()), "shop windows on the south front");
        check.accept(level.getBlockState(new BlockPos(40, 67, 20)).is(ModBlocks.SHOP_WINDOW.get()), "shop windows on the east front");
        check.accept(level.getBlockState(new BlockPos(20, 69, 20)).is(ModBlocks.CEILING_TILE.get())
                || level.getBlockState(new BlockPos(20, 69, 20)).is(ModBlocks.CEILING_TILE_STAINED.get()), "suspended ceiling at y 69");
        check.accept(level.getBlockState(new BlockPos(20, 64, 20)).is(ModBlocks.DINING_FLOOR.get()), "dining floor at y 64");
        check.accept(level.getBlockState(new BlockPos(20, 64, 40)).is(ModBlocks.ASPHALT.get())
                || level.getBlockState(new BlockPos(20, 64, 40)).is(ModBlocks.PARKING_LINE.get()), "the lot is asphalt at y 64");
        check.accept(level.getBlockState(new BlockPos(10, 64, 54)).is(ModBlocks.ROAD_LINE.get()), "the road's centre line at z 54");
        BlockState sign = level.getBlockState(new BlockPos(ExteriorBuilder.SIGN_X0, ExteriorBuilder.SIGN_Y0 + 3, ExteriorBuilder.SIGN_Z_FRONT));
        check.accept(sign.is(ModBlocks.POLE_SIGN.get()) && sign.getValue(Props.TILE16) == 0, "the pole sign's top-left tile");
        int barriers = 0;
        for (int x = -12; x <= 50; x++) if (level.getBlockState(new BlockPos(x, 67, -12)).is(Blocks.BARRIER)) barriers++;
        check.accept(barriers > 40, "barriers along the back of the property (" + barriers + ")");
    }

    // ------------------------------------------------------------------------------------------------ routes
    /** Grid search at feet level from a to b through passable cells (doors count as open). */
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
                BlockPos n = p.relative(dir);
                if (dist.containsKey(n)) continue;
                if (!n.equals(b) && !walkable(level, n)) continue;
                if (n.equals(b)) return d + 1;
                dist.put(n, d + 1);
                q.add(n);
            }
        }
        return -1;
    }

    static void routes(ServerLevel level, Consumer<String> log, BiConsumer<Boolean, String> check) {
        Fixtures.setAutoDoorsEnabled(level, true);
        for (String route : ROUTES) {
            List<Layout.Marker> pts = Layout.markers(route);
            int broken = 0;
            for (int i = 0; i + 1 < pts.size(); i++) {
                int d = gridPath(level, pts.get(i).pos(), pts.get(i + 1).pos(), 400);
                if (d < 0) {
                    broken++;
                    log.accept("  " + route + ": no way from " + pts.get(i).pos().toShortString() + " to " + pts.get(i + 1).pos().toShortString());
                }
            }
            check.accept(broken == 0, route + " is connected (" + pts.size() + " points, grid search)");
        }
        // a real mob's pathfinding (0.6 wide), doors on the way passable
        Zombie z = EntityType.ZOMBIE.create(level);
        if (z == null) return;
        z.setNoAi(false);
        z.setPersistenceRequired();
        if (z.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation g) g.setCanOpenDoors(true);
        int ok = 0, all = 0;
        for (String route : ROUTES) {
            List<Layout.Marker> pts = Layout.markers(route);
            for (int i = 0; i + 1 < pts.size(); i++) {
                BlockPos a = pts.get(i).pos(), b = pts.get(i + 1).pos();
                if (route.equals("visitor_path") && b.equals(Layout.marker("seat_7").pos())) continue;
                all++;
                z.moveTo(a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 0, 0);
                z.setOnGround(true);
                Path path = z.getNavigation().createPath(b, 0);
                if (path != null && path.canReach()) ok++;
                else log.accept("  zombie path " + route + " " + a.toShortString() + " -> " + b.toShortString() + " "
                        + (path == null ? "null" : "ends at " + path.getTarget().toShortString()));
            }
        }
        z.discard();
        check.accept(ok == all, "a zombie finds a path for every route segment (" + ok + "/" + all + ")");
        Fixtures.setAutoDoorsEnabled(level, false);
    }

    // ------------------------------------------------------------------------------------------------ fixtures
    static void fixtures(ServerLevel level, Consumer<String> log, BiConsumer<Boolean, String> check) {
        BlockPos tap = Layout.marker("sink_w").pos();
        Fixtures.setTap(level, tap, true);
        boolean on = Fixtures.isTapRunning(level, tap);
        Fixtures.setTap(level, tap, false);
        check.accept(on && !Fixtures.isTapRunning(level, tap), "the women's tap turns on and off");
        BlockPos kt = Layout.marker("sink_kitchen").pos();
        Fixtures.setTap(level, kt, true);
        check.accept(Fixtures.isTapRunning(level, kt), "the kitchen tap runs");
        Fixtures.setTap(level, kt, false);
        // circuits
        for (Circuit c : new Circuit[]{Circuit.DINING, Circuit.KITCHEN, Circuit.RESTROOM, Circuit.BACK, Circuit.SIGN}) {
            String room = switch (c) {
                case DINING -> "dining";
                case KITCHEN -> "kitchen";
                case RESTROOM -> "womens";
                case BACK -> "staff";
                default -> null;
            };
            BlockPos light = room != null ? Fixtures.lightsIn(level, room).get(0)
                    : ExteriorBuilder.signTiles().get(0);
            Fixtures.setCircuit(level, c, true);
            boolean lit = level.getBlockState(light).getValue(Props.LIT);
            Fixtures.setCircuit(level, c, false);
            boolean dark = !level.getBlockState(light).getValue(Props.LIT);
            Fixtures.setCircuit(level, c, c.startOn());
            check.accept(lit && dark && Fixtures.circuitOn(level, c) == c.startOn(), "circuit " + c.key() + " switches its lights (" + light.toShortString() + ")");
        }
        Fixtures.setCircuit(level, Circuit.CAMERAS, false);
        boolean camOff = !level.getBlockState(Layout.marker("cameras").pos()).getValue(Props.ON)
                && !level.getBlockState(Layout.marker("monitors").pos()).getValue(Props.ON);
        Fixtures.setCircuit(level, Circuit.CAMERAS, true);
        check.accept(camOff && level.getBlockState(Layout.marker("cameras").pos()).getValue(Props.ON), "circuit cameras switches cameras and monitors");
        Fixtures.setCircuit(level, Circuit.DOOR, false);
        Fixtures.setAutoDoorsEnabled(level, true);
        boolean noPower = !Fixtures.autoDoorsEnabled(level);
        Fixtures.setCircuit(level, Circuit.DOOR, true);
        check.accept(noPower && Fixtures.autoDoorsEnabled(level), "circuit door powers the automatic doors");
        Fixtures.setAutoDoorsEnabled(level, false);
        // lights: flicker, burst, all on
        BlockPos dl = Fixtures.lightsIn(level, "dining").get(0);
        Fixtures.burstLight(level, dl);
        Fixtures.allLightsOn(level);
        check.accept(level.getBlockState(dl).getValue(Props.LIT) && Fixtures.circuitOn(level, Circuit.DINING), "allLightsOn lights every light, burst ones too");
        Fixtures.burstLight(level, dl);
        check.accept(!level.getBlockState(dl).getValue(Props.LIT), "a burst tube stays dark");
        // devices
        int dev = 0, devAll = 0;
        for (String k : new String[]{"tv", "drink_bar", "hand_dryer_w", "hand_dryer_m", "dishwasher", "fryer", "gas_range", "pass_lamp", "phone",
                "monitors", "store_music", "register_drawer"}) {
            devAll++;
            Fixtures.setDevice(level, k, true);
            boolean a = Fixtures.deviceOn(level, k);
            Fixtures.setDevice(level, k, false);
            boolean b = !Fixtures.deviceOn(level, k);
            if (a && b) dev++;
            else log.accept("  device " + k + " on=" + a + " off=" + b);
        }
        check.accept(dev == devAll, "every device switches (" + dev + "/" + devAll + ")");
        // tables
        BlockPos slot = Fixtures.menuSlot(7);
        boolean stand = level.getBlockState(slot).is(ModBlocks.MENU_STAND.get());
        Fixtures.scatterMenus(level, 7);
        int scattered = 0;
        BlockPos t7 = Layout.marker("table_7").pos();
        for (BlockPos p : BlockPos.betweenClosed(t7.offset(-3, 0, -3), t7.offset(3, 0, 3))) if (level.getBlockState(p).is(ModBlocks.MENU_SCATTERED.get())) scattered++;
        Fixtures.placeWaterGlass(level, 7);
        Fixtures.placeWaterGlass(level, 7);
        BlockState glass = level.getBlockState(slot);
        Fixtures.ringCallButton(level, 7);
        boolean ringing = level.getBlockState(Layout.marker("button_7").pos()).getValue(Props.LIT)
                && level.getBlockState(Layout.marker("call_display").pos()).getValue(Props.NUMBER) == 7;
        Fixtures.clearTable(level, 7);
        boolean cleared = level.getBlockState(slot).is(ModBlocks.MENU_STAND.get()) && !level.getBlockState(Layout.marker("button_7").pos()).getValue(Props.LIT);
        check.accept(stand && scattered > 0, "the menus of table 7 scatter (" + scattered + " on the floor)");
        check.accept(glass.is(ModBlocks.WATER_GLASS.get()) && glass.getValue(Props.GLASSES) == 2, "two glasses of water on table 7");
        check.accept(ringing, "call button 7 rings and the call display shows 7");
        check.accept(cleared, "clearTable puts table 7 back");
        Fixtures.pullOutChair(level, 7, true);
        boolean out7 = level.getBlockState(new BlockPos(34, 65, 26)).is(ModBlocks.RESTAURANT_CHAIR.get());
        Fixtures.pullOutChair(level, 7, false);
        Fixtures.pullOutChair(level, 12, true);
        boolean out12 = level.getBlockState(new BlockPos(Layout.centreX(12), 65, Layout.centreZ(12) - 2)).is(ModBlocks.RESTAURANT_CHAIR.get());
        Fixtures.pullOutChair(level, 12, false);
        check.accept(out7 && out12, "chairs are pulled out at table 7 and table 12");
        List<BlockPos> chairs = Fixtures.chairsNear(level, Layout.marker("table_15").pos(), 3);
        check.accept(chairs.size() >= 4, "chairsNear finds the chairs of table 15 (" + chairs.size() + ")");
        if (!chairs.isEmpty()) {
            Fixtures.knockChair(level, chairs.get(0), true);
            check.accept(level.getBlockState(chairs.get(0)).getValue(Props.FALLEN), "a chair falls over");
        }
        // misc
        Fixtures.crackMirror(level, true);
        check.accept(level.getBlockState(Layout.marker("mirror").pos()).getValue(Props.CRACKED), "the mirror cracks");
        Fixtures.setMenuBoard(level, 1);
        check.accept(level.getBlockState(Layout.marker("menu_board").pos()).getValue(Props.MESSAGE) == 1, "the menu board asks 「まだ　いますか」");
        Fixtures.turnBear(level, Direction.WEST);
        check.accept(level.getBlockState(Layout.marker("stuffed_bear").pos()).getValue(com.reisatsu.block.FacingBlock.FACING) == Direction.WEST, "the bear turns");
        Fixtures.setStallOccupied(level, false);
        boolean free = !level.getBlockState(Layout.marker("stall_occupied").pos()).getValue(Props.OCCUPIED);
        Fixtures.setStallOccupied(level, true);
        check.accept(free && level.getBlockState(Layout.marker("stall_occupied").pos()).getValue(Props.OCCUPIED), "the third stall can be freed and occupied");
        Fixtures.setCameraSeen(level, Layout.marker("cameras").pos(), true);
        check.accept(level.getBlockState(Layout.marker("cameras").pos()).getValue(Props.SEEN), "a camera's LED turns red");
        Fixtures.setMonitorsAlert(level, true);
        check.accept(level.getBlockState(Layout.marker("monitors").pos()).getValue(Props.ALERT), "the monitors show REC");
        Fixtures.setCallDisplay(level, 0, true);
        check.accept(level.getBlockState(Layout.marker("call_display").pos()).getValue(Props.CLOSED), "the call display shows 閉店");
        BlockPos plate = new BlockPos(24, 65, 7);
        Fixtures.smashPlate(level, plate);
        check.accept(level.getBlockState(plate).is(ModBlocks.BROKEN_PLATE.get()), "a plate smashes in the kitchen");
        Fixtures.wetFootprint(level, new net.minecraft.world.phys.Vec3(20.5, 65, 26.5), 0);
        check.accept(level.getBlockState(new BlockPos(20, 65, 26)).is(ModBlocks.PUDDLE.get()), "a wet footprint appears");
        RestaurantDoorBlock.setBoth(level, new BlockPos(3, 65, 7), true, false);
        boolean staffOpen = level.getBlockState(new BlockPos(3, 65, 7)).getValue(DoorBlock.OPEN);
        RestaurantDoorBlock.setBoth(level, new BlockPos(3, 65, 7), false, false);
        check.accept(staffOpen && !level.getBlockState(new BlockPos(3, 65, 7)).getValue(DoorBlock.OPEN), "the staff door opens and closes");
        check.accept(level.getBlockState(new BlockPos(11, 65, 7)).getValue(Props.LOCKED), "the office door is locked");
        check.accept(level.getBlockState(new BlockPos(0, 65, 8)).getValue(Props.LOCKED), "the back door is locked");
        List<BlockPos> sp = Fixtures.speakers(level);
        check.accept(sp.size() >= 4, "store speakers (" + sp.size() + ")");
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
        Recorder r = new Recorder(-1, -1, 41, 33);
        RestaurantBuilder.build(r);
        int diff = 0;
        Set<String> examples = new HashSet<>();
        for (Map.Entry<BlockPos, BlockState> e : r.states.entrySet()) {
            if (e.getValue() == null) continue;
            BlockState now = level.getBlockState(e.getKey());
            if (now != e.getValue()) {
                diff++;
                if (examples.size() < 8) examples.add(e.getKey().toShortString() + " " + now + " != " + e.getValue());
            }
        }
        for (String s : examples) log.accept("  differs: " + s);
        check.accept(diff == 0, "resetAll restores every block of the restaurant (" + r.states.size() + " checked, " + diff + " differ, " + ms + " ms)");
        boolean circuits = true;
        for (Circuit c : Circuit.values()) circuits &= Fixtures.circuitOn(level, c) == c.startOn();
        check.accept(circuits && !Fixtures.autoDoorsEnabled(level) && !Fixtures.isAutoDoorOpen(level, "outer"), "resetAll restores circuits, the key switch and the doors");
        check.accept(level.getBlockState(Layout.marker("stall_occupied").pos()).getValue(Props.OCCUPIED), "the third stall is occupied at the start");
    }

    /** Light levels at a few places (for tuning: the closed restaurant must be dark but readable). */
    static void light(ServerLevel level, Consumer<String> log) {
        Object[][] spots = {{"register", new BlockPos(11, 66, 25)}, {"waiting bench", new BlockPos(6, 66, 24)}, {"vestibule", new BlockPos(4, 66, 28)},
                {"drink bar", new BlockPos(17, 66, 13)}, {"table 7 aisle", new BlockPos(33, 66, 27)}, {"dining centre", new BlockPos(26, 66, 19)},
                {"kitchen board", new BlockPos(25, 66, 6)}, {"corridor", new BlockPos(7, 66, 8)}, {"womens", new BlockPos(10, 66, 19)},
                {"cavity", new BlockPos(10, 66, 13)}, {"lot spawn", new BlockPos(4, 66, 41)}, {"under the sign", new BlockPos(44, 66, 46)},
                {"entrance", new BlockPos(4, 66, 33)}, {"flowers", new BlockPos(76, 66, 52)}};
        StringBuilder sb = new StringBuilder("light levels (block/sky):");
        for (Object[] s : spots) {
            BlockPos p = (BlockPos) s[1];
            sb.append(" ").append(s[0]).append("=").append(level.getBrightness(LightLayer.BLOCK, p)).append("/").append(level.getBrightness(LightLayer.SKY, p));
        }
        log.accept(sb.toString());
    }

    /** Writes every marker and room to a text file (run/reisatsu-layout.txt in CI). */
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
        if (!Boolean.getBoolean("reisatsu.selftest")) return false;
        try {
            Class.forName("com.reisatsu.debug.SelfTest", false, WorldCheck.class.getClassLoader());
            return false;
        } catch (ClassNotFoundException e) {
            return true;
        }
    }

    @Mod.EventBusSubscriber(modid = Reisatsu.MODID)
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
            dumpLayout(java.nio.file.Path.of("reisatsu-layout.txt"));
        }

        static void log(String s) {
            LINES.add(s);
            Reisatsu.LOGGER.info("[reisatsu-selftest] {}", s);
            try {
                Files.writeString(java.nio.file.Path.of("reisatsu-selftest.txt"), String.join("\n", LINES) + "\n", StandardCharsets.UTF_8);
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
                check(false, "the dimension reisatsu:hidamari exists");
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
                Reisatsu.LOGGER.error("[reisatsu-selftest] exception", e);
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
