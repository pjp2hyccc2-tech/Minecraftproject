package com.mcbesiege.test;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mcbesiege.MCBesiege;
import com.mcbesiege.block.MachinePartBlock;
import com.mcbesiege.entity.MachineEntity;
import com.mcbesiege.machine.LevelPhysics;
import com.mcbesiege.machine.MPart;
import com.mcbesiege.machine.MachineManager;
import com.mcbesiege.mission.Arena;
import com.mcbesiege.mission.MissionManager;
import com.mcbesiege.mission.Missions;
import com.mcbesiege.part.Keys;
import com.mcbesiege.part.PartType;
import com.mcbesiege.physics.V3;
import com.mcbesiege.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** CI only (-Dmcbesiege.selftest=true): drives a test car, fires a cannon at a wall, sets off a bomb, generates arenas. */
public final class SelfTest {
    private SelfTest() {}

    private static final boolean ENABLED = Boolean.getBoolean("mcbesiege.selftest");
    private static final List<String> errors = new ArrayList<>();
    private static final List<String> info = new ArrayList<>();
    private static int tick = -1;
    private static BlockPos base;
    private static MachineEntity car, gun, steer;
    private static V3 carStart, gunStart, steerStart, steerFwd0;
    private static int wallBefore;

    private static void err(String s) {
        errors.add(s);
        MCBesiege.LOGGER.error("[mcbesiege-selftest] FAIL {}", s);
    }

    private static void ok(String s) {
        info.add(s);
        MCBesiege.LOGGER.info("[mcbesiege-selftest] {}", s);
    }

    public static void start(MinecraftServer server) {
        if (!ENABLED) return;
        ok("starting");
        tick = 0;
    }

    private static void part(ServerLevel l, BlockPos p, PartType t, Direction f) {
        BlockState s = ModBlocks.part(t).defaultBlockState().setValue(MachinePartBlock.FACING, f);
        l.setBlock(p, s, 3);
    }

    public static void tick(MinecraftServer server) {
        if (!ENABLED || tick < 0) return;
        tick++;
        ServerLevel level = server.overworld();
        try {
            switch (tick) {
                case 20 -> buildSite(level);
                case 30 -> {
                    car = MachineManager.startSimulation(level, base, null);
                    gun = MachineManager.startSimulation(level, base.offset(20, 0, 0), null);
                    if (car == null || car.machine == null) err("car did not start");
                    else {
                        carStart = car.machine.corePos();
                        ok("car started with " + car.machine.parts.size() + " parts, " + car.machine.bodies.size() + " bodies, " + car.machine.joints.size() + " joints");
                    }
                    if (gun == null || gun.machine == null) err("cannon machine did not start");
                    else gunStart = gun.machine.corePos();
                    steer = MachineManager.startSimulation(level, base.offset(-20, 0, 0), null);
                    if (steer == null || steer.machine == null) err("steering car did not start");
                    else {
                        steerStart = steer.machine.corePos();
                        steerFwd0 = steer.machine.core.base.body.rb.rot.rotate(V3.Z);
                        ok("steering car started with " + steer.machine.parts.size() + " parts, " + steer.machine.bodies.size() + " bodies, " + steer.machine.joints.size() + " joints");
                    }
                }
                case 50 -> {
                    if (car != null && car.machine != null) {
                        V3 c = car.machine.corePos();
                        ok(String.format("car settled at %.2f %.2f %.2f (start %.2f %.2f %.2f)", c.x, c.y, c.z, carStart.x, carStart.y, carStart.z));
                        car.machine.keys.add(Keys.UP);
                    }
                    if (steer != null && steer.machine != null) {
                        steer.machine.keys.add(Keys.UP);
                        steer.machine.keys.add(Keys.LEFT);
                    }
                }
                case 110 -> {
                    if (car != null && car.machine != null) {
                        car.machine.keys.clear();
                        V3 c = car.machine.corePos();
                        double dz = c.z - carStart.z;
                        V3 up = car.machine.core.base.body.rb.rot.rotate(V3.Y);
                        ok(String.format("car after driving: dz=%.2f dx=%.2f y=%.2f up.y=%.3f", dz, c.x - carStart.x, c.y, up.y));
                        if (dz < 6) err("car did not drive forward (dz=" + dz + ")");
                        if (up.y < 0.9) err("car flipped (up.y=" + up.y + ")");
                    }
                    if (steer != null && steer.machine != null) {
                        steer.machine.keys.clear();
                        V3 c = steer.machine.corePos();
                        V3 f = steer.machine.core.base.body.rb.rot.rotate(V3.Z);
                        double yaw = Math.toDegrees(Math.atan2(steerFwd0.cross(f).y, steerFwd0.dot(f)));
                        ok(String.format("steering car: yaw %.1f deg (positive = left), moved dx=%.2f dz=%.2f, up.y=%.3f", yaw,
                                c.x - steerStart.x, c.z - steerStart.z, steer.machine.core.base.body.rb.rot.rotate(V3.Y).y));
                        if (yaw < 15) err("steering car did not turn left (yaw=" + yaw + ")");
                    }
                    if (gun != null && gun.machine != null) {
                        wallBefore = countWall(level);
                        gun.machine.keys.add(Keys.C);
                    }
                }
                case 112 -> {
                    if (gun != null && gun.machine != null) gun.machine.keys.clear();
                }
                case 160 -> {
                    int after = countWall(level);
                    ok("wall blocks before " + wallBefore + " after cannon " + after);
                    if (after >= wallBefore) err("cannonball did not break the wall");
                    if (gun != null && gun.machine != null) {
                        V3 c = gun.machine.corePos();
                        ok(String.format("cannon recoil dx=%.2f", c.x - gunStart.x));
                        // bomb test: detonate the bomb part directly
                        for (MPart p : gun.machine.parts) {
                            if (p.type == PartType.BOMB && p.alive) {
                                gun.machine.explode(p);
                                ok("bomb detonated");
                            }
                        }
                    }
                }
                case 180 -> {
                    if (gun != null && gun.machine != null) {
                        int detached = 0;
                        for (MPart p : gun.machine.parts) if (!p.alive || p.debris) detached++;
                        ok("parts destroyed/detached by bomb: " + detached + ", bodies " + gun.machine.bodies.size());
                    }
                    if (car != null && !car.isRemoved()) MachineManager.reset(car, true);
                    if (gun != null && !gun.isRemoved()) MachineManager.reset(gun, true);
                    if (steer != null && !steer.isRemoved()) MachineManager.reset(steer, true);
                }
                case 185 -> {
                    BlockState s = level.getBlockState(base);
                    if (!(s.getBlock() instanceof MachinePartBlock)) err("reset did not restore the starting block");
                    else ok("reset restored the machine");
                    arenas(server);
                }
                case 200 -> finish(server);
                default -> {}
            }
        } catch (Throwable t) {
            err("crash at tick " + tick + ": " + t);
            MCBesiege.LOGGER.error("[mcbesiege-selftest]", t);
            finish(server);
        }
    }

    private static void buildSite(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        int x = spawn.getX() + 4, z = spawn.getZ() + 4;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        base = new BlockPos(x, y, z); // y = first free block above the ground
        // clear and flatten
        for (int dx = -36; dx <= 40; dx++) {
            for (int dz = -6; dz <= 40; dz++) {
                level.setBlock(new BlockPos(x + dx, y - 1, z + dz), Blocks.STONE.defaultBlockState(), 3);
                for (int dy = 0; dy < 8; dy++) level.setBlock(new BlockPos(x + dx, y + dy, z + dz), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        // car: chassis 3x5, start block in the middle, 4 wheels, forward = south (+z)
        for (int dx = -1; dx <= 1; dx++) for (int dz = -2; dz <= 2; dz++) part(level, base.offset(dx, 0, dz), PartType.WOODEN_BLOCK, Direction.UP);
        part(level, base, PartType.STARTING_BLOCK, Direction.SOUTH);
        part(level, base.offset(-2, 0, -2), PartType.WHEEL, Direction.WEST);
        part(level, base.offset(-2, 0, 2), PartType.WHEEL, Direction.WEST);
        part(level, base.offset(2, 0, -2), PartType.WHEEL, Direction.EAST);
        part(level, base.offset(2, 0, 2), PartType.WHEEL, Direction.EAST);
        // steering car: rear wheels on the chassis, front wheels on steering hinges (Besiege layout)
        BlockPos s = base.offset(-20, 0, 0);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -2; dz <= 2; dz++) part(level, s.offset(dx, 0, dz), PartType.WOODEN_BLOCK, Direction.UP);
        part(level, s, PartType.STARTING_BLOCK, Direction.SOUTH);
        part(level, s.offset(-2, 0, -2), PartType.WHEEL, Direction.WEST);
        part(level, s.offset(2, 0, -2), PartType.WHEEL, Direction.EAST);
        part(level, s.offset(-2, 0, 2), PartType.STEERING_HINGE, Direction.WEST);
        part(level, s.offset(2, 0, 2), PartType.STEERING_HINGE, Direction.EAST);
        part(level, s.offset(-3, 0, 2), PartType.WHEEL, Direction.WEST);
        part(level, s.offset(3, 0, 2), PartType.WHEEL, Direction.EAST);
        // cannon machine facing +x, wall 6 blocks ahead
        BlockPos g = base.offset(20, 0, 0);
        part(level, g, PartType.STARTING_BLOCK, Direction.EAST);
        part(level, g.offset(0, -0, 1), PartType.IRON_BLOCK, Direction.UP);
        part(level, g.offset(0, 0, -1), PartType.IRON_BLOCK, Direction.UP);
        part(level, g.offset(1, 0, 0), PartType.CANNON, Direction.EAST);
        part(level, g.offset(-1, 0, 0), PartType.BOMB, Direction.WEST);
        for (int dy = 0; dy < 4; dy++) for (int dz = -2; dz <= 2; dz++) level.setBlock(g.offset(7, dy, dz), Blocks.COBBLESTONE.defaultBlockState(), 3);
        ok("site built at " + base.toShortString() + " ground y " + y);
    }

    private static int countWall(ServerLevel level) {
        BlockPos g = base.offset(20, 0, 0);
        int n = 0;
        for (int dy = 0; dy < 4; dy++) for (int dz = -2; dz <= 2; dz++) if (!level.getBlockState(g.offset(7, dy, dz)).isAir()) n++;
        return n;
    }

    private static void arenas(MinecraftServer server) {
        ServerLevel dim = server.getLevel(MissionManager.DIM);
        if (dim == null) {
            err("siege dimension missing");
            return;
        }
        ok("siege dimension present, ground at " + dim.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
        for (Missions.Def d : Missions.ALL) {
            try {
                Arena a = new Arena(dim, new BlockPos(5000 + Missions.index(d) * 200, MissionManager.GROUND, 5000));
                d.build().accept(a);
                ok(String.format("arena %s: %d targets, %d mobs, %d props, goal=%s", d.id(), a.targets.size(), a.mobs.size(), a.props.size(), a.goal != null));
                if (!a.destroyTargets && !a.killAll && !a.deliver && !a.reach) err("mission " + d.id() + " has no objective");
            } catch (Throwable t) {
                err("arena " + d.id() + " crashed: " + t);
            }
        }
    }

    private static void finish(MinecraftServer server) {
        tick = -1;
        JsonObject o = new JsonObject();
        JsonArray e = new JsonArray();
        errors.forEach(e::add);
        JsonArray i = new JsonArray();
        info.forEach(i::add);
        o.add("errors", e);
        o.add("info", i);
        o.addProperty("ok", errors.isEmpty());
        try {
            Files.writeString(Path.of("mcbesiege-selftest.json"), new GsonBuilder().setPrettyPrinting().create().toJson(o), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            MCBesiege.LOGGER.error("[mcbesiege-selftest] could not write results", ex);
        }
        MCBesiege.LOGGER.info("[mcbesiege-selftest] DONE ok={} errors={}", errors.isEmpty(), errors.size());
        server.halt(false);
    }
}
