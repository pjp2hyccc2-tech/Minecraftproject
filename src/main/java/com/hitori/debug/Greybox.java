package com.hitori.debug;

import com.hitori.Hitori;
import com.hitori.world.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.HashMap;
import java.util.Map;

/**
 * Test stand-in for room 203 while the world's dimension {@code hitori:shirakaba} does not exist yet (the game branch
 * before the world is merged): a grey box of the floor plan of docs/DESIGN.md §2 in the overworld at the layout's
 * coordinates — rooms, doors, the 押し入れ with its fusuma, the tub, a table, a TV, lamps (light blocks) and blocks at
 * the item markers — so the ritual, the cameras, the doll and the HUD can be tested in CI. Never used once the
 * dimension exists. {@code game/Apt} drives its fixtures ({@link #show}).
 */
public final class Greybox {
    private static final String[] PLAN = {
            "##D#########",
            "#ggghh#t#bb#",
            "#ggghhdt#bb#",
            "####hh###d##",
            "#kkkhh#wwww#",
            "#kkkhhdwwww#",
            "#kkkhh######",
            "#kkkhhdyyyy#",
            "##ff#f#yyyy#",
            "#jjjjV#yyyy#",
            "#Ojjjj#yyyc#",
            "#Ojjjj#yyyc#",
            "#Ojjjj#yyyc#",
            "##WWW###WW##",
    };
    private static final int X0 = 22, Y = Layout.FEET_Y;
    private static final Map<String, Integer> STATE = new HashMap<>();

    private Greybox() {}

    /** Is the stand-in needed (the world's dimension is missing)? */
    public static boolean fallback(MinecraftServer server) {
        return server.getLevel(Layout.DIMENSION) == null;
    }

    /** Builds the box once (a marker block tells it is there). */
    public static void ensure(ServerLevel level) {
        if (level.dimension() == Layout.DIMENSION) return;
        BlockPos mark = new BlockPos(-6, Y - 3, -6);
        if (level.getBlockState(mark).is(Blocks.BEDROCK)) return;
        long t0 = System.currentTimeMillis();
        // clear the air around, a floor slab under the whole building, the corridor and the balcony
        for (int x = -4; x <= 47; x++) {
            for (int z = -6; z <= 18; z++) {
                for (int y = Y - 1; y <= Y + 5; y++) set(level, x, y, z, Blocks.AIR.defaultBlockState());
                set(level, x, Y - 2, z, Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        // the outdoor corridor (z -3..-1) along the building, its railing, the units' outer wall with their doors
        for (int x = 0; x <= 43; x++) {
            for (int z = -3; z <= -1; z++) set(level, x, Y - 1, z, Blocks.GRAY_CONCRETE.defaultBlockState());
            set(level, x, Y, -4, Blocks.IRON_BARS.defaultBlockState());
            if (x < X0 || x > X0 + 11) {
                for (int y = Y; y <= Y + 2; y++) set(level, x, y, 0, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
            }
            set(level, x, Y + 3, -1, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
            // the corridor's lamps, one by every other door
            if (x % 6 == 4) set(level, x, Y + 2, -1, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 11));
        }
        for (int unit = 0; unit < 4; unit++) {
            if (unit == 2) continue;
            int dx = unit * 11 + 2;
            door(level, dx, 0, Direction.NORTH, Blocks.IRON_DOOR.defaultBlockState());
        }
        for (int z = 0; z < PLAN.length; z++) {
            String row = PLAN[z];
            for (int i = 0; i < row.length(); i++) cell(level, X0 + i, z, row.charAt(i));
        }
        // the balcony
        for (int x = X0; x <= X0 + 11; x++) {
            set(level, x, Y - 1, 14, Blocks.GRAY_CONCRETE.defaultBlockState());
            set(level, x, Y - 1, 15, Blocks.GRAY_CONCRETE.defaultBlockState());
            set(level, x, Y, 16, Blocks.IRON_BARS.defaultBlockState());
        }
        // the closet: its frame, the futon shelf, the box
        for (int y = Y; y <= Y + 2; y++) set(level, X0 + 1, y, 9, Blocks.WHITE_TERRACOTTA.defaultBlockState());
        for (int z = 10; z <= 12; z++) {
            set(level, X0 + 1, Y + 2, z, Blocks.WHITE_WOOL.defaultBlockState());
            set(level, X0 + 2, Y + 2, z, Blocks.BIRCH_PLANKS.defaultBlockState());
        }
        // the ceiling
        for (int x = X0; x <= X0 + 11; x++) for (int z = 0; z <= 13; z++) set(level, x, Y + 3, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
        props(level);
        set(level, mark.getX(), mark.getY(), mark.getZ(), Blocks.BEDROCK.defaultBlockState());
        Hitori.LOGGER.info("[hitori] greybox stand-in for room 203 built in {} ms (the shirakaba dimension is missing)", System.currentTimeMillis() - t0);
    }

    private static void cell(ServerLevel level, int x, int z, char c) {
        BlockState floor = switch (c) {
            case 'j' -> Blocks.SMOOTH_SANDSTONE.defaultBlockState();
            case 'h', 'y' -> Blocks.DARK_OAK_PLANKS.defaultBlockState();
            case 'k', 'w' -> Blocks.BIRCH_PLANKS.defaultBlockState();
            case 't', 'b' -> Blocks.LIGHT_BLUE_TERRACOTTA.defaultBlockState();
            case 'g' -> Blocks.GRAY_TERRACOTTA.defaultBlockState();
            case 'O', 'c' -> Blocks.SPRUCE_PLANKS.defaultBlockState();
            default -> Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState();
        };
        set(level, x, Y - 1, z, floor);
        switch (c) {
            case '#' -> wall(level, x, z, z == 0 || z == 13 || x == X0 || x == X0 + 11 ? Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState()
                    : Blocks.WHITE_TERRACOTTA.defaultBlockState());
            case 'W' -> {
                set(level, x, Y + 2, z, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
                for (int y = Y; y < Y + 2; y++) set(level, x, y, z, Blocks.GLASS_PANE.defaultBlockState());
            }
            case 'd' -> {
                Direction f = z == 3 ? Direction.SOUTH : Direction.EAST;
                door(level, x, z, f, Blocks.SPRUCE_DOOR.defaultBlockState());
            }
            case 'D' -> door(level, x, z, Direction.NORTH, Blocks.IRON_DOOR.defaultBlockState());
            case 'f' -> set(level, x, Y + 2, z, Blocks.WHITE_TERRACOTTA.defaultBlockState());
            case 'V' -> {
                set(level, x, Y, z, Blocks.DARK_OAK_SLAB.defaultBlockState());
                set(level, x, Y + 1, z, Blocks.BLACK_CONCRETE.defaultBlockState());
            }
            default -> {
            }
        }
    }

    private static void props(ServerLevel level) {
        // the closet's fusuma (shut), the low table, the tub, the kitchen counter, the desk, the shelf, the camera bag
        show(level, "fusuma", 0);
        show(level, "bath", 0);
        put(level, "table", Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        for (int z = 4; z <= 7; z++) set(level, X0 + 1, Y, z, Blocks.SMOOTH_STONE.defaultBlockState());
        put(level, "kitchen_sink", Blocks.CAULDRON.defaultBlockState());
        put(level, "rice", Blocks.BARREL.defaultBlockState());
        put(level, "salt", Blocks.WHITE_CANDLE.defaultBlockState());
        put(level, "cup", Blocks.FLOWER_POT.defaultBlockState());
        put(level, "knife", Blocks.LIGHTNING_ROD.defaultBlockState());
        put(level, "lighter", Blocks.CHEST.defaultBlockState());
        for (int z = 8; z <= 9; z++) set(level, X0 + 10, Y, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
        put(level, "laptop", Blocks.GRAY_CARPET.defaultBlockState());
        put(level, "scissors", Blocks.RED_CARPET.defaultBlockState());
        put(level, "printout", Blocks.WHITE_CARPET.defaultBlockState());
        put(level, "plush_shelf", Blocks.WHITE_WOOL.defaultBlockState());
        put(level, "camera_bag", Blocks.BLACK_WOOL.defaultBlockState());
        put(level, "nail_clipper", Blocks.STONE_BUTTON.defaultBlockState());
        put(level, "washbasin", Blocks.CAULDRON.defaultBlockState());
        if (Layout.has("plush_shelf")) {
            BlockPos s = Layout.marker("plush_shelf").pos();
            set(level, s.getX(), s.getY() - 1, s.getZ(), Blocks.SPRUCE_PLANKS.defaultBlockState());
        }
    }

    private static void put(ServerLevel level, String marker, BlockState s) {
        if (!Layout.has(marker)) return;
        BlockPos p = Layout.marker(marker).pos();
        level.setBlock(p, s, 2 | 16);
    }

    /** The fixtures' look in the greybox (game/Apt): lamps, the TV's glow, the fusuma, the water in the tub. */
    public static void show(ServerLevel level, String key, int v) {
        if (level.dimension() == Layout.DIMENSION) return;
        STATE.put(key, v);
        boolean breaker = STATE.getOrDefault("breaker", 1) == 1;
        if (key.equals("breaker")) {
            for (String r : new String[]{"genkan", "hall", "toilet", "bath", "washroom", "dk", "tatami", "western"}) show(level, "light_" + r, STATE.getOrDefault("light_" + r, 0));
            show(level, "tv", STATE.getOrDefault("tv", 0));
            return;
        }
        if (key.startsWith("light_")) {
            String m = "lights_" + key.substring(6);
            if (!Layout.has(m)) return;
            for (Layout.Marker mk : Layout.markers(m)) {
                BlockPos p = mk.pos();
                if (!level.getBlockState(p).isAir() && !level.getBlockState(p).is(Blocks.LIGHT)) continue;
                level.setBlock(p, v == 1 && breaker ? Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 13) : Blocks.AIR.defaultBlockState(), 2 | 16);
            }
            return;
        }
        switch (key) {
            case "tv" -> {
                if (!Layout.has("tv")) return;
                BlockPos tv = Layout.marker("tv").pos();
                BlockPos glow = tv.relative(Layout.marker("tv").facing()).above();
                if (level.getBlockState(glow).isAir() || level.getBlockState(glow).is(Blocks.LIGHT)) {
                    level.setBlock(glow, v == 1 && breaker ? Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 8) : Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            }
            case "fusuma" -> {
                if (!Layout.has("closet_fusuma")) return;
                var list = Layout.markers("closet_fusuma");
                for (int i = 0; i < list.size(); i++) {
                    BlockPos p = list.get(i).pos();
                    // the crack (1): the middle panel open its whole height, a slit to look through
                    boolean open = v == 2 || (v == 1 && i == list.size() / 2);
                    BlockState s = open ? Blocks.AIR.defaultBlockState() : Blocks.WHITE_WOOL.defaultBlockState();
                    level.setBlock(p, s, 2 | 16);
                    level.setBlock(p.above(), s, 2 | 16);
                }
            }
            case "bath" -> {
                if (!Layout.has("bathtub")) return;
                for (Layout.Marker m : Layout.markers("bathtub")) {
                    BlockState s = v <= 0 ? Blocks.CAULDRON.defaultBlockState()
                            : Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, v == 1 ? 2 : 3);
                    level.setBlock(m.pos(), s, 2 | 16);
                }
            }
            default -> {
            }
        }
    }

    private static void door(ServerLevel level, int x, int z, Direction facing, BlockState door) {
        BlockState d = door.setValue(DoorBlock.FACING, facing);
        set(level, x, Y, z, d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(level, x, Y + 1, z, d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        set(level, x, Y + 2, z, Blocks.WHITE_TERRACOTTA.defaultBlockState());
    }

    private static void wall(ServerLevel level, int x, int z, BlockState s) {
        for (int y = Y; y < Y + 3; y++) set(level, x, y, z, s);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState s) {
        level.setBlock(new BlockPos(x, y, z), s, 2 | 16);
    }
}
