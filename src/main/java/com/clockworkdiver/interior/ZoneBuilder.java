package com.clockworkdiver.interior;

import com.clockworkdiver.block.MechanismBlockEntity;
import com.clockworkdiver.clock.DefectType;
import com.clockworkdiver.registry.ModBlocks;
import com.clockworkdiver.util.StructureBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Builds the giant interior of a clock around the faulty part. The player is "shrunk": a gear tooth is as
 * wide as a doorway, wheels are the size of houses. Every chamber is a brass drum (radius {@link #R},
 * height {@link #H}) with huge gears on the walls and ceiling, plus the repair challenge for the defect.
 */
public final class ZoneBuilder {
    public static final int R = 20;
    public static final int H = 22;
    private static final double SCREW_PITCH = 4.0;

    private ZoneBuilder() {}

    public static ZoneLayout build(ServerLevel level, BlockPos origin, DefectType type, int difficulty, int period, boolean tutorial,
                                   RandomSource rand) {
        StructureBuilder b = new StructureBuilder(level, origin);
        ZoneLayout layout = new ZoneLayout();
        b.clear(-R - 4, -4, -R - 4, R + 4, H + 4, R + 4);
        chamber(b, layout, type, period, rand);
        switch (type) {
            case RUST -> rust(b, layout, difficulty, tutorial, rand);
            case TEETH -> teeth(b, layout, difficulty, tutorial, period);
            case SCREW -> screw(b, layout, difficulty, tutorial);
            case SPRING -> spring(b, layout, period);
            case NEST -> nest(b, layout, period);
        }
        b.finish();

        layout.center = origin;
        layout.spawn = new Vec3(origin.getX() + 0.5, origin.getY(), origin.getZ() + R - 4 + 0.5);
        layout.spawnYaw = 180.0f;
        layout.bounds = new AABB(origin.getX() - R - 3, origin.getY() - 8, origin.getZ() - R - 3,
                origin.getX() + R + 4, origin.getY() + H + 4, origin.getZ() + R + 4);
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2.0 * i / 12.0;
            double x = Math.cos(a) * (R - 3.5);
            double z = Math.sin(a) * (R - 3.5);
            if (z > R - 10 && Math.abs(x) < 8) continue; // keep the arrival spot calm
            layout.enemySpawns.add(new Vec3(origin.getX() + 0.5 + x, origin.getY() + 0.1, origin.getZ() + 0.5 + z));
        }
        return layout;
    }

    // ------------------------------------------------------------------ chamber

    private static void chamber(StructureBuilder b, ZoneLayout layout, DefectType type, int period, RandomSource rand) {
        BlockState plate = ModBlocks.BRASS_PLATE.get().defaultBlockState();
        BlockState tiles = ModBlocks.BRASS_TILES.get().defaultBlockState();
        BlockState brass = ModBlocks.BRASS_BLOCK.get().defaultBlockState();
        BlockState jewel = ModBlocks.JEWEL_BEARING.get().defaultBlockState();
        BlockState rusty = ModBlocks.RUSTY_PLATE.get().defaultBlockState();
        boolean rustyZone = type == DefectType.RUST;

        for (int x = -R - 2; x <= R + 2; x++) {
            for (int z = -R - 2; z <= R + 2; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > R + 2.0) continue;
                b.set(x, -2, z, plate);
                b.set(x, H + 1, z, plate);
                double ang = (Math.toDegrees(Math.atan2(z, x)) + 360.0) % 360.0;
                if (d <= R + 0.5) {
                    BlockState floor = ((int) d % 6 == 5) ? plate : tiles;
                    if (d < 1.5) floor = jewel;
                    else if (rustyZone && rand.nextFloat() < 0.12f) floor = rusty;
                    b.set(x, -1, z, floor);
                    if ((int) d == 12 && ang % 30.0 < 4.0) b.set(x, H + 1, z, jewel);
                } else {
                    for (int y = -1; y <= H; y++) {
                        BlockState w = plate;
                        if (ang % 30.0 < 4.0) w = brass;
                        else if ((y == 3 || y == 11 || y == 18) && ang % 15.0 < 3.5) w = jewel;
                        else if (rustyZone && rand.nextFloat() < 0.08f) w = rusty;
                        b.set(x, y, z, w);
                    }
                }
            }
        }

        // Giant gears on the walls, all stuck until the part is repaired.
        if (type != DefectType.TEETH) {
            wallGear(b, layout, 0, 10, -(R - 2), MechanismBlockEntity.AXIS_Z, 7.0f, 42, 3.0f, MechanismBlockEntity.COLOR_BRASS, period);
        }
        wallGear(b, layout, R - 2, 10, 0, MechanismBlockEntity.AXIS_X, 6.0f, 36, -3.5f, MechanismBlockEntity.COLOR_COPPER, period);
        wallGear(b, layout, -(R - 2), 9, 0, MechanismBlockEntity.AXIS_X, 6.5f, 39, 3.2f, MechanismBlockEntity.COLOR_STEEL, period);
        if (type != DefectType.TEETH) {
            wallGear(b, layout, 7, 15, -(R - 4), MechanismBlockEntity.AXIS_Z, 3.0f, 18, -7.0f, MechanismBlockEntity.COLOR_GOLD, period);
        }
        if (type != DefectType.SPRING && type != DefectType.NEST) {
            wallGear(b, layout, 0, H - 3, 0, MechanismBlockEntity.AXIS_Y, 11.0f, 66, 2.0f, MechanismBlockEntity.COLOR_BRASS, period);
            wallGear(b, layout, 12, H - 5, 9, MechanismBlockEntity.AXIS_Y, 4.0f, 24, -5.5f, MechanismBlockEntity.COLOR_STEEL, period);
        }
    }

    private static void wallGear(StructureBuilder b, ZoneLayout layout, int x, int y, int z, int axis, float radius, int teeth,
                                 float speed, int color, int period) {
        MechanismBlockEntity m = b.mechanism(x, y, z);
        m.gear(radius, teeth, 0.6f, axis, speed, color);
        m.period = period;
        m.running = false;
        m.phase = (x * 31 + z * 17) % 360;
        m.sync();
        layout.mechanisms.add(b.abs(x, y, z));
    }

    private static void vent(StructureBuilder b, ZoneLayout layout, int x, int z) {
        b.set(x, -1, z, ModBlocks.STEAM_VENT.get().defaultBlockState());
        layout.vents.add(b.abs(x, -1, z));
    }

    // ------------------------------------------------------------------ rust

    private static void rust(StructureBuilder b, ZoneLayout layout, int difficulty, boolean tutorial, RandomSource rand) {
        BlockState brass = ModBlocks.BRASS_BLOCK.get().defaultBlockState();
        BlockState rusty = ModBlocks.RUSTY_PLATE.get().defaultBlockState();
        BlockState jewel = ModBlocks.JEWEL_BEARING.get().defaultBlockState();
        int gearR = 10;
        for (int x = -gearR - 1; x <= gearR + 1; x++) {
            for (int z = -gearR - 1; z <= gearR + 1; z++) {
                double d = Math.sqrt(x * x + z * z);
                double ang = (Math.toDegrees(Math.atan2(z, x)) + 360.0) % 360.0;
                if (d <= gearR - 0.5) {
                    boolean spoke = ang % 90.0 < 14.0 || ang % 90.0 > 76.0;
                    if (d > 3.5 && d < 7.5 && !spoke) continue; // spoked wheel
                    b.set(x, 0, z, rand.nextFloat() < 0.35f ? rusty : brass);
                } else if (d <= gearR + 1.0 && ang % 12.0 < 6.0) {
                    b.set(x, 0, z, rand.nextFloat() < 0.5f ? rusty : brass);
                }
            }
        }
        for (int y = 1; y <= H; y++) {
            if (y == H - 3) continue; // the ceiling gear's hub sits on the axle here
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (Math.abs(x) + Math.abs(z) > 1) continue;
                    b.set(x, y, z, (y == 1 || y == H || y % 7 == 0) ? jewel : brass);
                }
            }
        }
        int count = tutorial ? 6 : Math.min(14, 8 + difficulty * 2);
        Set<Long> used = new HashSet<>();
        int guard = 0;
        while (layout.targets.size() < count && guard++ < 500) {
            double a = rand.nextDouble() * Math.PI * 2.0;
            double r = rand.nextBoolean() ? 2.2 + rand.nextDouble() * 1.2 : 7.6 + rand.nextDouble() * 1.8;
            int x = (int) Math.round(Math.cos(a) * r);
            int z = (int) Math.round(Math.sin(a) * r);
            if (Math.abs(x) + Math.abs(z) <= 1) continue;
            if (b.get(x, 0, z).isAir()) continue;
            long key = (long) x * 1000L + z;
            if (!used.add(key)) continue;
            b.set(x, 1, z, ModBlocks.RUST_CLUSTER.get().defaultBlockState());
            layout.targets.add(b.abs(x, 1, z));
        }
        vent(b, layout, 14, 7);
        vent(b, layout, -14, 7);
    }

    // ------------------------------------------------------------------ teeth

    private static void teeth(StructureBuilder b, ZoneLayout layout, int difficulty, boolean tutorial, int period) {
        BlockState brass = ModBlocks.BRASS_BLOCK.get().defaultBlockState();
        BlockState plate = ModBlocks.BRASS_PLATE.get().defaultBlockState();
        int sockets = tutorial ? 3 : Math.min(6, 3 + difficulty / 2);

        MechanismBlockEntity wheel = b.mechanism(0, 11, -(R - 3));
        wheel.kind(MechanismBlockEntity.ESCAPE_WHEEL);
        wheel.radius = 8.0f;
        wheel.teeth = 15;
        wheel.thickness = 0.8f;
        wheel.axis = MechanismBlockEntity.AXIS_Z;
        wheel.speed = 24.0f;
        wheel.color = MechanismBlockEntity.COLOR_STEEL;
        wheel.missing = sockets;
        wheel.period = period;
        wheel.running = false;
        wheel.sync();
        layout.mechanisms.add(b.abs(0, 11, -(R - 3)));
        layout.mainMechanism = b.abs(0, 11, -(R - 3));

        // A raised dais under the wheel.
        for (int x = -15; x <= 15; x++) {
            for (int z = -R; z <= -5; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > R - 1.5) continue;
                b.set(x, 0, z, (z == -5 || Math.abs(x) == 15) ? plate : brass);
            }
        }
        for (int i = 0; i < sockets; i++) {
            double ang = Math.toRadians(-90.0 + (i - (sockets - 1) / 2.0) * 24.0);
            int x = (int) Math.round(Math.cos(ang) * 11.0);
            int z = (int) Math.round(Math.sin(ang) * 11.0);
            b.set(x, 1, z, ModBlocks.TOOTH_SOCKET.get().defaultBlockState());
            layout.targets.add(b.abs(x, 1, z));
        }
        // Spare parts lying around for cover.
        int[][] crates = {{-9, 6}, {8, 4}, {-4, 10}, {12, -1}};
        for (int[] c : crates) {
            b.set(c[0], 0, c[1], Blocks.BARREL.defaultBlockState());
            b.set(c[0] + 1, 0, c[1], ModBlocks.BRASS_PLATE.get().defaultBlockState());
        }
        vent(b, layout, 13, 9);
        vent(b, layout, -13, 9);
    }

    // ------------------------------------------------------------------ screw

    private static void screw(StructureBuilder b, ZoneLayout layout, int difficulty, boolean tutorial) {
        int needed = tutorial ? 4 : Math.min(7, 5 + difficulty / 2);
        int height = 4 + needed;
        layout.screwHeight = height;
        layout.screwMaxHeight = height;
        buildScrew(b, height, 0);
        vent(b, layout, 12, 10);
        vent(b, layout, -12, 10);
        vent(b, layout, 0, -14);
    }

    /** Rebuilds the screw at a new height and slot angle (each successful turn drives it one block deeper). */
    public static void rebuildScrew(ServerLevel level, BlockPos origin, int height, int phaseStep) {
        StructureBuilder b = new StructureBuilder(level, origin);
        b.clear(-6, 0, -6, 6, height + 7, 6);
        buildScrew(b, height, phaseStep);
    }

    private static void buildScrew(StructureBuilder b, int height, int phaseStep) {
        BlockState brass = ModBlocks.BRASS_BLOCK.get().defaultBlockState();
        BlockState plate = ModBlocks.BRASS_PLATE.get().defaultBlockState();
        BlockState steel = Blocks.IRON_BLOCK.defaultBlockState();
        double phase = phaseStep * Math.PI / 4.0;
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= 2.5) {
                    for (int y = 0; y < height; y++) b.set(x, y, z, steel);
                } else if (d <= 4.6) {
                    double theta = Math.atan2(z, x) + phase;
                    double frac = theta / (Math.PI * 2.0);
                    frac -= Math.floor(frac);
                    for (int k = 0; k < 16; k++) {
                        int y = (int) Math.floor((frac + k) * SCREW_PITCH);
                        if (y > height - 1) break;
                        b.set(x, y, z, plate);
                    }
                }
                if (d <= 4.8) b.set(x, height, z, brass);
            }
        }
        int[][] dirs = {{1, 0}, {1, 1}, {0, 1}, {1, -1}};
        int[] dir = dirs[Math.floorMod(phaseStep, 4)];
        int reach = (dir[0] != 0 && dir[1] != 0) ? 2 : 3;
        for (int t = -reach; t <= reach; t++) {
            b.set(t * dir[0], height, t * dir[1], ModBlocks.SCREW_HEAD.get().defaultBlockState());
        }
    }

    // ------------------------------------------------------------------ spring

    private static void spring(StructureBuilder b, ZoneLayout layout, int period) {
        BlockState brass = ModBlocks.BRASS_BLOCK.get().defaultBlockState();
        BlockState plate = ModBlocks.BRASS_PLATE.get().defaultBlockState();
        for (int x = -13; x <= 13; x++) {
            for (int z = -13; z <= 13; z++) {
                double d = Math.sqrt(x * x + z * z);
                double ang = (Math.toDegrees(Math.atan2(z, x)) + 360.0) % 360.0;
                if (d > 10.5 && d <= 12.1) {
                    double m = (ang + 45.0) % 90.0;
                    if (m < 11.0 || m > 79.0) continue; // four gaps on the diagonals
                    b.set(x, 0, z, plate);
                    b.set(x, 1, z, ang % 20.0 < 10.0 ? brass : plate);
                }
                if (d <= 3.2) b.set(x, 0, z, brass);
            }
        }
        b.set(0, 1, 0, ModBlocks.WINDING_CRANK.get().defaultBlockState());
        layout.crank = b.abs(0, 1, 0);

        MechanismBlockEntity spring = b.mechanism(0, 14, 0);
        spring.kind(MechanismBlockEntity.SPRING);
        spring.radius = 12.5f;
        spring.thickness = 2.0f;
        spring.axis = MechanismBlockEntity.AXIS_Y;
        spring.color = MechanismBlockEntity.COLOR_STEEL;
        spring.progress = 0.0f;
        spring.period = period;
        spring.running = false;
        spring.sync();
        layout.mainMechanism = b.abs(0, 14, 0);
        layout.mechanisms.add(layout.mainMechanism);

        vent(b, layout, 7, 0);
        vent(b, layout, -7, 0);
        vent(b, layout, 0, 7);
        vent(b, layout, 0, -7);
    }

    // ------------------------------------------------------------------ nest

    private static void nest(StructureBuilder b, ZoneLayout layout, int period) {
        BlockState brass = ModBlocks.BRASS_BLOCK.get().defaultBlockState();
        BlockState jewel = ModBlocks.JEWEL_BEARING.get().defaultBlockState();
        BlockState rusty = ModBlocks.RUSTY_PLATE.get().defaultBlockState();
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(45.0 + i * 90.0);
            int cx = (int) Math.round(Math.cos(a) * 11.0);
            int cz = (int) Math.round(Math.sin(a) * 11.0);
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (x * x + z * z > 4) continue;
                    for (int y = 0; y <= H; y++) {
                        b.set(cx + x, y, cz + z, (y == 6 || y == 14) ? jewel : (y < 3 && (x + z + y) % 3 == 0 ? rusty : brass));
                    }
                }
            }
        }
        MechanismBlockEntity balance = b.mechanism(0, 15, 0);
        balance.kind(MechanismBlockEntity.BALANCE);
        balance.radius = 9.0f;
        balance.thickness = 0.8f;
        balance.axis = MechanismBlockEntity.AXIS_Y;
        balance.speed = 200.0f;
        balance.color = MechanismBlockEntity.COLOR_GOLD;
        balance.period = period;
        balance.running = false;
        balance.sync();
        layout.mainMechanism = b.abs(0, 15, 0);
        layout.mechanisms.add(layout.mainMechanism);

        vent(b, layout, 15, 0);
        vent(b, layout, -15, 0);
        vent(b, layout, 0, -15);
        vent(b, layout, 0, 15);
    }
}
