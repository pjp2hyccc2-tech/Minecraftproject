package com.frostbound.server;

import com.frostbound.block.GrateBlock;
import com.frostbound.core.city.Building;
import com.frostbound.core.city.BuildingType;
import com.frostbound.core.city.BuildingTypes;
import com.frostbound.core.city.City;
import com.frostbound.registry.ModRegistries;
import com.frostbound.world.Terrain;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Real block structures for the city: the generator and every building type, procedurally from vanilla
 * blocks plus the mod's structure blocks. Designs are made in local coordinates (width along x, depth
 * along z, the door in the front wall at z = depth - 1) and rotated into place. Blocks are written
 * without neighbour updates; connecting blocks (fences, panes, walls, stairs) are fixed up afterwards.
 */
public final class StructureBuilder {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState SNOW = Blocks.SNOW_BLOCK.defaultBlockState();
    private static final BlockState FOUNDATION = Blocks.COBBLED_DEEPSLATE.defaultBlockState();
    /** Blocks placed so far for each building under construction (transient). */
    private static final Map<Integer, Integer> placedCount = new HashMap<>();

    public record Placement(BlockPos pos, BlockState state) {
    }

    private StructureBuilder() {
    }

    // ================================================================== generator

    /** The generator core block: at the front (south face) of the boiler, one block above the floor. */
    public static BlockPos corePos(int gx, int gy, int gz) {
        return new BlockPos(gx, gy + 1, gz + 3);
    }

    private static List<BlockPos> gratePositions(int gx, int gy, int gz) {
        List<BlockPos> out = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            out.add(new BlockPos(gx + dx, gy, gz + 3));
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double r2 = dx * dx + dz * dz;
                if (r2 > 9.0 && r2 <= 12.96) {
                    out.add(new BlockPos(gx + dx, gy + 4, gz + dz));
                }
            }
        }
        return out;
    }

    public static List<Placement> generatorBlueprint(int gx, int gy, int gz) {
        LinkedHashMap<BlockPos, BlockState> map = new LinkedHashMap<>();
        for (Placement p : generatorParts(gx, gy, gz)) {
            map.put(p.pos(), p.state());
        }
        List<Placement> out = new ArrayList<>(map.size());
        map.forEach((pos, state) -> out.add(new Placement(pos, state)));
        return out;
    }

    private static List<Placement> generatorParts(int gx, int gy, int gz) {
        List<Placement> out = new ArrayList<>();
        BlockState casing = ModRegistries.GENERATOR_CASING.get().defaultBlockState();
        BlockState chimney = ModRegistries.CHIMNEY_BRICK.get().defaultBlockState();
        BlockState pipeY = ModRegistries.GENERATOR_PIPE.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        BlockState pipeX = ModRegistries.GENERATOR_PIPE.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        BlockState grate = ModRegistries.GENERATOR_GRATE.get().defaultBlockState();
        BlockState lamp = ModRegistries.FROST_LAMP.get().defaultBlockState();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                int r2 = dx * dx + dz * dz;
                if (r2 <= 45) {
                    out.add(new Placement(new BlockPos(gx + dx, gy - 1, gz + dz), r2 >= 30
                            ? Blocks.POLISHED_BLACKSTONE.defaultBlockState()
                            : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()));
                }
            }
        }
        for (int dy = 0; dy <= 8; dy++) {
            double r = dy <= 6 ? 3.6 : dy == 7 ? 3.0 : 2.2;
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (dx * dx + dz * dz <= r * r) {
                        out.add(new Placement(new BlockPos(gx + dx, gy + dy, gz + dz), casing));
                    }
                }
            }
        }
        for (BlockPos p : gratePositions(gx, gy, gz)) {
            out.add(new Placement(p, grate));
        }
        out.add(new Placement(corePos(gx, gy, gz), ModRegistries.GENERATOR_CORE.get().defaultBlockState()));
        for (int dy = 9; dy <= 40; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    out.add(new Placement(new BlockPos(gx + dx, gy + dy, gz + dz), dx == 0 && dz == 0 ? AIR : chimney));
                }
            }
        }
        for (int dy : new int[] {20, 38}) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                        out.add(new Placement(new BlockPos(gx + dx, gy + dy, gz + dz), chimney));
                    }
                }
            }
        }
        for (int side : new int[] {-1, 1}) {
            for (int dy = 0; dy <= 13; dy++) {
                out.add(new Placement(new BlockPos(gx + side * 6, gy + dy, gz - 3), pipeY));
            }
            for (int d = 2; d <= 5; d++) {
                out.add(new Placement(new BlockPos(gx + side * d, gy + 2, gz - 3), pipeX));
            }
            out.add(new Placement(new BlockPos(gx + side * 4, gy, gz + 4), Blocks.COAL_BLOCK.defaultBlockState()));
            out.add(new Placement(new BlockPos(gx + side * 4, gy, gz + 5), Blocks.COAL_BLOCK.defaultBlockState()));
            out.add(new Placement(new BlockPos(gx + side * 3, gy, gz + 5), Blocks.COAL_BLOCK.defaultBlockState()));
            out.add(new Placement(new BlockPos(gx + side * 4, gy + 1, gz + 5), Blocks.COAL_BLOCK.defaultBlockState()));
        }
        for (int[] c : new int[][] {{-4, -4}, {4, -4}}) {
            out.add(new Placement(new BlockPos(gx + c[0], gy, gz + c[1]), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState()));
            out.add(new Placement(new BlockPos(gx + c[0], gy + 1, gz + c[1]), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState()));
            out.add(new Placement(new BlockPos(gx + c[0], gy + 2, gz + c[1]), lamp));
        }
        for (int dx : new int[] {-2, 2}) {
            out.add(new Placement(new BlockPos(gx + dx, gy, gz + 5), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState()));
            out.add(new Placement(new BlockPos(gx + dx, gy + 1, gz + 5), lamp));
        }
        return out;
    }

    public static void buildGenerator(ServerLevel level, int gx, int gy, int gz) {
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                int top = Math.max(gy + 42, Terrain.height(gx + dx, gz + dz) + 2);
                for (int y = gy; y <= top; y++) {
                    set(level, new BlockPos(gx + dx, y, gz + dz), AIR);
                }
                for (int y = Terrain.height(gx + dx, gz + dz) + 1; y < gy - 1; y++) {
                    set(level, new BlockPos(gx + dx, y, gz + dz), FOUNDATION);
                }
            }
        }
        List<Placement> bp = generatorBlueprint(gx, gy, gz);
        for (Placement p : bp) {
            set(level, p.pos(), p.state());
        }
        fixShapes(level, bp);
    }

    /** Lights or darkens the grates (only when loaded). */
    public static void setGratesLit(ServerLevel level, int gx, int gy, int gz, boolean lit) {
        for (BlockPos p : gratePositions(gx, gy, gz)) {
            if (!level.isLoaded(p)) {
                return;
            }
            BlockState s = level.getBlockState(p);
            if (s.is(ModRegistries.GENERATOR_GRATE.get()) && s.getValue(GrateBlock.LIT) != lit) {
                level.setBlock(p, s.setValue(GrateBlock.LIT, lit), FLAGS);
            }
        }
    }

    // ================================================================== buildings

    /** Local design canvas: width {@code w} (x) by depth {@code d} (z); y is relative to the floor. */
    private static final class Canvas {
        final int w;
        final int d;
        final LinkedHashMap<Long, BlockState> cells = new LinkedHashMap<>();
        int coreX;
        int coreY = 1;
        int coreZ;

        Canvas(int w, int d) {
            this.w = w;
            this.d = d;
            this.coreX = Math.min(w - 1, w / 2 + 1);
            this.coreZ = d - 1;
            if (w < 5) {
                coreY = 0;
            }
        }

        static long key(int x, int y, int z) {
            return ((long) (y + 512) << 32) | ((long) (x + 1024) << 16) | (z + 1024);
        }

        void set(int x, int y, int z, BlockState s) {
            if (x < 0 || z < 0 || x >= w || z >= d) {
                return;
            }
            cells.put(key(x, y, z), s);
        }

        BlockState get(int x, int y, int z) {
            return cells.get(key(x, y, z));
        }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        set(x, y, z, s);
                    }
                }
            }
        }

        boolean edge(int x, int z) {
            return x == 0 || z == 0 || x == w - 1 || z == d - 1;
        }

        boolean corner(int x, int z) {
            return (x == 0 || x == w - 1) && (z == 0 || z == d - 1);
        }

        void core(int x, int y, int z) {
            coreX = x;
            coreY = y;
            coreZ = z;
        }
    }

    private record Style(BlockState wall, BlockState post, BlockState floor, BlockState stair, BlockState ridge,
            BlockState window) {
        Style withWall(BlockState w) {
            return new Style(w, post, floor, stair, ridge, window);
        }
    }

    private static BlockState st(Block b) {
        return b.defaultBlockState();
    }

    private static BlockState stair(Block b, Direction facing) {
        return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM);
    }

    private static BlockState slab(Block b) {
        return b.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static BlockState log(Block b, Direction.Axis axis) {
        return b.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    private static BlockState lantern(boolean hanging) {
        return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, hanging);
    }

    private static BlockState campfire() {
        return Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false);
    }

    /** Floor, perimeter walls up to {@code top - 1}, corner posts, windows and a door in the front wall. */
    private static void box(Canvas c, int top, Style s, boolean windows) {
        c.fill(0, -1, 0, c.w - 1, -1, c.d - 1, s.floor());
        for (int y = 0; y < top; y++) {
            for (int x = 0; x < c.w; x++) {
                for (int z = 0; z < c.d; z++) {
                    if (c.edge(x, z)) {
                        c.set(x, y, z, c.corner(x, z) ? s.post() : s.wall());
                    }
                }
            }
        }
        if (windows) {
            for (int x = 2; x < c.w - 2; x += 2) {
                c.set(x, 1, 0, s.window());
            }
            for (int z = 2; z < c.d - 1; z += 2) {
                c.set(0, 1, z, s.window());
                c.set(c.w - 1, 1, z, s.window());
            }
            if (c.w >= 7) {
                c.set(1, 1, c.d - 1, s.window());
                c.set(c.w - 2, 1, c.d - 1, s.window());
            }
        }
        door(c, c.w / 2, Blocks.SPRUCE_DOOR);
    }

    private static void door(Canvas c, int x, Block door) {
        c.set(x, 0, c.d - 1, door.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        c.set(x, 1, c.d - 1, door.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** Gable roof with the ridge along z, starting at height {@code y0}; returns the ridge height. */
    private static int gable(Canvas c, int y0, Style s) {
        int rows = (c.w + 1) / 2;
        for (int r = 0; r < rows; r++) {
            int y = y0 + r;
            int left = r;
            int right = c.w - 1 - r;
            for (int z = 0; z < c.d; z++) {
                if (left == right) {
                    c.set(left, y, z, s.ridge());
                } else {
                    c.set(left, y, z, s.stair().setValue(StairBlock.FACING, Direction.EAST));
                    c.set(right, y, z, s.stair().setValue(StairBlock.FACING, Direction.WEST));
                }
            }
            for (int x = left + 1; x < right; x++) {
                c.set(x, y, 0, s.wall());
                c.set(x, y, c.d - 1, s.wall());
            }
        }
        return y0 + rows - 1;
    }

    /** Flat roof at {@code y} with a low parapet. */
    private static void flat(Canvas c, int y, BlockState roof, BlockState parapet) {
        c.fill(0, y, 0, c.w - 1, y, c.d - 1, roof);
        if (parapet != null) {
            for (int x = 0; x < c.w; x++) {
                for (int z = 0; z < c.d; z++) {
                    if (c.edge(x, z)) {
                        c.set(x, y + 1, z, parapet);
                    }
                }
            }
        }
    }

    private static void chimney(Canvas c, int x, int z, int top, BlockState brick) {
        for (int y = 0; y <= top; y++) {
            c.set(x, y, z, brick);
        }
        c.set(x, top + 1, z, campfire());
    }

    private static void cross(Canvas c, int cx, int cy, int z, BlockState red) {
        c.set(cx, cy, z, red);
        c.set(cx - 1, cy, z, red);
        c.set(cx + 1, cy, z, red);
        c.set(cx, cy + 1, z, red);
        c.set(cx, cy - 1, z, red);
    }

    private static Style style(String id) {
        return switch (id) {
            case "shelter", "hunters_lodge", "fishery" -> new Style(st(Blocks.SPRUCE_PLANKS), log(Blocks.SPRUCE_LOG, Direction.Axis.Y),
                    st(Blocks.SPRUCE_PLANKS), stair(Blocks.SPRUCE_STAIRS, Direction.EAST), st(Blocks.SPRUCE_PLANKS), st(Blocks.GLASS_PANE));
            case "wooden_house", "meeting_hall", "warehouse" -> new Style(st(Blocks.SPRUCE_PLANKS),
                    log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y), st(Blocks.SPRUCE_PLANKS), stair(Blocks.DARK_OAK_STAIRS, Direction.EAST),
                    st(Blocks.DARK_OAK_PLANKS), st(Blocks.GLASS_PANE));
            case "heated_house" -> new Style(st(Blocks.STONE_BRICKS), st(Blocks.POLISHED_DEEPSLATE), st(Blocks.SPRUCE_PLANKS),
                    stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.EAST), st(Blocks.DEEPSLATE_TILES), st(Blocks.GLASS_PANE));
            case "apartment" -> new Style(st(Blocks.BRICKS), st(Blocks.STONE_BRICKS), st(Blocks.SPRUCE_PLANKS),
                    stair(Blocks.BRICK_STAIRS, Direction.EAST), st(Blocks.BRICKS), st(Blocks.GLASS_PANE));
            case "food_processor", "workshop" -> new Style(st(Blocks.STONE_BRICKS), log(Blocks.SPRUCE_LOG, Direction.Axis.Y),
                    st(Blocks.STONE_BRICKS), stair(Blocks.SPRUCE_STAIRS, Direction.EAST), st(Blocks.SPRUCE_PLANKS), st(Blocks.GLASS_PANE));
            case "clinic", "hospital", "advanced_lab" -> new Style(st(Blocks.WHITE_CONCRETE), st(Blocks.QUARTZ_PILLAR),
                    st(Blocks.SMOOTH_QUARTZ), stair(Blocks.DARK_OAK_STAIRS, Direction.EAST), st(Blocks.DARK_OAK_PLANKS), st(Blocks.GLASS_PANE));
            case "steelworks", "machine_factory" -> new Style(st(Blocks.DEEPSLATE_BRICKS), st(Blocks.POLISHED_BLACKSTONE),
                    st(Blocks.DEEPSLATE_TILES), stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.EAST), st(Blocks.DEEPSLATE_BRICKS),
                    st(Blocks.IRON_BARS));
            case "steam_engine_factory", "core_extractor" -> new Style(st(Blocks.WAXED_CUT_COPPER), st(Blocks.DEEPSLATE_BRICKS),
                    st(Blocks.DEEPSLATE_TILES), stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.EAST), st(Blocks.DEEPSLATE_BRICKS),
                    st(Blocks.IRON_BARS));
            case "research_lab" -> new Style(st(Blocks.BRICKS), st(Blocks.STONE_BRICKS), st(Blocks.SPRUCE_PLANKS),
                    stair(Blocks.BRICK_STAIRS, Direction.EAST), st(Blocks.BRICKS), st(Blocks.GLASS_PANE));
            case "tavern" -> new Style(st(Blocks.DARK_OAK_PLANKS), log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y),
                    st(Blocks.SPRUCE_PLANKS), stair(Blocks.SPRUCE_STAIRS, Direction.EAST), st(Blocks.SPRUCE_PLANKS), st(Blocks.GLASS_PANE));
            case "school" -> new Style(st(Blocks.BIRCH_PLANKS), log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y), st(Blocks.SPRUCE_PLANKS),
                    stair(Blocks.SPRUCE_STAIRS, Direction.EAST), st(Blocks.SPRUCE_PLANKS), st(Blocks.GLASS_PANE));
            case "chapel", "guard_post" -> new Style(st(Blocks.STONE_BRICKS), st(Blocks.POLISHED_ANDESITE), st(Blocks.STONE_BRICKS),
                    stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.EAST), st(Blocks.DEEPSLATE_TILES), st(Blocks.GLASS_PANE));
            default -> new Style(st(Blocks.SPRUCE_PLANKS), log(Blocks.SPRUCE_LOG, Direction.Axis.Y), st(Blocks.SPRUCE_PLANKS),
                    stair(Blocks.SPRUCE_STAIRS, Direction.EAST), st(Blocks.SPRUCE_PLANKS), st(Blocks.GLASS_PANE));
        };
    }

    private static Canvas design(BuildingType t, int level) {
        Canvas c = new Canvas(t.width, t.depth);
        Style s = style(t.id);
        int w = c.w;
        int d = c.d;
        int cx = w / 2;
        BlockState lamp = ModRegistries.FROST_LAMP.get().defaultBlockState();
        int wallTop = 3;
        switch (t.id) {
            case "tent", "medical_tent" -> {
                BlockState cloth = st(Blocks.WHITE_WOOL);
                BlockState trim = st(Blocks.LIGHT_GRAY_WOOL);
                int h = t.height;
                int half = (w - 1) / 2;
                c.fill(0, -1, 0, w - 1, -1, d - 1, st(Blocks.SPRUCE_PLANKS));
                for (int y = 0; y < h; y++) {
                    int inset = Math.max(0, half - (h - 1 - y));
                    for (int z = 0; z < d; z++) {
                        c.set(inset, y, z, z == 0 || z == d - 1 ? trim : cloth);
                        c.set(w - 1 - inset, y, z, z == 0 || z == d - 1 ? trim : cloth);
                    }
                    for (int x = inset + 1; x < w - 1 - inset; x++) {
                        c.set(x, y, 0, cloth);
                        if (y >= 2) {
                            c.set(x, y, d - 1, cloth);
                        }
                    }
                }
                if (t.id.equals("medical_tent")) {
                    c.set(cx, 2, d - 1, st(Blocks.RED_WOOL));
                    c.set(cx - 1, 2, d - 1, st(Blocks.RED_WOOL));
                    c.set(cx + 1, 2, d - 1, st(Blocks.RED_WOOL));
                    c.set(cx, 3, d - 1, st(Blocks.RED_WOOL));
                    c.set(1, 0, 1, st(Blocks.WHITE_CARPET));
                    c.set(w - 2, 0, 1, st(Blocks.WHITE_CARPET));
                    c.set(cx, 0, 1, st(Blocks.BREWING_STAND));
                } else {
                    c.set(cx, 0, 0, st(Blocks.BARREL));
                }
                c.set(cx, h - 2, d / 2, lantern(true));
                c.set(cx, h - 1, d / 2, cloth);
                c.core(w - 1, 0, d - 1);
            }
            case "shelter", "hunters_lodge", "fishery" -> {
                box(c, wallTop, s, true);
                gable(c, wallTop, s);
                c.set(1, 0, 1, st(Blocks.BARREL));
                c.set(w - 2, 0, 1, lantern(false));
                if (t.id.equals("hunters_lodge")) {
                    c.set(cx, 0, 1, st(Blocks.SMOKER));
                } else if (t.id.equals("fishery")) {
                    c.set(cx, 0, 1, st(Blocks.CAULDRON));
                    c.set(1, 0, d - 2, st(Blocks.PACKED_ICE));
                }
            }
            case "wooden_house", "tavern", "school" -> {
                box(c, wallTop, s, true);
                int ridge = gable(c, wallTop, s);
                chimney(c, w - 2, 1, ridge, st(Blocks.STONE_BRICKS));
                c.set(1, 0, 1, st(Blocks.BARREL));
                c.set(1, 0, d - 2, lantern(false));
                if (t.id.equals("tavern")) {
                    c.set(cx, 0, 2, st(Blocks.BARREL));
                    c.set(cx - 1, 0, 2, st(Blocks.BARREL));
                    c.set(1, 1, 1, st(Blocks.BARREL));
                } else if (t.id.equals("school")) {
                    c.set(cx, 0, 1, st(Blocks.LECTERN));
                    c.fill(1, 0, 2, 1, 1, d - 3, st(Blocks.BOOKSHELF));
                }
            }
            case "heated_house" -> {
                box(c, wallTop, s, true);
                int ridge = gable(c, wallTop, s);
                chimney(c, w - 2, 1, ridge, st(Blocks.BRICKS));
                for (int x = 1; x < w - 1; x++) {
                    c.set(x, 2, d - 1, log(ModRegistries.GENERATOR_PIPE.get(), Direction.Axis.X));
                }
                c.set(1, 0, 1, st(Blocks.FURNACE));
                c.set(1, 0, d - 2, lantern(false));
            }
            case "apartment" -> {
                int floors = 2;
                int top = floors * 4;
                box(c, top, s, false);
                for (int f = 0; f < floors; f++) {
                    int y = f * 4;
                    if (f > 0) {
                        c.fill(1, y - 1, 1, w - 2, y - 1, d - 2, st(Blocks.SPRUCE_PLANKS));
                    }
                    for (int x = 1; x < w - 1; x += 2) {
                        c.set(x, y + 1, 0, s.window());
                        c.set(x, y + 1, d - 1, f == 0 && x == cx ? c.get(x, y + 1, d - 1) : s.window());
                    }
                    for (int z = 1; z < d - 1; z += 2) {
                        c.set(0, y + 1, z, s.window());
                        c.set(w - 1, y + 1, z, s.window());
                    }
                    c.set(w - 2, y, 2, lantern(false));
                    c.set(w - 2, y, 1, st(Blocks.BARREL));
                }
                flat(c, top, slab(Blocks.STONE_BRICK_SLAB), st(Blocks.STONE_BRICK_WALL));
                chimney(c, 1, 1, top + 1, st(Blocks.BRICKS));
                door(c, cx, Blocks.SPRUCE_DOOR);
            }
            case "coal_pit" -> {
                BlockState rim = st(Blocks.COBBLED_DEEPSLATE);
                c.fill(0, -1, 0, w - 1, -1, d - 1, rim);
                for (int x = 0; x < w; x++) {
                    for (int z = 0; z < d; z++) {
                        if (c.edge(x, z)) {
                            c.set(x, 0, z, rim);
                        }
                    }
                }
                c.fill(1, -3, 1, w - 2, -3, d - 2, st(Blocks.STONE));
                c.fill(1, -2, 1, w - 2, -1, d - 2, AIR);
                c.set(2, -3, 2, st(Blocks.COAL_ORE));
                c.set(w - 3, -3, 3, st(Blocks.COAL_ORE));
                c.set(cx, -3, d - 3, st(Blocks.COAL_ORE));
                c.set(2, -2, d - 3, st(Blocks.COAL_BLOCK));
                for (int[] p : new int[][] {{1, 1}, {w - 2, 1}, {1, d - 2}, {w - 2, d - 2}}) {
                    c.fill(p[0], 0, p[1], p[0], 3, p[1], log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
                }
                for (int x = 1; x < w - 1; x++) {
                    c.set(x, 4, 1, log(Blocks.SPRUCE_LOG, Direction.Axis.X));
                    c.set(x, 4, d - 2, log(Blocks.SPRUCE_LOG, Direction.Axis.X));
                }
                for (int z = 2; z < d - 2; z++) {
                    c.set(cx, 4, z, log(Blocks.SPRUCE_LOG, Direction.Axis.Z));
                }
                for (int y = 1; y <= 3; y++) {
                    c.set(cx, y, d / 2, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
                }
                c.set(cx, 0, d / 2, lantern(true));
                c.set(cx, 0, d - 1, AIR);
                c.core(cx + 1, 1, d - 1);
                c.set(cx + 1, 0, d - 1, rim);
            }
            case "sawmill" -> {
                c.fill(0, -1, 0, w - 1, -1, d - 1, s.floor());
                for (int x = 0; x < w; x += w - 1) {
                    for (int z = 0; z < d; z += d - 1) {
                        c.fill(x, 0, z, x, 3, z, s.post());
                    }
                }
                c.fill(cx, 0, 0, cx, 3, 0, s.post());
                flat(c, 4, slab(Blocks.SPRUCE_SLAB), null);
                c.fill(1, 0, 1, 2, 1, 1, log(Blocks.SPRUCE_LOG, Direction.Axis.X));
                c.fill(w - 3, 0, 1, w - 2, 0, 1, log(Blocks.SPRUCE_LOG, Direction.Axis.X));
                c.set(cx, 0, d / 2, st(Blocks.STONECUTTER));
                c.set(1, 0, d - 2, st(Blocks.CRAFTING_TABLE));
                c.set(cx, 3, d / 2, lantern(true));
                c.core(w - 1, 1, d - 1);
            }
            case "mine" -> {
                box(c, 4, s.withWall(st(Blocks.STONE_BRICKS)), false);
                flat(c, 4, slab(Blocks.STONE_BRICK_SLAB), st(Blocks.COBBLESTONE_WALL));
                c.fill(cx - 1, 0, d - 1, cx + 1, 2, d - 1, AIR);
                c.fill(cx - 2, 3, d - 1, cx + 2, 3, d - 1, log(Blocks.SPRUCE_LOG, Direction.Axis.X));
                c.fill(cx - 1, -1, 1, cx + 1, -1, d - 1, st(Blocks.GRAVEL));
                for (int z = 3; z < d - 1; z++) {
                    c.set(cx, 0, z, st(Blocks.RAIL));
                }
                c.fill(cx - 1, -3, 1, cx + 1, -1, 2, AIR);
                c.set(1, 0, 1, st(Blocks.IRON_ORE));
                c.set(w - 2, 0, 1, st(Blocks.COAL_ORE));
                c.set(1, 3, d - 2, lantern(true));
                c.core(cx + 2, 1, d - 1);
            }
            case "warehouse", "meeting_hall" -> {
                box(c, wallTop + 1, s, true);
                gable(c, wallTop + 1, s);
                if (t.id.equals("warehouse")) {
                    c.fill(cx - 1, 0, d - 1, cx + 1, 2, d - 1, AIR);
                    c.core(cx + 2, 1, d - 1);
                    c.fill(1, 0, 1, w - 2, 1, 1, st(Blocks.BARREL));
                    c.fill(1, 0, 2, 1, 0, d - 3, st(Blocks.BARREL));
                    c.set(w - 2, 0, 2, st(Blocks.HAY_BLOCK));
                } else {
                    for (int z = 2; z < d - 2; z += 2) {
                        c.fill(1, 0, z, cx - 1, 0, z, stair(Blocks.SPRUCE_STAIRS, Direction.NORTH));
                        c.fill(cx + 1, 0, z, w - 2, 0, z, stair(Blocks.SPRUCE_STAIRS, Direction.NORTH));
                    }
                    c.set(cx, 0, 1, st(Blocks.LECTERN));
                }
                c.set(1, 0, d - 2, lantern(false));
                c.set(w - 2, 0, d - 2, lantern(false));
            }
            case "food_processor", "workshop" -> {
                box(c, wallTop, s, true);
                int ridge = gable(c, wallTop, s);
                if (t.id.equals("food_processor")) {
                    chimney(c, w - 2, 1, ridge, st(Blocks.BRICKS));
                    c.set(1, 0, 1, st(Blocks.SMOKER));
                    c.set(2, 0, 1, st(Blocks.FURNACE));
                    c.set(cx, 0, 1, st(Blocks.BARREL));
                } else {
                    c.set(1, 0, 1, st(Blocks.CRAFTING_TABLE));
                    c.set(2, 0, 1, st(Blocks.ANVIL));
                    c.set(cx + 1, 0, 1, st(Blocks.GRINDSTONE));
                    c.set(w - 2, 0, 2, st(Blocks.SMITHING_TABLE));
                }
                c.set(1, 0, d - 2, lantern(false));
            }
            case "clinic" -> {
                box(c, wallTop, s, true);
                gable(c, wallTop, s);
                cross(c, cx, 4, d - 1, st(Blocks.RED_CONCRETE));
                c.set(1, 0, 1, st(Blocks.BREWING_STAND));
                c.set(2, 0, 1, st(Blocks.CAULDRON));
                c.fill(w - 2, 0, 1, w - 2, 0, d - 3, st(Blocks.WHITE_CARPET));
                c.set(1, 0, d - 2, lantern(false));
            }
            case "hospital", "advanced_lab" -> {
                int top = t.height - 1;
                box(c, top, s, false);
                for (int y = 1; y < top - 1; y += 3) {
                    for (int x = 1; x < w - 1; x += 2) {
                        c.set(x, y, 0, s.window());
                    }
                    for (int z = 1; z < d - 1; z += 2) {
                        c.set(0, y, z, s.window());
                        c.set(w - 1, y, z, s.window());
                    }
                }
                flat(c, top, slab(Blocks.SMOOTH_QUARTZ_SLAB), st(Blocks.IRON_BARS));
                door(c, cx, Blocks.IRON_DOOR);
                if (t.id.equals("hospital")) {
                    cross(c, cx, top - 2, d - 1, st(Blocks.RED_CONCRETE));
                    c.fill(1, 0, 1, w - 2, 0, 1, st(Blocks.WHITE_CARPET));
                    c.set(cx, 0, 2, st(Blocks.BREWING_STAND));
                } else {
                    c.set(1, 0, 1, st(Blocks.BREWING_STAND));
                    c.set(2, 0, 1, st(Blocks.BREWING_STAND));
                    c.set(w - 2, 0, 1, st(Blocks.WAXED_COPPER_BLOCK));
                    c.set(cx, top + 1, d / 2, st(Blocks.LIGHTNING_ROD));
                }
                c.set(1, 0, d - 2, lantern(false));
                c.set(w - 2, 0, d - 2, lantern(false));
            }
            case "steelworks", "machine_factory", "steam_engine_factory", "core_extractor" -> {
                int top = Math.max(4, t.height - 2);
                box(c, top, s, false);
                for (int x = 2; x < w - 2; x += 2) {
                    c.set(x, 2, 0, s.window());
                }
                for (int z = 2; z < d - 1; z += 2) {
                    c.set(0, 2, z, s.window());
                    c.set(w - 1, 2, z, s.window());
                }
                flat(c, top, slab(Blocks.DEEPSLATE_BRICK_SLAB), st(Blocks.POLISHED_BLACKSTONE_WALL));
                c.fill(cx - 1, 0, d - 1, cx + 1, 2, d - 1, AIR);
                c.fill(cx - 1, 3, d - 1, cx + 1, 3, d - 1, st(Blocks.IRON_BLOCK));
                c.core(cx + 2, 1, d - 1);
                switch (t.id) {
                    case "steelworks" -> {
                        chimney(c, w - 2, 1, top + 3, st(Blocks.BRICKS));
                        c.set(1, 0, 1, st(Blocks.BLAST_FURNACE));
                        c.set(2, 0, 1, st(Blocks.BLAST_FURNACE));
                        c.set(cx, 0, 2, st(Blocks.ANVIL));
                        c.set(1, 0, 3, st(Blocks.IRON_BLOCK));
                    }
                    case "machine_factory" -> {
                        chimney(c, w - 2, 1, top + 3, st(Blocks.BRICKS));
                        c.fill(1, 0, 1, 3, 0, 1, st(Blocks.PISTON));
                        c.set(cx, 0, 2, st(Blocks.ANVIL));
                        c.set(w - 3, 0, 1, st(Blocks.CRAFTING_TABLE));
                    }
                    case "steam_engine_factory" -> {
                        chimney(c, 1, 1, top + 3, st(Blocks.BRICKS));
                        chimney(c, w - 2, 1, top + 3, st(Blocks.BRICKS));
                        for (int x = 2; x < w - 2; x++) {
                            c.set(x, 1, 1, log(ModRegistries.GENERATOR_PIPE.get(), Direction.Axis.X));
                        }
                        c.set(cx, 0, 2, st(Blocks.BLAST_FURNACE));
                    }
                    default -> {
                        for (int y = top + 1; y <= top + 4; y++) {
                            c.set(cx, y, d / 2, st(Blocks.IRON_BARS));
                        }
                        c.set(cx, top + 5, d / 2, st(Blocks.LIGHTNING_ROD));
                        c.fill(cx - 1, -2, d / 2 - 1, cx + 1, -1, d / 2 + 1, st(Blocks.WAXED_CUT_COPPER));
                        c.set(cx, 0, d / 2, st(Blocks.WAXED_COPPER_BLOCK));
                        c.set(cx, 1, d / 2, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
                    }
                }
                c.set(1, 0, d - 2, lantern(false));
                c.set(w - 2, 0, d - 2, lantern(false));
            }
            case "research_lab" -> {
                box(c, wallTop + 1, s, true);
                int ridge = gable(c, wallTop + 1, s);
                c.set(cx, ridge + 1, d / 2, st(Blocks.LIGHTNING_ROD));
                c.set(cx, 0, 1, st(Blocks.LECTERN));
                c.fill(1, 0, 1, 1, 2, 3, st(Blocks.BOOKSHELF));
                c.fill(w - 2, 0, 1, w - 2, 2, 3, st(Blocks.BOOKSHELF));
                c.set(cx + 1, 0, 2, st(Blocks.BREWING_STAND));
                c.set(1, 0, d - 2, lantern(false));
            }
            case "chapel" -> {
                box(c, 5, s, false);
                for (int z = 2; z < d - 2; z += 2) {
                    c.set(0, 2, z, st(Blocks.BLUE_STAINED_GLASS_PANE));
                    c.set(0, 3, z, st(Blocks.BLUE_STAINED_GLASS_PANE));
                    c.set(w - 1, 2, z, st(Blocks.BLUE_STAINED_GLASS_PANE));
                    c.set(w - 1, 3, z, st(Blocks.BLUE_STAINED_GLASS_PANE));
                }
                int ridge = gable(c, 5, s);
                for (int y = 0; y <= ridge + 2; y++) {
                    c.set(cx - 1, y, d - 1, s.post());
                    c.set(cx + 1, y, d - 1, s.post());
                }
                c.set(cx, ridge + 1, d - 1, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING)
                        .setValue(BellBlock.FACING, Direction.SOUTH));
                c.set(cx, ridge + 2, d - 1, s.ridge());
                c.set(cx, ridge + 3, d - 1, st(Blocks.LIGHTNING_ROD));
                c.fill(cx - 1, ridge, d - 1, cx + 1, ridge, d - 1, s.wall());
                c.set(cx, 2, d - 1, AIR);
                for (int z = 2; z < d - 2; z += 2) {
                    c.fill(1, 0, z, cx - 1, 0, z, stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
                    c.fill(cx + 1, 0, z, w - 2, 0, z, stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
                }
                c.set(cx, 0, 1, st(Blocks.LECTERN));
                c.set(1, 0, 1, lantern(false));
                c.set(w - 2, 0, 1, lantern(false));
                c.core(cx + 2, 1, d - 1);
            }
            case "guard_post" -> {
                c.fill(0, -1, 0, w - 1, -1, d - 1, s.floor());
                for (int y = 0; y < 3; y++) {
                    for (int x = 0; x < w; x++) {
                        for (int z = 0; z < d; z++) {
                            if (c.edge(x, z)) {
                                c.set(x, y, z, c.corner(x, z) ? s.post() : st(Blocks.COBBLESTONE));
                            }
                        }
                    }
                }
                door(c, cx, Blocks.SPRUCE_DOOR);
                c.fill(0, 3, 0, w - 1, 3, d - 1, st(Blocks.SPRUCE_PLANKS));
                for (int x = 0; x < w; x++) {
                    for (int z = 0; z < d; z++) {
                        if (c.edge(x, z)) {
                            c.set(x, 4, z, c.corner(x, z) ? log(Blocks.SPRUCE_LOG, Direction.Axis.Y) : st(Blocks.SPRUCE_FENCE));
                        }
                    }
                }
                for (int[] p : new int[][] {{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}}) {
                    c.set(p[0], 5, p[1], log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
                }
                flat(c, 6, slab(Blocks.SPRUCE_SLAB), null);
                c.set(cx, 5, d / 2, lantern(true));
                c.set(1, 0, 1, st(Blocks.BARREL));
                c.core(cx + 1, 1, d - 1);
            }
            case "greenhouse" -> {
                c.fill(0, -1, 0, w - 1, -1, d - 1, st(Blocks.MOSS_BLOCK));
                c.fill(cx, -1, 1, cx, -1, d - 1, st(Blocks.SPRUCE_PLANKS));
                for (int y = 0; y < 3; y++) {
                    for (int x = 0; x < w; x++) {
                        for (int z = 0; z < d; z++) {
                            if (c.edge(x, z)) {
                                c.set(x, y, z, c.corner(x, z) || (x == cx && z == 0) ? log(Blocks.SPRUCE_LOG, Direction.Axis.Y)
                                        : y == 0 ? st(Blocks.SPRUCE_PLANKS) : st(Blocks.GLASS));
                            }
                        }
                    }
                }
                int rows = (w + 1) / 2;
                for (int r = 0; r < rows; r++) {
                    for (int z = 0; z < d; z++) {
                        c.set(r, 3 + r, z, st(Blocks.GLASS));
                        c.set(w - 1 - r, 3 + r, z, st(Blocks.GLASS));
                    }
                    for (int x = r + 1; x < w - 1 - r; x++) {
                        c.set(x, 3 + r, 0, st(Blocks.GLASS));
                        c.set(x, 3 + r, d - 1, st(Blocks.GLASS));
                    }
                }
                door(c, cx, Blocks.SPRUCE_DOOR);
                BlockState leaves = Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                for (int z = 1; z < d - 2; z++) {
                    c.set(1, 0, z, z % 2 == 0 ? leaves : st(Blocks.MOSS_CARPET));
                    c.set(w - 2, 0, z, z % 2 == 1 ? leaves : st(Blocks.MOSS_CARPET));
                    c.set(2, 0, z, st(Blocks.MOSS_CARPET));
                    c.set(w - 3, 0, z, st(Blocks.MOSS_CARPET));
                }
                c.set(cx, 2 + rows, d / 2, lantern(true));
            }
            default -> {
                box(c, wallTop, s, true);
                gable(c, wallTop, s);
                c.set(1, 0, 1, lantern(false));
            }
        }
        if (level >= 2 && solid(c.get(0, 2, d - 1))) {
            c.set(0, 2, d - 1, lamp);
        }
        if (level >= 3 && solid(c.get(w - 1, 2, d - 1))) {
            c.set(w - 1, 2, d - 1, lamp);
        }
        c.set(c.coreX, c.coreY, c.coreZ, ModRegistries.BUILDING_CORE.get().defaultBlockState());
        return c;
    }

    private static boolean solid(BlockState s) {
        return s != null && !s.isAir();
    }

    private static Rotation rotation(int rot) {
        return switch (Math.floorMod(rot, 4)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** Local (x, z) of a design of size w x d to world position for a building at (bx, bz) with rotation rot. */
    private static BlockPos toWorld(int bx, int by, int bz, int rot, int w, int d, int lx, int ly, int lz) {
        int wx;
        int wz;
        switch (Math.floorMod(rot, 4)) {
            case 1 -> {
                wx = bx + (d - 1 - lz);
                wz = bz + lx;
            }
            case 2 -> {
                wx = bx + (w - 1 - lx);
                wz = bz + (d - 1 - lz);
            }
            case 3 -> {
                wx = bx + lz;
                wz = bz + (w - 1 - lx);
            }
            default -> {
                wx = bx + lx;
                wz = bz + lz;
            }
        }
        return new BlockPos(wx, by + ly, wz);
    }

    public static List<Placement> blueprint(Building b) {
        BuildingType t = b.type();
        if (t == BuildingTypes.GENERATOR) {
            return generatorBlueprint(b.x + 6, b.y, b.z + 6);
        }
        Canvas c = design(t, b.level);
        Rotation r = rotation(b.rotation);
        List<Placement> out = new ArrayList<>(c.cells.size());
        for (Map.Entry<Long, BlockState> e : c.cells.entrySet()) {
            long k = e.getKey();
            int ly = (int) (k >> 32) - 512;
            int lx = (int) ((k >> 16) & 0xFFFF) - 1024;
            int lz = (int) (k & 0xFFFF) - 1024;
            out.add(new Placement(toWorld(b.x, b.y, b.z, b.rotation, c.w, c.d, lx, ly, lz), e.getValue().rotate(r)));
        }
        out.sort(Comparator.comparingInt(p -> p.pos().getY()));
        return out;
    }

    /** The building's core block (right-click to open its panel). */
    public static BlockPos corePos(Building b) {
        if (b.type() == BuildingTypes.GENERATOR) {
            return corePos(b.x + 6, b.y, b.z + 6);
        }
        Canvas c = design(b.type(), b.level);
        return toWorld(b.x, b.y, b.z, b.rotation, c.w, c.d, c.coreX, c.coreY, c.coreZ);
    }

    /** Just outside the door, on the ground (where citizens gather). */
    public static BlockPos doorOutside(Building b) {
        BuildingType t = b.type();
        if (t == BuildingTypes.GENERATOR) {
            return new BlockPos(b.x + 6, b.y, b.z + 14);
        }
        return toWorld(b.x, b.y, b.z, b.rotation, t.width, t.depth, t.width / 2, 0, t.depth);
    }

    private static int top(Building b) {
        return b.y + b.type().height + b.level + 6;
    }

    /** Clears and levels the footprint, lays the foundation and puts up scaffolding. */
    public static void prepareSite(ServerLevel level, Building b) {
        placedCount.remove(b.id);
        int top = top(b);
        for (int x = b.x; x <= b.maxX(); x++) {
            for (int z = b.z; z <= b.maxZ(); z++) {
                int h = Terrain.height(x, z);
                for (int y = b.y; y <= Math.max(top, h + 1); y++) {
                    set(level, new BlockPos(x, y, z), AIR);
                }
                for (int y = Math.min(h + 1, b.y - 1); y <= b.y - 1; y++) {
                    set(level, new BlockPos(x, y, z), y == b.y - 1 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : FOUNDATION);
                }
            }
        }
        int sh = Math.max(3, b.type().height - 1);
        for (int[] p : new int[][] {{b.x, b.z}, {b.maxX(), b.z}, {b.x, b.maxZ()}, {b.maxX(), b.maxZ()}}) {
            for (int y = b.y; y < b.y + sh; y++) {
                set(level, new BlockPos(p[0], y, p[1]), Blocks.SCAFFOLDING.defaultBlockState());
            }
        }
    }

    /** Places the share of the blueprint that matches the construction progress (0..1). */
    public static void progress(ServerLevel level, Building b, double fraction) {
        if (!level.isLoaded(new BlockPos(b.x, b.y, b.z)) || !level.isLoaded(new BlockPos(b.maxX(), b.y, b.maxZ()))) {
            return;
        }
        List<Placement> bp = blueprint(b);
        int target = (int) Math.floor(Math.max(0, Math.min(1, fraction)) * bp.size() * 0.92);
        int done = placedCount.getOrDefault(b.id, 0);
        int limit = Math.min(target, done + 400);
        for (int i = done; i < limit; i++) {
            Placement p = bp.get(i);
            if (!level.getBlockState(p.pos()).is(Blocks.SCAFFOLDING)) {
                set(level, p.pos(), p.state());
            }
        }
        placedCount.put(b.id, Math.max(done, limit));
    }

    /** Called every second for buildings under construction. */
    public static void tickConstruction(ServerLevel level, City city) {
        for (Building b : city.buildings()) {
            if (b.state == Building.State.CONSTRUCTION) {
                BuildingType t = b.type();
                double f = b.buildProgress / Math.max(1.0, t.buildHours * (double) t.builders);
                progress(level, b, f);
            }
        }
    }

    /** Builds the finished structure (also after upgrades). */
    public static void build(ServerLevel level, Building b) {
        placedCount.remove(b.id);
        if (b.type() == BuildingTypes.GENERATOR) {
            buildGenerator(level, b.x + 6, b.y, b.z + 6);
            return;
        }
        int top = top(b);
        for (int x = b.x; x <= b.maxX(); x++) {
            for (int z = b.z; z <= b.maxZ(); z++) {
                int h = Terrain.height(x, z);
                for (int y = b.y; y <= Math.max(top, h + 1); y++) {
                    set(level, new BlockPos(x, y, z), AIR);
                }
                for (int y = Math.min(h + 1, b.y - 1); y < b.y - 1; y++) {
                    set(level, new BlockPos(x, y, z), FOUNDATION);
                }
            }
        }
        List<Placement> bp = blueprint(b);
        for (Placement p : bp) {
            set(level, p.pos(), p.state());
        }
        fixShapes(level, bp);
    }

    /** Removes the structure and restores the snow surface. */
    public static void clear(ServerLevel level, Building b) {
        placedCount.remove(b.id);
        int top = top(b);
        for (int x = b.x; x <= b.maxX(); x++) {
            for (int z = b.z; z <= b.maxZ(); z++) {
                for (int y = b.y - 3; y <= top; y++) {
                    BlockState s = y < b.y - 1 ? Blocks.STONE.defaultBlockState() : y == b.y - 1 ? SNOW : AIR;
                    set(level, new BlockPos(x, y, z), s);
                }
            }
        }
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.isOutsideBuildHeight(pos)) {
            return;
        }
        if (level.getBlockState(pos) != state) {
            level.setBlock(pos, state, FLAGS);
        }
    }

    private static void fixShapes(ServerLevel level, List<Placement> placements) {
        for (Placement p : placements) {
            Block block = p.state().getBlock();
            if (block instanceof FenceBlock || block instanceof IronBarsBlock || block instanceof CrossCollisionBlock
                    || block instanceof WallBlock || block instanceof StairBlock) {
                BlockState now = level.getBlockState(p.pos());
                BlockState fixed = Block.updateFromNeighbourShapes(now, level, p.pos());
                if (fixed != now && !fixed.isAir()) {
                    level.setBlock(p.pos(), fixed, FLAGS);
                }
            }
        }
    }
}
