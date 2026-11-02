package com.clockworkdiver.workshop;

import com.clockworkdiver.block.MechanismBlockEntity;
import com.clockworkdiver.block.RepairBenchBlock;
import com.clockworkdiver.registry.ModBlocks;
import com.clockworkdiver.util.StructureBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Builds a player's steampunk clock repair shop in the workshop dimension: the shop with its counter,
 * repair bench, clocks and pipes, the street outside, a facade across the street and a clock tower.
 * Coordinates are relative to the shop origin (floor surface, centre of the shop).
 */
public final class WorkshopBuilder {
    public static final int VERSION = 1;

    private WorkshopBuilder() {}

    public static void build(ServerLevel level, BlockPos origin) {
        StructureBuilder b = new StructureBuilder(level, origin);
        RandomSource rand = RandomSource.create(origin.asLong());
        b.clear(-18, -4, -12, 18, 44, 46);

        BlockState planks = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState bricks = Blocks.BRICKS.defaultBlockState();
        BlockState pillar = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
        BlockState beamX = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        BlockState beamZ = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        BlockState brassTiles = ModBlocks.BRASS_TILES.get().defaultBlockState();

        // Foundation and floor.
        for (int x = -10; x <= 10; x++) {
            for (int z = -8; z <= 8; z++) {
                b.set(x, -2, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
                boolean border = Math.abs(x) == 9 || Math.abs(z) == 7;
                boolean wall = Math.abs(x) == 10 || Math.abs(z) == 8;
                BlockState floor = wall ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                        : border ? brassTiles : ((x + z) % 2 == 0 ? planks : Blocks.SPRUCE_PLANKS.defaultBlockState());
                if (border && Math.abs(x) == 9 && Math.abs(z) == 7) floor = ModBlocks.JEWEL_BEARING.get().defaultBlockState();
                b.set(x, -1, z, floor);
            }
        }
        // Rug in the middle of the shop.
        for (int x = -2; x <= 2; x++) for (int z = -1; z <= 1; z++) b.set(x, 0, z, Blocks.RED_CARPET);

        // Walls.
        for (int y = 0; y <= 5; y++) {
            for (int x = -10; x <= 10; x++) {
                b.set(x, y, -8, wallBlock(x, y, bricks, planks, beamX, pillar));
                b.set(x, y, 8, wallBlock(x, y, bricks, planks, beamX, pillar));
            }
            for (int z = -7; z <= 7; z++) {
                b.set(-10, y, z, wallBlock(z, y, bricks, planks, beamZ, pillar));
                b.set(10, y, z, wallBlock(z, y, bricks, planks, beamZ, pillar));
            }
        }
        // Side windows.
        for (int z : new int[]{-5, -4, 4, 5}) {
            for (int y = 2; y <= 3; y++) {
                b.setConnected(-10, y, z, Blocks.GLASS_PANE);
                b.setConnected(10, y, z, Blocks.GLASS_PANE);
            }
        }
        // Shop front: display windows either side of the doorway.
        for (int x : new int[]{-7, -6, -5, -4, 4, 5, 6, 7}) {
            for (int y = 1; y <= 3; y++) b.setConnected(x, y, 8, Blocks.GLASS_PANE);
        }
        for (int x = -1; x <= 1; x++) for (int y = 0; y <= 2; y++) b.set(x, y, 8, Blocks.AIR);
        b.set(0, 3, 8, ModBlocks.BRASS_BLOCK.get());

        // Ceiling with beams and the roof.
        for (int x = -10; x <= 10; x++) {
            for (int z = -8; z <= 8; z++) {
                b.set(x, 6, z, planks);
                b.set(x, 7, z, Blocks.DEEPSLATE_TILES);
            }
        }
        for (int z : new int[]{-4, 0, 4}) for (int x = -9; x <= 9; x++) b.set(x, 5, z, beamX);
        // Chimneys with signal smoke.
        chimney(b, -7, -5, 8);
        chimney(b, 6, -6, 8);

        // Hanging lanterns.
        BlockState hanging = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        for (int[] p : new int[][]{{-6, -2}, {6, -2}, {-6, 6}, {6, 6}, {0, -2}, {0, 6}}) b.set(p[0], 5, p[1], hanging);

        // Counter.
        BlockState counterTop = Blocks.WAXED_CUT_COPPER_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        for (int x = -6; x <= 3; x++) {
            b.set(x, 0, 3, Blocks.STRIPPED_SPRUCE_WOOD);
            b.set(x, 1, 3, counterTop);
        }
        b.set(-6, 2, 3, Blocks.AIR);
        MechanismBlockEntity deskClock = b.mechanism(-4, 2, 3);
        deskClock.kind(MechanismBlockEntity.CLOCK_FACE).flip(false);
        deskClock.radius = 0.38f;
        deskClock.axis = MechanismBlockEntity.AXIS_Z;
        deskClock.color = MechanismBlockEntity.COLOR_GOLD;
        deskClock.phase = 37;
        deskClock.offsetY = -0.2f;
        deskClock.sync();

        // Repair bench at the back, facing the room.
        b.set(0, 0, -6, ModBlocks.REPAIR_BENCH.get().defaultBlockState().setValue(RepairBenchBlock.FACING, Direction.SOUTH));
        BlockState barrel = Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP);
        b.set(-1, 0, -6, barrel);
        b.set(1, 0, -6, barrel);
        b.set(-1, 1, -6, Blocks.LANTERN);
        MechanismBlockEntity benchClock = b.mechanism(1, 1, -6);
        benchClock.kind(MechanismBlockEntity.CLOCK_FACE);
        benchClock.radius = 0.32f;
        benchClock.axis = MechanismBlockEntity.AXIS_Z;
        benchClock.color = MechanismBlockEntity.COLOR_BRASS;
        benchClock.offsetY = -0.15f;
        benchClock.phase = 210;
        benchClock.sync();

        // Gear train on the back wall.
        MechanismBlockEntity big = b.mechanism(0, 3, -7);
        big.gear(2.0f, 20, 0.35f, MechanismBlockEntity.AXIS_Z, 6.0f, MechanismBlockEntity.COLOR_BRASS);
        big.offsetZ = -0.2f;
        big.sync();
        for (int side : new int[]{-3, 3}) {
            MechanismBlockEntity small = b.mechanism(side, 3, -7);
            small.gear(1.0f, 10, 0.35f, MechanismBlockEntity.AXIS_Z, -12.0f, MechanismBlockEntity.COLOR_COPPER);
            small.phase = 18.0f;
            small.offsetZ = -0.2f;
            small.sync();
        }

        // Bookshelves and a grandfather clock.
        for (int y = 0; y <= 2; y++) {
            for (int x : new int[]{-9, -8, 8, 9}) b.set(x, y, -7, Blocks.BOOKSHELF);
        }
        for (int y = 0; y <= 4; y++) {
            b.set(-6, y, -7, Blocks.DARK_OAK_LOG);
            b.set(-4, y, -7, Blocks.DARK_OAK_LOG);
        }
        b.set(-5, 5, -7, Blocks.DARK_OAK_PLANKS);
        MechanismBlockEntity pendulum = b.mechanism(-5, 3, -7);
        pendulum.kind(MechanismBlockEntity.PENDULUM);
        pendulum.radius = 2.2f;
        pendulum.speed = 14.0f;
        pendulum.axis = MechanismBlockEntity.AXIS_Z;
        pendulum.color = MechanismBlockEntity.COLOR_GOLD;
        pendulum.period = 20;
        pendulum.sync();
        MechanismBlockEntity grandFace = b.mechanism(-5, 4, -7);
        grandFace.kind(MechanismBlockEntity.CLOCK_FACE);
        grandFace.radius = 0.45f;
        grandFace.axis = MechanismBlockEntity.AXIS_Z;
        grandFace.color = MechanismBlockEntity.COLOR_DARK;
        grandFace.sync();

        // A cabinet of small clocks.
        BlockState shelf = Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
        for (int x = 4; x <= 6; x++) {
            b.set(x, 1, -7, shelf);
            MechanismBlockEntity c = b.mechanism(x, 2, -7);
            c.kind(MechanismBlockEntity.CLOCK_FACE);
            c.radius = 0.33f;
            c.axis = MechanismBlockEntity.AXIS_Z;
            c.color = x == 4 ? MechanismBlockEntity.COLOR_COPPER : x == 5 ? MechanismBlockEntity.COLOR_STEEL : MechanismBlockEntity.COLOR_GOLD;
            c.phase = x * 97;
            c.offsetY = -0.12f;
            c.sync();
        }

        // Clocks hung on the side walls, each set to a different hour.
        int phase = 0;
        for (int z : new int[]{-2, 1}) {
            for (int side : new int[]{-1, 1}) {
                MechanismBlockEntity wallClock = b.mechanism(side * 9, 3, z);
                wallClock.kind(MechanismBlockEntity.CLOCK_FACE);
                wallClock.radius = 0.42f;
                wallClock.axis = MechanismBlockEntity.AXIS_X;
                wallClock.flip(side > 0);
                wallClock.offsetX = side * 0.35f;
                wallClock.color = (phase % 2 == 0) ? MechanismBlockEntity.COLOR_BRASS : MechanismBlockEntity.COLOR_DARK;
                wallClock.phase = phase * 133;
                wallClock.sync();
                phase++;
            }
        }

        // Workbench corner and storage.
        b.set(8, 0, -4, Blocks.ANVIL);
        b.set(8, 0, -2, Blocks.SMITHING_TABLE);
        b.set(8, 0, 0, Blocks.CRAFTING_TABLE);
        b.set(-8, 0, -2, Blocks.CHEST);
        b.set(-8, 0, 0, barrel);
        b.set(-8, 1, 0, Blocks.POTTED_RED_TULIP);

        // Steam pipes.
        BlockState pipeY = ModBlocks.COPPER_PIPE.get().defaultBlockState();
        BlockState pipeX = ModBlocks.COPPER_PIPE.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        for (int y = 0; y <= 4; y++) {
            b.set(-9, y, 6, pipeY);
            b.set(9, y, 6, pipeY);
        }
        for (int x = -8; x <= 8; x++) b.set(x, 5, 7, pipeX);
        b.set(-9, 5, 7, pipeY);
        b.set(9, 5, 7, pipeY);
        b.set(-9, -1, 5, ModBlocks.STEAM_VENT.get());

        buildStreet(b, rand);
        buildFacade(b);
        buildTower(b);
        b.finish();
    }

    private static BlockState wallBlock(int along, int y, BlockState bricks, BlockState planks, BlockState beam, BlockState pillar) {
        if (Math.abs(along) % 5 == 0) return pillar;
        if (y == 5) return beam;
        if (y <= 1) return bricks;
        return planks;
    }

    /** A brick chimney; hay under the campfire makes tall signal smoke. */
    private static void chimney(StructureBuilder b, int x, int z, int baseY) {
        for (int y = baseY; y <= baseY + 2; y++) b.set(x, y, z, Blocks.BRICKS);
        b.set(x, baseY + 3, z, Blocks.HAY_BLOCK);
        b.set(x, baseY + 4, z, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true));
    }

    private static void buildStreet(StructureBuilder b, RandomSource rand) {
        for (int x = -9; x <= 9; x++) {
            for (int z = 9; z <= 27; z++) {
                b.set(x, -2, z, Blocks.STONE_BRICKS);
                BlockState top;
                if (Math.abs(x) >= 6) top = Blocks.SMOOTH_STONE.defaultBlockState();
                else top = rand.nextInt(5) == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
                b.set(x, -1, z, top);
            }
        }
        // Kerb wall so nobody tumbles into the void.
        for (int z = 9; z <= 27; z++) {
            b.set(-9, 0, z, Blocks.STONE_BRICKS);
            b.set(9, 0, z, Blocks.STONE_BRICKS);
        }
        // Gas lamps.
        for (int z : new int[]{12, 20}) {
            for (int side : new int[]{-7, 7}) {
                for (int y = 0; y <= 2; y++) b.set(side, y, z, Blocks.DARK_OAK_FENCE);
                b.set(side, 3, z, Blocks.LANTERN);
            }
        }
        // Manhole steam.
        b.set(3, -1, 15, ModBlocks.STEAM_VENT.get());
        b.set(-4, -1, 22, ModBlocks.STEAM_VENT.get());
    }

    private static void buildFacade(StructureBuilder b) {
        for (int x = -12; x <= 12; x++) {
            for (int y = -1; y <= 12; y++) {
                for (int z = 28; z <= 30; z++) b.set(x, y, z, z == 28 ? Blocks.BRICKS : Blocks.DARK_OAK_PLANKS);
            }
            b.set(x, 13, 28, Blocks.STONE_BRICK_SLAB);
        }
        // Glowing shop windows.
        for (int wx : new int[]{-9, -5, 4, 8}) {
            for (int y = 1; y <= 9; y += 4) {
                for (int dx = 0; dx <= 1; dx++) {
                    for (int dy = 0; dy <= 1; dy++) {
                        b.set(wx + dx, y + dy, 28, Blocks.ORANGE_STAINED_GLASS);
                        b.set(wx + dx, y + dy, 29, Blocks.SHROOMLIGHT);
                    }
                }
            }
        }
        for (int x = -12; x <= 12; x += 6) {
            for (int y = 0; y <= 12; y++) b.set(x, y, 27, Blocks.STRIPPED_DARK_OAK_LOG);
        }
        chimney(b, -10, 29, 13);
        chimney(b, 10, 30, 13);
        MechanismBlockEntity gear = b.mechanism(8, 15, 28);
        gear.gear(2.5f, 24, 0.5f, MechanismBlockEntity.AXIS_Z, 4.0f, MechanismBlockEntity.COLOR_COPPER);
        gear.stepped(false);
        gear.sync();
        MechanismBlockEntity gear2 = b.mechanism(4, 14, 28);
        gear2.gear(1.5f, 14, 0.5f, MechanismBlockEntity.AXIS_Z, -6.7f, MechanismBlockEntity.COLOR_BRASS);
        gear2.stepped(false);
        gear2.offsetX = 0.05f;
        gear2.sync();
    }

    private static void buildTower(StructureBuilder b) {
        for (int y = -1; y <= 34; y++) {
            for (int x = -3; x <= 3; x++) {
                for (int z = 34; z <= 40; z++) {
                    boolean edge = Math.abs(x) == 3 || z == 34 || z == 40;
                    if (!edge) continue;
                    Block block = (Math.abs(x) == 3 && (z == 34 || z == 40)) ? Blocks.STRIPPED_DARK_OAK_LOG
                            : (y % 8 == 7 ? Blocks.DARK_OAK_PLANKS : Blocks.STONE_BRICKS);
                    b.set(x, y, z, block);
                }
            }
        }
        // Pointed roof.
        for (int layer = 0; layer <= 4; layer++) {
            int r = 4 - layer;
            for (int x = -r; x <= r; x++) for (int z = 37 - r; z <= 37 + r; z++) b.set(x, 35 + layer, z, Blocks.DEEPSLATE_TILES);
        }
        b.set(0, 40, 37, Blocks.LIGHTNING_ROD);
        MechanismBlockEntity face = b.mechanism(0, 26, 33);
        face.kind(MechanismBlockEntity.CLOCK_FACE);
        face.radius = 2.9f;
        face.axis = MechanismBlockEntity.AXIS_Z;
        face.flip(true);
        face.color = MechanismBlockEntity.COLOR_DARK;
        face.offsetZ = 0.35f;
        face.sync();
    }
}
