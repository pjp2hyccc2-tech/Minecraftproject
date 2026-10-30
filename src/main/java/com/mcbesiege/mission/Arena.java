package com.mcbesiege.mission;

import com.mcbesiege.block.HangingBannerBlock;
import com.mcbesiege.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * A mission arena being generated: queued block operations (executed over several ticks), targets, mobs, props and
 * zones. Coordinates are relative to the arena origin (ground level = y 0, the build zone is centred on x=0, z=0,
 * the enemy lies towards +z).
 */
public final class Arena {
    public record Mob(EntityType<?> type, int x, int y, int z, boolean onTop) {}

    public record Prop(int x, int y, int z, float mass) {}

    public final ServerLevel level;
    public final BlockPos o;
    final List<BlockPos> opPos = new ArrayList<>();
    final List<BlockState> opState = new ArrayList<>();
    public final List<BlockPos> targets = new ArrayList<>();
    public final List<Mob> mobs = new ArrayList<>();
    public final List<Prop> props = new ArrayList<>();
    public AABB goal;
    public boolean killAll;
    public int killCount;
    public boolean destroyTargets;
    public boolean deliver;
    public boolean reach;
    public int buildHalf = 7;

    public Arena(ServerLevel level, BlockPos origin) {
        this.level = level;
        this.o = origin;
    }

    public BlockPos at(int x, int y, int z) { return o.offset(x, y, z); }

    public void set(int x, int y, int z, BlockState s) {
        opPos.add(at(x, y, z));
        opState.add(s);
    }

    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++)
                for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) set(x, y, z, s);
    }

    public void hollow(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int y = y0; y <= y1; y++)
            for (int z = z0; z <= z1; z++)
                for (int x = x0; x <= x1; x++)
                    if (x == x0 || x == x1 || z == z0 || z == z1) set(x, y, z, s);
    }

    /** Wipes the arena area: air above ground, grass on top, dirt below. */
    public void clear(int x0, int z0, int x1, int z1, int height) {
        BlockState air = Blocks.AIR.defaultBlockState(), grass = Blocks.GRASS_BLOCK.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState();
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                for (int y = -16; y <= -2; y++) set(x, y, z, y < -12 ? Blocks.STONE.defaultBlockState() : dirt);
                set(x, -1, z, grass);
                for (int y = 0; y <= height; y++) set(x, y, z, air);
            }
        }
    }

    public void buildZoneMarkers() {
        int h = buildHalf;
        BlockState a = Blocks.YELLOW_CONCRETE.defaultBlockState(), b = Blocks.BLACK_CONCRETE.defaultBlockState();
        for (int i = -h - 1; i <= h + 1; i++) {
            BlockState s = (i & 1) == 0 ? a : b;
            set(i, -1, -h - 1, s);
            set(i, -1, h + 1, s);
            set(-h - 1, -1, i, s);
            set(h + 1, -1, i, s);
        }
    }

    public AABB buildZone() {
        return new AABB(o.getX() - buildHalf, o.getY() - 1, o.getZ() - buildHalf, o.getX() + buildHalf + 1, o.getY() + 24, o.getZ() + buildHalf + 1);
    }

    public void target(int x, int y, int z) {
        set(x, y, z, ModBlocks.SIEGE_TARGET.get().defaultBlockState());
        targets.add(at(x, y, z));
        destroyTargets = true;
    }

    public void mob(EntityType<?> type, int x, int y, int z) {
        mobs.add(new Mob(type, x, y, z, false));
    }

    public void banner(int x, int y, int z, Direction facing, boolean red) {
        BlockState s = (red ? ModBlocks.BANNER_RED : ModBlocks.BANNER_BLUE).get().defaultBlockState().setValue(HangingBannerBlock.FACING, facing);
        set(x, y, z, s);
    }

    public void goal(int x0, int z0, int x1, int z1) {
        goal = new AABB(o.getX() + x0, o.getY() - 2, o.getZ() + z0, o.getX() + x1 + 1, o.getY() + 30, o.getZ() + z1 + 1);
        BlockState g = Blocks.GOLD_BLOCK.defaultBlockState();
        for (int x = x0; x <= x1; x++) {
            set(x, -1, z0, g);
            set(x, -1, z1, g);
        }
        for (int z = z0; z <= z1; z++) {
            set(x0, -1, z, g);
            set(x1, -1, z, g);
        }
    }

    // ------------------------------------------------------------------------------------------------ structures

    static BlockState bricks() { return ModBlocks.CASTLE_BRICKS.get().defaultBlockState(); }

    public static BlockState bricksState() { return bricks(); }

    /** Applies every queued block operation immediately (used for showcases / tests). */
    public void apply() {
        for (int i = 0; i < opPos.size(); i++) level.setBlock(opPos.get(i), opState.get(i), 2 | 16);
        opPos.clear();
        opState.clear();
    }

    static BlockState stone() { return ModBlocks.CASTLE_STONE.get().defaultBlockState(); }

    static BlockState slab() { return ModBlocks.CASTLE_BRICK_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM); }

    static BlockState thatchStairs(Direction facing) {
        return ModBlocks.THATCH_STAIRS.get().defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    /** Square tower with battlements; returns the top y. */
    public int tower(int cx, int cz, int r, int h, BlockState wall) {
        for (int y = 0; y < h; y++) hollow(cx - r, y, cz - r, cx + r, y, cz + r, wall);
        fill(cx - r + 1, h - 1, cz - r + 1, cx + r - 1, h - 1, cz + r - 1, Blocks.SPRUCE_PLANKS.defaultBlockState());
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                boolean edge = x == cx - r || x == cx + r || z == cz - r || z == cz + r;
                if (edge && ((x + z) & 1) == 0) set(x, h, z, wall);
            }
        }
        return h;
    }

    /** Straight castle wall along x (z fixed) or z, thickness 2, with crenellations. */
    public void wallX(int x0, int x1, int z, int h) {
        fill(x0, 0, z, x1, h - 1, z + 1, bricks());
        for (int x = x0; x <= x1; x++) {
            if ((x & 1) == 0) set(x, h, z + 1, bricks());
            else set(x, h, z + 1, slab());
            if ((x % 6) == 0) set(x, 1, z, stone());
        }
    }

    public void wallZ(int x, int z0, int z1, int h) {
        fill(x, 0, z0, x + 1, h - 1, z1, bricks());
        for (int z = z0; z <= z1; z++) {
            if ((z & 1) == 0) set(x + 1, h, z, bricks());
            else set(x + 1, h, z, slab());
        }
    }

    public void gate(int x0, int x1, int z, int h) {
        fill(x0, 0, z, x1, h - 1, z + 1, ModBlocks.CASTLE_GATE.get().defaultBlockState());
    }

    public void windmill(int cx, int cz) {
        // stone foundation + timber tower
        fill(cx - 3, 0, cz - 3, cx + 3, 0, cz + 3, Blocks.COBBLESTONE.defaultBlockState());
        for (int y = 1; y <= 9; y++) hollow(cx - 3, y, cz - 3, cx + 3, y, cz + 3, y % 4 == 0 ? Blocks.DARK_OAK_PLANKS.defaultBlockState()
                : ModBlocks.TIMBER_FRAME.get().defaultBlockState());
        for (int y = 1; y <= 9; y++) {
            set(cx - 3, y, cz - 3, Blocks.DARK_OAK_LOG.defaultBlockState());
            set(cx + 3, y, cz - 3, Blocks.DARK_OAK_LOG.defaultBlockState());
            set(cx - 3, y, cz + 3, Blocks.DARK_OAK_LOG.defaultBlockState());
            set(cx + 3, y, cz + 3, Blocks.DARK_OAK_LOG.defaultBlockState());
        }
        // door
        set(cx, 1, cz - 3, Blocks.AIR.defaultBlockState());
        set(cx, 2, cz - 3, Blocks.AIR.defaultBlockState());
        // thatch roof
        for (int i = 0; i <= 3; i++) {
            int y = 10 + i, r = 4 - i;
            for (int x = cx - r; x <= cx + r; x++) {
                set(x, y, cz - r, thatchStairs(Direction.SOUTH));
                set(x, y, cz + r, thatchStairs(Direction.NORTH));
            }
            for (int z = cz - r + 1; z <= cz + r - 1; z++) {
                set(cx - r, y, z, thatchStairs(Direction.EAST));
                set(cx + r, y, z, thatchStairs(Direction.WEST));
            }
        }
        set(cx, 14, cz, ModBlocks.THATCH.get().defaultBlockState());
        // sails on the front (north) face, hub = target
        int hy = 8, hz = cz - 4;
        set(cx, hy, cz - 3, Blocks.DARK_OAK_LOG.defaultBlockState());
        for (int i = 1; i <= 6; i++) {
            BlockState w = Blocks.WHITE_WOOL.defaultBlockState();
            set(cx, hy + i, hz, i == 6 ? Blocks.DARK_OAK_FENCE.defaultBlockState() : w);
            set(cx, hy - i, hz, i >= 7 ? w : Blocks.WHITE_WOOL.defaultBlockState());
            set(cx + i, hy, hz, w);
            set(cx - i, hy, hz, w);
            set(cx + 1, hy + i, hz, Blocks.SPRUCE_FENCE.defaultBlockState());
            set(cx - 1, hy - i, hz, Blocks.SPRUCE_FENCE.defaultBlockState());
            set(cx + i, hy - 1, hz, Blocks.SPRUCE_FENCE.defaultBlockState());
            set(cx - i, hy + 1, hz, Blocks.SPRUCE_FENCE.defaultBlockState());
        }
        target(cx, hy, hz);
        target(cx, 1, cz);
    }

    public void house(int cx, int cz, boolean big) {
        int rx = big ? 3 : 2, rz = 2;
        fill(cx - rx, 0, cz - rz, cx + rx, 0, cz + rz, Blocks.COBBLESTONE.defaultBlockState());
        for (int y = 1; y <= 3; y++) hollow(cx - rx, y, cz - rz, cx + rx, y, cz + rz,
                y == 3 ? ModBlocks.TIMBER_FRAME_PLAIN.get().defaultBlockState() : ModBlocks.TIMBER_FRAME.get().defaultBlockState());
        set(cx, 1, cz - rz, Blocks.AIR.defaultBlockState());
        set(cx, 2, cz - rz, Blocks.AIR.defaultBlockState());
        set(cx - rx, 2, cz, Blocks.GLASS_PANE.defaultBlockState());
        set(cx + rx, 2, cz, Blocks.GLASS_PANE.defaultBlockState());
        for (int i = 0; i <= rz + 1; i++) {
            for (int x = cx - rx - 1; x <= cx + rx + 1; x++) {
                set(x, 4 + i, cz - rz - 1 + i, thatchStairs(Direction.SOUTH));
                set(x, 4 + i, cz + rz + 1 - i, thatchStairs(Direction.NORTH));
            }
        }
        fill(cx - rx - 1, 4 + rz + 1, cz, cx + rx + 1, 4 + rz + 1, cz, ModBlocks.THATCH_SLAB.get().defaultBlockState());
    }

    public void tent(int cx, int cz, BlockState wool) {
        for (int i = 0; i < 3; i++) {
            fill(cx - 2 + i, i, cz - 2, cx - 2 + i, i, cz + 2, wool);
            fill(cx + 2 - i, i, cz - 2, cx + 2 - i, i, cz + 2, wool);
        }
        set(cx, 0, cz - 2, Blocks.AIR.defaultBlockState());
        set(cx, 1, cz - 2, Blocks.AIR.defaultBlockState());
    }

    public void scarecrow(int x, int z) {
        set(x, 0, z, Blocks.HAY_BLOCK.defaultBlockState());
        set(x, 1, z, Blocks.OAK_FENCE.defaultBlockState());
        set(x, 2, z, Blocks.OAK_FENCE.defaultBlockState());
        set(x - 1, 2, z, Blocks.OAK_FENCE.defaultBlockState());
        set(x + 1, 2, z, Blocks.OAK_FENCE.defaultBlockState());
        target(x, 3, z);
        set(x, 4, z, Blocks.CARVED_PUMPKIN.defaultBlockState());
        set(x - 2, 0, z + 1, Blocks.HAY_BLOCK.defaultBlockState());
        set(x + 2, 0, z - 1, Blocks.HAY_BLOCK.defaultBlockState());
    }

    public void palisade(int cx, int cz, int r, int gapHalf) {
        for (int a = 0; a < 360; a += 2) {
            double rad = Math.toRadians(a);
            int x = cx + (int) Math.round(Math.sin(rad) * r);
            int z = cz - (int) Math.round(Math.cos(rad) * r);
            if (Math.abs(x - cx) <= gapHalf && z < cz) continue;
            for (int y = 0; y < 4; y++) set(x, y, z, Blocks.SPRUCE_LOG.defaultBlockState());
            set(x, 4, z, Blocks.SPRUCE_FENCE.defaultBlockState());
        }
    }
}
