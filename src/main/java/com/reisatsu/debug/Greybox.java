package com.reisatsu.debug;

import com.reisatsu.Reisatsu;
import com.reisatsu.world.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Test stand-in for the restaurant while the world's dimension {@code reisatsu:hidamari} does not exist yet (the
 * game branch before the world is merged): a grey box of the ground floor plan of docs/DESIGN.md §2 built in the
 * overworld at the layout's coordinates, so the camera, the spirits and the HUD can be tested in CI. Never used once
 * the dimension exists.
 */
public final class Greybox {
    private static final String[] PLAN = {
            "#########################################",
            "#ssssss#oooooo#ffff=kkkkkkkkkkkkkkkkkkkk#",
            "#ssssss#oooooo#ffff=kkkkkkkkkkkkkkkkkkkk#",
            "#ssssss#oooooo#ffff=kkkkkkkkkkkkkkkkkkkk#",
            "#ssssss#oooooo#==F==kkkkkkkkkkkkkkkkkkkk#",
            "#ssssss#oooooo#kkkkkkkkkkkkkkkkkkkkkkkkk#",
            "#ssssss#oooooo#kkkkkkkkkkkkkkkkkkkkkkkkk#",
            "###d###E#wwd###kkkkkkkkkkkkkkkkkkkkkkkkk#",
            "Bccccccccccccc#kkkkkkkkkkkkkkkkkkkkkkkkk#",
            "#cccccccccccccdkkkkkkkkkkkkkkkkkkkkkkkkk#",
            "###d##################PPPPP##SS##########",
            "#ttttt#xxxxxxx#.........................#",
            "#ttttt#xxxxxxx#.........................W",
            "#ttttt#xxxxxxx#.........................W",
            "#ttttt#xxxxxxx#.........................W",
            "#ttttt#xxxxxxx#.........................W",
            "#########MMM###.........................W",
            "#mmmmm#rrrrrrr#.........................W",
            "#mmmmm#rrrrrrr#.........................W",
            "#mmmmm#rrrrrrr#.........................W",
            "#mmmmm#rrrrrrr#.........................W",
            "#mmmmm#rrrrrrr#.........................W",
            "###d######d####.........................W",
            "#aaaaaaaaaaaaaa.........................W",
            "#aaaaaaaaaaaaaa.........................W",
            "#aaaaaaaaaaaaaa.........................W",
            "###AA####aaaaaa.........................W",
            "#vvvvvvv#aaaaaa.........................W",
            "#vvvvvvv#aaaaaa.........................W",
            "#vvvvvvv#aaaaaa.........................W",
            "###AA####WWWWWWWWWWWWWWWWWWWWWWWWWWWWWWW#",
    };
    private static final int Y = Layout.FEET_Y;

    private Greybox() {}

    /** Is the stand-in needed (the world's dimension is missing)? */
    public static boolean fallback(MinecraftServer server) {
        return server.getLevel(Layout.DIMENSION) == null;
    }

    /** Builds the box once (a marker block under the origin tells it is there). */
    public static void ensure(ServerLevel level) {
        if (level.dimension() == Layout.DIMENSION) return;
        BlockPos mark = new BlockPos(-12, Y - 2, -12);
        if (level.getBlockState(mark).is(Blocks.BEDROCK)) return;
        long t0 = System.currentTimeMillis();
        BlockState lot = Blocks.BLACK_CONCRETE.defaultBlockState();
        BlockState road = Blocks.GRAY_CONCRETE.defaultBlockState();
        for (int x = -12; x <= 96; x++) {
            for (int z = -12; z <= 62; z++) {
                for (int y = Y; y <= Y + 8; y++) set(level, x, y, z, Blocks.AIR.defaultBlockState());
                set(level, x, Y - 1, z, z >= 50 && z <= 58 ? road : lot);
                set(level, x, Y - 2, z, Blocks.STONE.defaultBlockState());
                boolean border = x == -12 || x == 96 || z == -12 || z == 62;
                if (border) set(level, x, Y, z, Blocks.BARRIER.defaultBlockState());
            }
        }
        for (int z = 0; z < PLAN.length; z++) {
            String row = PLAN[z];
            for (int x = 0; x < row.length(); x++) cell(level, x, z, row.charAt(x));
        }
        // ceiling and roof
        for (int x = 0; x <= 40; x++) for (int z = 0; z <= 30; z++) {
            set(level, x, Y + 4, z, Blocks.SMOOTH_STONE.defaultBlockState());
            set(level, x, Y + 5, z, Blocks.TERRACOTTA.defaultBlockState());
        }
        // tables: a slab top so a crouching player can hide, a look like the real ones
        for (int n = 1; n <= 17; n++) {
            if (!Layout.has("table_" + n)) continue;
            BlockPos t = Layout.marker("table_" + n).pos();
            set(level, t.getX(), Y, t.getZ(), Blocks.SPRUCE_SLAB.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
        }
        // dim pools of light (exit signs, the drink bar's glow)
        int[][] lights = {{20, 12}, {30, 20}, {36, 27}, {10, 24}, {4, 28}, {25, 5}, {10, 8}, {10, 19}, {3, 3}, {11, 3}, {5, 40}};
        for (int[] l : lights) set(level, l[0], Y + 3, l[1], Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 7));
        set(level, mark.getX(), mark.getY(), mark.getZ(), Blocks.BEDROCK.defaultBlockState());
        Reisatsu.LOGGER.info("[reisatsu] greybox stand-in built in {} ms (the hidamari dimension is missing)", System.currentTimeMillis() - t0);
    }

    private static void cell(ServerLevel level, int x, int z, char c) {
        BlockState floor = switch (c) {
            case 'k', 'f', 'c', 't' -> Blocks.TERRACOTTA.defaultBlockState();
            case 'm', 'r', 'x' -> Blocks.WHITE_CONCRETE.defaultBlockState();
            case 'v' -> Blocks.GRAY_CONCRETE.defaultBlockState();
            case 's', 'o' -> Blocks.SPRUCE_PLANKS.defaultBlockState();
            default -> Blocks.OAK_PLANKS.defaultBlockState();
        };
        set(level, x, Y - 1, z, floor);
        switch (c) {
            case '#', 'E' -> wall(level, x, z, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
            case '=' -> wall(level, x, z, Blocks.WHITE_TERRACOTTA.defaultBlockState());
            case 'W', 'w', 'M' -> {
                set(level, x, Y, z, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
                for (int y = Y + 1; y < Y + 4; y++) set(level, x, y, z, Blocks.GLASS.defaultBlockState());
            }
            case 'P' -> {
                set(level, x, Y, z, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
                set(level, x, Y + 3, z, Blocks.WHITE_TERRACOTTA.defaultBlockState());
            }
            case 'd', 'B', 'F' -> {
                Direction facing = z == 7 || z == 10 || z == 22 || c == 'F' ? Direction.SOUTH : Direction.EAST;
                BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing);
                set(level, x, Y, z, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                set(level, x, Y + 1, z, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                for (int y = Y + 2; y < Y + 4; y++) set(level, x, y, z, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
            }
            case 'A', 'S' -> {
                for (int y = Y + 3; y < Y + 4; y++) set(level, x, y, z, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
            }
            default -> {
            }
        }
    }

    private static void wall(ServerLevel level, int x, int z, BlockState s) {
        for (int y = Y; y < Y + 4; y++) set(level, x, y, z, s);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState s) {
        level.setBlock(new BlockPos(x, y, z), s, 2 | 16);
    }
}
